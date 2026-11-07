# HTTPS-proxy op de Pi

Deze handleiding behandelt alleen TLS-certificaten en Nginx. De applicaties
en Keycloak worden afzonderlijk ingericht: volg daarvoor de
[frontend](../frontend/README.md), [backend](../backend/README.md) en
[Keycloak](../keycloak/README.md)-handleidingen. Start die services voordat je
de proxy controleert.

| HTTPS-adres | Nginx-bestemming op de Pi |
|---|---|
| `https://pi.thomasmichels.nl` | Frontend op `127.0.0.1:8081` |
| `https://login.thomasmichels.nl` | Keycloak op `127.0.0.1:9000` |

Nginx stuurt HTTP-verzoeken met status 308 door naar HTTPS. De backend wordt
via de frontend gebruikt en heeft geen proxyroute.

## Namen en certificaat

Voer de proxycommando's op de Pi uit. Voer ze vanuit de root van deze
repository uit, tenzij anders vermeld. Gebruik alleen het lokale netwerk;
open geen routerpoorten.

Installeer Certbot op de Pi en vraag één certificaat voor beide namen aan:

```sh
sudo apt update
sudo apt install certbot
sudo certbot certonly \
  --manual \
  --preferred-challenges dns \
  -d pi.thomasmichels.nl \
  -d login.thomasmichels.nl
```

Voeg de door Certbot opgegeven TXT-records toe bij je DNS-provider. Voor deze
domeinen gebruiken we Cloudflare:

| Naam in Cloudflare | Volledige recordnaam |
|---|---|
| `_acme-challenge.login` | `_acme-challenge.login.thomasmichels.nl` |
| `_acme-challenge.pi` | `_acme-challenge.pi.thomasmichels.nl` |

Bevestig een controle pas in Certbot nadat de waarde publiek zichtbaar is:

```sh
dig @1.1.1.1 TXT _acme-challenge.login.thomasmichels.nl +short
dig @1.1.1.1 TXT _acme-challenge.pi.thomasmichels.nl +short
```

Controleer na succes met `sudo certbot certificates` waar Certbot het
certificaat heeft opgeslagen. De commando's hieronder gaan uit van
`/etc/letsencrypt/live/pi.thomasmichels.nl`. De domeinnamen van het
certificaat worden openbaar vastgelegd in Certificate Transparency-logs; de Pi
wordt daardoor niet publiek bereikbaar.

## Rootless inrichten

De proxy draait rootless via `compose.yaml`. Daarvoor zijn twee eenmalige
stappen met `sudo` nodig.

Een gewone gebruiker mag standaard geen poorten onder 1024 gebruiken. Sta
poort 80 en hoger toe:

```sh
echo 'net.ipv4.ip_unprivileged_port_start=80' |
  sudo tee /etc/sysctl.d/99-unprivileged-ports.conf
sudo sysctl --system
```

Hierdoor mag elke lokale gebruiker poorten vanaf 80 openen.

De bestanden in `/etc/letsencrypt` zijn alleen voor root leesbaar. Kopieer het
certificaat als gebruiker `thomas` naar `~/.local/share/pi-proxy/certs`; de
privésleutel blijft alleen voor die gebruiker leesbaar:

```sh
mkdir -p ~/.local/share/pi-proxy/certs
chmod 700 ~/.local/share/pi-proxy/certs
sudo install -o thomas -g thomas -m 0644 \
  /etc/letsencrypt/live/pi.thomasmichels.nl/fullchain.pem \
  ~/.local/share/pi-proxy/certs/
sudo install -o thomas -g thomas -m 0600 \
  /etc/letsencrypt/live/pi.thomasmichels.nl/privkey.pem \
  ~/.local/share/pi-proxy/certs/
```

Kopieer de bestanden, verplaats ze niet: Certbot heeft ze in
`/etc/letsencrypt` nodig. `install` kopieert de inhoud van de bestanden in
`live`, en zet direct de juiste eigenaar
en rechten. Gebruik `PROXY_CERT_DIRECTORY` in `compose.yaml` als je een andere
map wilt.

## Nginx controleren en starten

Controleer eerst of poorten 80 en 443 vrij zijn:

```sh
ss -ltn '( sport = :80 or sport = :443 )'
```

De proxy gebruikt `network_mode: host`, zodat Nginx de loopbackpoorten van
de frontend en Keycloak bereikt en het echte client-IP ziet. Er is geen
poortpublicatie nodig.

Controleer de configuratie; dit start geen blijvende proxy:

```sh
podman-compose run --rm pi-proxy nginx -t
```

Ga alleen verder bij `syntax is ok` en `test is successful`. Start de proxy,
eventueel samen met de andere services:

```sh
podman-compose up -d pi-proxy
```

Controleer vanaf de client of HTTP naar HTTPS wordt doorgestuurd en of beide
HTTPS-adressen bereikbaar zijn:

```sh
curl -I http://pi.thomasmichels.nl
curl -I http://login.thomasmichels.nl
curl -I https://pi.thomasmichels.nl
curl -I https://login.thomasmichels.nl
```

Voor HTTP verwacht je status 308 en een `Location` met de HTTPS-URL. Gebruik
geen `-k` (`--insecure`): daarmee slaat curl de certificaatcontrole over.
Bekijk proxylogs met `podman logs pi-proxy`.

## Onderhoud

De handmatige DNS-methode vernieuwt certificaten niet automatisch. Controleer
de geldigheid met `sudo certbot certificates` en vraag vóór het verlopen op
dezelfde manier een nieuw certificaat aan. Kopieer het daarna opnieuw met de
`install`-commando's uit [Rootless inrichten](#rootless-inrichten). Laat Nginx
na vernieuwing of een wijziging aan `nginx.conf` de configuratie en certificaten opnieuw
inlezen:

```sh
podman exec pi-proxy nginx -t &&
podman exec pi-proxy nginx -s reload
```

Nog te regelen: automatisch starten van de proxy na een Pi-herstart
(bijvoorbeeld met Quadlet) en automatische certificaatvernieuwing via
Cloudflare-DNS met daarna een Nginx-reload.
