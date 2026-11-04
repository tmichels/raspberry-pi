# HTTPS op de Pi met Nginx

Deze handleiding zet een HTTPS-proxy voor de frontend en Keycloak.
De backend blijft intern bereikbaar via de frontend. Zo worden login en
API-verzoeken versleuteld zonder in elke applicatie HTTPS te configureren.

| Adres | Bestemming |
|---|---|
| `https://pi.thomasmichels.nl` | Frontend op `127.0.0.1:8081` |
| `https://login.thomasmichels.nl` | Keycloak op `127.0.0.1:9000` |
| `/api` op het frontendadres | Backend via `pi-app`, op `pi-backend:8080` |

## 1. Voorbereiden

Gebruik een Pi met Podman, de applicatie-images en een bestaande Keycloak
met realm `donna`, client `pi` en volume `keycloak-data`.
Zie de [frontend](../frontend/README.md), [backend](../backend/README.md)
en [Keycloak](../keycloak/README.md) voor bouwen en basisconfiguratie.

Voer containercommando's op de Pi uit als gewone gebruiker.
Alleen de proxy gebruikt `sudo`. Voer ze uit vanuit de hoofdmap van deze
repository, tenzij anders vermeld.

De startcommando's hieronder vereisen vrije containernamen. Stop en verwijder
bij vervanging eerst de bestaande container; verwijder geen gegevensvolumes.
Vervanging onderbreekt de applicatie tijdelijk.

Controleer of poorten 80 (HTTP-redirect) en 443 (HTTPS) vrij zijn:

```sh
sudo ss -ltn '( sport = :80 or sport = :443 )'
```

Laat beide domeinnamen op je Linux-client naar de Pi verwijzen.
Voeg aan `/etc/hosts` toe:

```text
192.168.178.96 pi.thomasmichels.nl login.thomasmichels.nl
```

Dit regelt alleen de naamherkenning op die computer. Andere apparaten
hebben een eigen instelling of lokale DNS nodig. Open geen routerpoorten:
de applicaties zijn alleen voor het lokale netwerk bedoeld.

## 2. Certificaat aanvragen

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

Een TXT-record is een stukje tekst in DNS. Let’s Encrypt controleert of je
de door Certbot opgegeven waarde onder de gevraagde naam kunt plaatsen.
Daarmee bewijs je dat je de DNS van het domein beheert, zonder de Pi publiek
bereikbaar te maken.
Het domein is geregistreerd bij Strato, maar gebruikt Cloudflare-nameservers:
voeg de gevraagde TXT-records daarom toe bij **Cloudflare → DNS → Records**.
Gebruik type `TXT`, TTL `Auto` en de afzonderlijke waarden uit Certbot.

| Naam in Cloudflare | Volledige recordnaam |
|---|---|
| `_acme-challenge.login` | `_acme-challenge.login.thomasmichels.nl` |
| `_acme-challenge.pi` | `_acme-challenge.pi.thomasmichels.nl` |

Bevestig elke controle pas in Certbot nadat de bijbehorende waarde publiek
zichtbaar is. Controleer in een aparte terminal:

```sh
dig @1.1.1.1 TXT _acme-challenge.login.thomasmichels.nl +short
dig @1.1.1.1 TXT _acme-challenge.pi.thomasmichels.nl +short
```

Controleer na succes de opslagpaden met `sudo certbot certificates`.
`nginx.conf` verwacht:

- `/etc/letsencrypt/live/pi.thomasmichels.nl/fullchain.pem`: certificaat en uitgeversketen.
- `/etc/letsencrypt/live/pi.thomasmichels.nl/privkey.pem`: geheime privésleutel.

Pas `nginx.conf` aan als Certbot andere paden toont. Bewaar deze bestanden
buiten Git en maak de privésleutel niet openbaar leesbaar.
De tijdelijke TXT-records kunnen na succes weg: ze dienen alleen voor de
domeincontrole, niet voor HTTPS. Bij een volgende handmatige vernieuwing
geeft Certbot nieuwe controlewaarden.

De domeinnamen op het certificaat worden openbaar vastgelegd in
Certificate Transparency-logs. Dit maakt de namen vindbaar, maar maakt
de Pi niet bereikbaar vanaf internet. Privésleutels, wachtwoorden en
applicatiegegevens staan niet in deze registers.

## 3. Proxy controleren en starten

De proxy draait in deze opzet als root om poort 443 te kunnen gebruiken en
de beveiligde Certbot-bestanden in te lezen. Rootless draaien kan ook,
maar vereist aparte inrichting voor beide. De applicaties blijven rootless.

`--network host` laat Nginx de loopbackpoorten van de Pi bereiken.
Koppel de hele Certbot-map alleen-lezen (`:ro`): de bestanden in `live`
verwijzen naar bestanden in `archive`.

