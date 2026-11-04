# HTTPS-proxy op de Pi

Nginx verzorgt HTTPS voor de bestaande losse Podman-containers:

| Publiek adres binnen het LAN | Doorgestuurd naar |
|---|---|
| `https://pi.thomasmichels.nl` | Frontend op `127.0.0.1:8081` |
| `https://login.thomasmichels.nl` | Keycloak op `127.0.0.1:9000` |

De frontend stuurt `/api` zelf door naar de backend op poort 8080.
Een reverse proxy ontvangt verzoeken en stuurt ze naar de juiste applicatie.
Het certificaat beveiligt de verbinding tussen het apparaat en de proxy.

## Vereisten

- Podman op de Pi en de bestaande frontend-, backend- en Keycloak-containers.
- TCP-poort 443 is vrij.
- Beide domeinnamen verwijzen op het gebruikte apparaat naar `192.168.178.96`.
  Op de Linux-testcomputer is hiervoor aan `/etc/hosts` toegevoegd:

  ```text
  192.168.178.96 pi.thomasmichels.nl login.thomasmichels.nl
  ```

- Een geldig certificaat voor beide namen op de Pi:
  - `/etc/letsencrypt/live/pi.thomasmichels.nl/fullchain.pem`
  - `/etc/letsencrypt/live/pi.thomasmichels.nl/privkey.pem`

Certificaten en de geheime privésleutel blijven buiten de repository.
Kopieer ze niet naar deze map en maak de privésleutel niet openbaar leesbaar.
De `.gitignore` in deze map sluit PEM- en KEY-bestanden aanvullend uit.

## Certificaat aanvragen

Op Ubuntu kan Certbot worden geïnstalleerd met:

```sh
sudo apt update
sudo apt install certbot
```

Een certificaat voor beide namen aanvragen:

```sh
sudo certbot certonly \
  --manual \
  --preferred-challenges dns \
  -d pi.thomasmichels.nl \
  -d login.thomasmichels.nl
```

Het domein is geregistreerd bij Strato, maar gebruikt Cloudflare-nameservers.
Voeg de door Certbot gevraagde TXT-records daarom bij Cloudflare toe.
Controleer elke waarde voordat je de betreffende stap in Certbot bevestigt:

```sh
dig @1.1.1.1 TXT _acme-challenge.login.thomasmichels.nl +short
dig @1.1.1.1 TXT _acme-challenge.pi.thomasmichels.nl +short
```

Dit bewijst domeinbeheer zonder routerpoorten te openen. De tijdelijke
TXT-records kunnen na een geslaagde aanvraag worden verwijderd.
De namen op het certificaat komen in openbare certificaatregisters.

Controleer de opslagpaden en geldigheid met `sudo certbot certificates`.
Bij een afwijkend opslagpad moet je `nginx.conf` aanpassen.
Deze handmatige DNS-methode vernieuwt niet automatisch. Automatisering van
de DNS-controle moet nog worden ingericht; Let's Encrypt stuurt geen
herinneringsmails voor het verlopen van certificaten.

## Configuratie controleren

Voer de onderstaande commando's op de Pi uit vanuit de root van deze repository.
Ze gebruiken de configuratie uit `proxy/nginx.conf`.

```sh
sudo podman run --rm \
  --network host \
  -v "$PWD/proxy/nginx.conf:/etc/nginx/nginx.conf:ro" \
  -v /etc/letsencrypt:/etc/letsencrypt:ro \
  docker.io/library/nginx:1.28 \
  nginx -t
```

Dit start geen blijvende proxy. Ga alleen verder als Nginx meldt dat de
configuratiecontrole geslaagd is.

De proxy gebruikt `sudo` voor poort 443 en de beveiligde certificaatbestanden.
De bestaande applicatiecontainers blijven rootless draaien. Het hostnetwerk
maakt hun gepubliceerde poorten bereikbaar via `127.0.0.1`.
De hele map `/etc/letsencrypt` wordt alleen-lezen gekoppeld, omdat de bestanden
in `live` verwijzingen naar bestanden in `archive` zijn.

## Proxy starten

```sh
sudo podman run -d --name pi-proxy \
  --network host \
  -v "$PWD/proxy/nginx.conf:/etc/nginx/nginx.conf:ro" \
  -v /etc/letsencrypt:/etc/letsencrypt:ro \
  docker.io/library/nginx:1.28
```

Door het hostnetwerk is geen `-p` nodig. Er wordt alleen HTTPS aangeboden,
geen HTTP-redirect op poort 80.

Controleer de status en eventuele fouten:

```sh
sudo podman ps --filter name=pi-proxy
sudo podman logs pi-proxy
```

Vanaf de Linux-testcomputer kun je de HTTPS-verbinding controleren:

```sh
curl -I https://pi.thomasmichels.nl
curl -I https://login.thomasmichels.nl
```

Gebruik geen `-k`: daarmee zou je juist de certificaatcontrole overslaan.
Een werkende TLS-verbinding betekent nog niet dat de login correct werkt.

## Configuratie of certificaten herladen

Na een wijziging of certificaatvernieuwing:

```sh
sudo podman exec pi-proxy nginx -t &&
sudo podman exec pi-proxy nginx -s reload
```

Nginx moet de vernieuwde certificaten opnieuw inlezen.
Automatisch starten na een reboot is nog niet ingericht.

## Nog nodig voor werkende en afgeschermde login

Deze proxyconfiguratie alleen is niet voldoende. De volgende stappen moeten
nog samen worden uitgevoerd:

- Keycloak starten met `KC_HOSTNAME=https://login.thomasmichels.nl` en
  `KC_PROXY_HEADERS=xforwarded`, met HTTP ingeschakeld voor de interne verbinding.
- De frontend bouwen met
  `VITE_KEYCLOAK_URL=https://login.thomasmichels.nl`.
- De backend starten met
  `KEYCLOAK_ISSUER_URI=https://login.thomasmichels.nl/realms/donna`.
  Deze URL moet ook vanuit de backendcontainer naar de Pi verwijzen en
  bereikbaar zijn met een vertrouwd certificaat.
- De redirect-, logout- en web-origininstellingen van Keycloak-client `pi`
  aanpassen naar de exacte frontend-origin `https://pi.thomasmichels.nl`.
- Rechtstreekse HTTP-toegang vanaf het LAN naar de applicatiepoorten
  afschermen, zonder de interne frontend-backendverbinding te verbreken.
  Vooral Keycloak mag proxyheaders alleen van de vertrouwde proxy ontvangen.

Tot die laatste stap zijn de bestaande HTTP-poorten nog bereikbaar vanaf
het LAN. Open geen routerpoorten voor deze lokaal bedoelde applicaties.

Zie ook de handleidingen van de [frontend](../frontend/README.md),
[backend](../backend/README.md) en [Keycloak](../keycloak/README.md).
