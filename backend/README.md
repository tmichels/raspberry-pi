# Raspberry Pi backend

Een zelfstandige Java/Spring Boot-API voor vaste Linux-acties. Alle endpoints vereisen een Keycloak bearer access token (behalve swagger).

## Authenticatie

De backend is een stateless OAuth 2.0 Resource Server. Spring Security valideert de JWT-handtekening
met de publieke sleutels van Keycloak. Zie [Keycloak-handleiding](../keycloak/README.md). CSRF is uitgeschakeld omdat
uitsluitend bearer tokens in de Authorization-header worden gebruikt, geen
cookies.

De issuer staat in `src/main/resources/application.properties` en is voor
lokaal ontwikkelen en Podman https://login.thomasmichels.nl/realms/donna .
De issuer moet exact overeenkomen met Keycloak's `iss`-claim en vanuit de
backend bereikbaar zijn. De Keycloak-handleiding beschrijft de verplichte
audience-mapper voor client `pi`.
Ontbrekende, verlopen of ongeldige tokens geven HTTP 401 zonder uitvoering
van het commando. React gebruikt de bestaande `/api`-proxy; CORS is niet nodig.

## Container met Podman

Gebruik op de Pi een losse rootless container op netwerk `pi-app`.
De backendcontainer moet Keycloak kunnen bereiken en deelt dit netwerk met
de [frontend](../frontend/README.md).
Configureer [Keycloak](../keycloak/README.md) en de
[HTTPS-proxy](../proxy/README.md) volgens hun eigen handleidingen.
Maak vooraf het [gedeelde Podman-netwerk](../README.md#gedeeld-podman-netwerk)
aan.

Voer vanuit `backend` uit:

```sh
podman build -t localhost/pi-backend . &&
podman run -d --replace --name pi-backend \
  --network pi-app \
  --userns=keep-id:uid=10001,gid=10001 \
  --add-host login.thomasmichels.nl:host-gateway \
  -e PI_COMMANDS_DIRECTORY=/data \
  -v pi-backend-data:/data \
  -v /home/thomas/muziek:/home/thomas/muziek:ro \
  localhost/pi-backend
```

- `-t localhost/pi-backend .`  tagt met `localhost/pi-backend`.
- `-d` start op de achtergrond.
- `--replace --name pi-backend` vervangt een eventuele bestaande container
  met deze naam.
- `--network pi-app` maakt de backend via `http://pi-backend:8080` bereikbaar
  voor de frontend op netwerk pi-app. Er is geen `-p` ingesteld:
  apparaten gebruiken `https://pi.thomasmichels.nl/api` via de frontend en
  HTTPS-proxy.
- `--userns=keep-id:uid=10001,gid=10001` koppelt de huidige hostgebruiker aan
  de containergebruiker met UID en GID `10001`, zoals ingesteld in de Containerfile.
- `--add-host login.thomasmichels.nl:host-gateway` laat de backend Keycloak
  via de Pi bereiken. De issuer blijft de externe HTTPS-URL, zodat deze
  overeenkomt met tokens.
- `-e PI_COMMANDS_DIRECTORY=/data` stelt de werkmap voor de Linux-acties in.
- `-v pi-backend-data:/data` bewaart de aangemaakte bestanden in een
  Podman-volume.
- `-v /home/thomas/muziek:/home/thomas/muziek:ro` mount volume read only voor de actie `/api/commands/music-directories`.

## Actie en API

Zie [Swagger UI](https://pi.thomasmichels.nl/api/swagger-ui.html) en de
[gegenereerde OpenAPI-specificatie](https://pi.thomasmichels.nl/api/v3/api-docs).
Beide zijn zonder login bereikbaar; voor het uitvoeren van acties is een
bearer access token nodig via **Authorize**.