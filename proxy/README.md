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
certificaat heeft opgeslagen. `nginx.conf` verwacht:

- `/etc/letsencrypt/live/pi.thomasmichels.nl/fullchain.pem`
- `/etc/letsencrypt/live/pi.thomasmichels.nl/privkey.pem`

Pas de paden in `nginx.conf` aan als Certbot andere paden toont. Mount de
Certbot-map alleen-lezen en maak de privésleutel niet openbaar leesbaar. De
domeinnamen van het certificaat worden openbaar vastgelegd in Certificate
Transparency-logs; de Pi wordt daardoor niet publiek bereikbaar.

## Nginx controleren en starten

Controleer eerst of poorten 80 en 443 vrij zijn:

```sh
sudo ss -ltn '( sport = :80 or sport = :443 )'
```

De proxy draait in deze opzet als root om poort 443 te gebruiken en de
Certbot-bestanden te lezen. De applicaties zelf draaien volgens hun eigen
handleidingen. `--network host` laat Nginx de loopbackpoorten van de Pi
bereiken. Mount de hele Certbot-map omdat bestanden in `live` naar bestanden
in `archive` verwijzen.

Controleer de configuratie; dit start geen blijvende proxy:

```sh
sudo podman run --rm \
  --network host \
  -v /usr/share/zoneinfo/Europe/Amsterdam:/etc/localtime:ro \
  -e TZ=Europe/Amsterdam \
  -v "$PWD/proxy/nginx.conf:/etc/nginx/nginx.conf:ro" \
  -v /etc/letsencrypt:/etc/letsencrypt:ro \
  docker.io/library/nginx:1.28 \
  nginx -t
```

Ga alleen verder bij `syntax is ok` en `test is successful`:

```sh
sudo podman run -d --name pi-proxy \
  --replace \
  --network host \
  -v /usr/share/zoneinfo/Europe/Amsterdam:/etc/localtime:ro \
  -e TZ=Europe/Amsterdam \
  -v "$PWD/proxy/nginx.conf:/etc/nginx/nginx.conf:ro" \
  -v /etc/letsencrypt:/etc/letsencrypt:ro \
  docker.io/library/nginx:1.28
```

Er is geen `-p` nodig door het hostnetwerk. Alleen de proxy gebruikt `sudo`;
de frontend-, backend- en Keycloak-containers zijn rootless.

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
Bekijk proxylogs met `sudo podman logs pi-proxy`.

## Onderhoud

De handmatige DNS-methode vernieuwt certificaten niet automatisch. Controleer
de geldigheid met `sudo certbot certificates` en vraag vóór het verlopen op
dezelfde manier een nieuw certificaat aan. Laat Nginx na vernieuwing of een
wijziging aan `nginx.conf` de configuratie en certificaten opnieuw inlezen:

```sh
sudo podman exec pi-proxy nginx -t &&
sudo podman exec pi-proxy nginx -s reload
```

Nog te regelen: automatisch starten van de proxy na een Pi-herstart
(bijvoorbeeld met Quadlet) en automatische certificaatvernieuwing via
Cloudflare-DNS met daarna een Nginx-reload.
Rootless draaien vereist aparte inrichting voor poort 443 en veilige toegang
tot de certificaten.