Controleer eerst de configuratie; dit start geen blijvende proxy:

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
  --network host \
  -v /usr/share/zoneinfo/Europe/Amsterdam:/etc/localtime:ro \
  -e TZ=Europe/Amsterdam \
  -v "$PWD/proxy/nginx.conf:/etc/nginx/nginx.conf:ro" \
  -v /etc/letsencrypt:/etc/letsencrypt:ro \
  docker.io/library/nginx:1.28
```

Er is geen `-p` nodig door het hostnetwerk. Poort 80 stuurt verzoeken voor
beide namen met HTTP 308 door naar HTTPS, met behoud van pad en query.
Zo werkt ook een browser die bij een adres zonder protocol eerst HTTP
probeert. De applicaties blijven achter HTTPS; stuur tokens of wachtwoorden
altijd direct via HTTPS, want een redirect beveiligt het eerste HTTP-verzoek niet.

Bij een bestaande proxy: controleer op de Pi eerst of poort 80 vrij is,
werk `proxy/nginx.conf` bij en herlaad Nginx met het commando onder Onderhoud.
Controleer daarna vanaf je client:

```sh
curl -I http://pi.thomasmichels.nl
curl -I http://login.thomasmichels.nl
```

Verwacht HTTP 308 met een `Location` naar de bijbehorende HTTPS-URL.

## 4. Keycloak instellen

Stop en verwijder de bestaande Keycloak-container en start de vervanger:

```sh
podman stop keycloak &&
podman rm keycloak &&
podman run -d --name keycloak \
  -p 127.0.0.1:9000:8080 \
  -v keycloak-data:/opt/keycloak/data \
  -v /usr/share/zoneinfo/Europe/Amsterdam:/etc/localtime:ro \
  -e TZ=Europe/Amsterdam \
  -e JAVA_OPTS_APPEND=-Duser.timezone=Europe/Amsterdam \
  -e KC_HTTP_ENABLED=true \
  -e KC_HOSTNAME=https://login.thomasmichels.nl \
  -e KC_PROXY_HEADERS=xforwarded \
  docker.io/keycloak/keycloak:26.8.1 start
```

De browser maakt een HTTPS-verbinding met Nginx. Nginx handelt de
versleuteling af en stuurt het verzoek via HTTP binnen de Pi naar Keycloak.
Met de header `X-Forwarded-Proto: https` vertelt Nginx dat de browser HTTPS
gebruikte. Door `KC_PROXY_HEADERS=xforwarded` gebruikt Keycloak deze
informatie om de juiste HTTPS-adressen te maken, bijvoorbeeld na inloggen.

`127.0.0.1:9000:8080` maakt de Keycloak-poort alleen bereikbaar op de Pi zelf,
niet vanaf andere apparaten in het LAN. Die moeten via Nginx verbinden,
dat de proxyheaders zelf instelt. Zo kunnen ze niet rechtstreeks Keycloak
aanroepen met vervalste proxyheaders.

Het bestaande volume behoudt de adminaccount; de bootstrapvariabelen
`KC_BOOTSTRAP_ADMIN_USERNAME` en `KC_BOOTSTRAP_ADMIN_PASSWORD` zijn
hier niet opnieuw nodig.

Open `https://login.thomasmichels.nl/admin/`. Voeg onder
**realm donna → Clients → pi → Settings** deze waarden toe en sla op:

| Instelling | Waarden |
|---|---|
| Valid redirect URIs | `https://pi.thomasmichels.nl` en `https://pi.thomasmichels.nl/` |
| Valid post logout redirect URIs | `https://pi.thomasmichels.nl` en `https://pi.thomasmichels.nl/` |
| Web origins | `https://pi.thomasmichels.nl` |

Deze instellingen staan terugkeer naar de frontend toe. Gebruik exacte
URL's, geen wildcards. De afsluitende slash voorkomt een mismatch met
de login-aanvraag. Laat localhost-adressen staan als je ze voor ontwikkeling gebruikt.

## 5. Frontend en backend starten

We gebruiken losse containers, geen Podman-pod.
Beide containers worden direct op hetzelfde netwerk aangemaakt.

Bouw vanuit de hoofdmap van de repository beide images:

```sh
podman build -t localhost/pi-backend ./backend &&
podman build \
  --build-arg VITE_KEYCLOAK_URL=https://login.thomasmichels.nl \
  -t localhost/pi-frontend ./frontend
```

De Keycloak-URL zit in de build: een runtimevariabele of herstart alleen
wijzigt die niet.

Maak op de Pi het gedeelde netwerk, als het nog niet bestaat:

```sh
podman network create pi-app
```

Dit netwerk maakt de backend bereikbaar via zijn containernaam zonder
poort 8080 op de Pi te publiceren. Het netwerk wordt bij `podman run`
gekozen met `--network pi-app`.

Maak nu de backend- en frontendcontainers direct op dit netwerk:

```sh
podman run -d --name pi-backend \
  --network pi-app \
  --add-host login.thomasmichels.nl:host-gateway \
  -e KEYCLOAK_ISSUER_URI=https://login.thomasmichels.nl/realms/donna \
  -e PI_COMMANDS_DIRECTORY=/data \
  -v pi-backend-data:/data \
  localhost/pi-backend:latest &&

podman run -d --name pi-frontend \
  --network pi-app \
  -p 127.0.0.1:8081:8080 \
  -e BACKEND_URL=http://pi-backend:8080 \
  localhost/pi-frontend
```

De issuer moet exact overeenkomen met de issuer in Keycloak-tokens.
`--add-host` laat de backend het HTTPS-adres via de Pi bereiken voor het
ophalen van publieke sleutels; het hosts-bestand van je client geldt niet
voor de container. De backend behoudt zijn gegevensvolume. Alleen de
frontendpoort wordt op loopback beschikbaar gemaakt voor Nginx.

## 6. Werking en afscherming controleren

Bekijk op de Pi de status en bij problemen de logs:

```sh
podman ps -a
sudo podman ps -a
```

Zonder `sudo` zie je de applicaties, met `sudo`
de proxy. Controleer vanaf je Linux-client:

```sh
curl -I https://pi.thomasmichels.nl
curl -I https://login.thomasmichels.nl
```

Verwacht frontendstatus 200 en een Keycloak-redirect naar
`https://login.thomasmichels.nl/admin/`. Gebruik geen `-k` (`--insecure`):
die curl-optie slaat de controle van de geldigheid, de vertrouwde uitgever
en de domeinnaam van het certificaat over.

Open de frontend, log in met een gebruiker uit realm `donna` en druk
één keer op de actieknop. Exitcode 0 bevestigt de volledige keten,
inclusief tokenvalidatie en het aanmaken van een bestand.

Controleer ook dat de interne poorten vanaf de client onbereikbaar zijn:

```sh
for port in 8080 8081 9000; do
  echo "Controle van poort $port"
  curl --noproxy '*' --connect-timeout 3 --max-time 5 \
    -I "http://192.168.178.96:$port"
done
```

Voor alle drie verwacht je een verbindingsfout of time-out, geen HTTP-antwoord.

## Onderhoud en todo's

### Tijdzone

Frontend en backend gebruiken standaard `Europe/Amsterdam` in hun images.
De backend stelt ook de Java-tijdzone in, zodat logs en bestandsnamen uit
`date` dezelfde lokale tijd gebruiken. Keycloak en de proxy gebruiken
standaardimages: de startcommando's koppelen daarom het tijdzonebestand
van de Pi alleen-lezen en stellen voor Keycloak ook de JVM-tijdzone in.
Controleer dat `/usr/share/zoneinfo/Europe/Amsterdam` op de Pi bestaat.

De tijdzone schakelt automatisch tussen CET en CEST. React gebruikt de
browsertijdzone; HTTP-headers zoals `Date` blijven volgens de standaard GMT
en tokenvelden zoals `exp` zijn tijdzone-onafhankelijke tijdstempels.
Voor bestaande containers: bouw frontend en backend opnieuw en maak alle
containers opnieuw aan met de bovenstaande instellingen. Behoud de volumes.
Controleer daarna op de Pi:

```sh
podman exec pi-backend date '+%F %T %Z %z'
podman exec pi-frontend date '+%F %T %Z %z'
podman exec keycloak date '+%F %T %Z %z'
sudo podman exec pi-proxy date '+%F %T %Z %z'
```

Verwacht CET (+0100) in de winter en CEST (+0200) in de zomer.

### Certificaten

Controleer de geldigheid met `sudo certbot certificates`. De handmatige
DNS-methode vernieuwt niet automatisch; Let's Encrypt stuurt geen
verloopherinneringen. Voer vóór het verlopen dezelfde certificaataanvraag
uit en voltooi de nieuwe DNS-controles.

Laat Nginx na certificaatvernieuwing of een configuratiewijziging opnieuw
de bestanden inlezen:

```sh
sudo podman exec pi-proxy nginx -t &&
sudo podman exec pi-proxy nginx -s reload
```

Nog te regelen:

- Lokale DNS voor andere apparaten, zodat niet elk apparaat een hosts-instelling nodig heeft.
- Automatisch starten na een Pi-herstart, bijvoorbeeld met Quadlet; linger alleen start geen containers.
- Automatische certificaatvernieuwing via Cloudflare-DNS, inclusief Nginx herladen.
- Rootless proxy: toegang via poort 443 en veilige certificaatdistributie regelen,
  inclusief vernieuwing. Bekijk daarbij een beperkter netwerk dan het hostnetwerk.
