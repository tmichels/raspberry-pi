# Raspberry Pi backend

Een zelfstandige Java/Spring Boot-API voor vaste Linux-acties. Alle endpoints
vereisen een Keycloak bearer access token (behalve Swagger).

## Authenticatie

De backend is een stateless OAuth 2.0 Resource Server. Spring Security
valideert de JWT-handtekening met de publieke sleutels van Keycloak. Zie de
[Keycloak-handleiding](../keycloak/README.md). CSRF is uitgeschakeld omdat
uitsluitend bearer tokens in de Authorization-header worden gebruikt, geen
cookies.

De issuer staat in `src/main/resources/application.properties` en is
`https://login.thomasmichels.nl/realms/donna`. Deze moet exact overeenkomen
met Keycloak's `iss`-claim en vanuit de backend bereikbaar zijn. De
Keycloak-handleiding beschrijft de verplichte audience-mapper voor client
`pi`.

Ontbrekende, verlopen of ongeldige tokens geven HTTP 401 zonder uitvoering van
het commando. React gebruikt de bestaande `/api`-proxy; CORS is niet nodig.

## Deployen met Podman Compose

De backend draait als rootless Compose-service `pi-backend`; de
`compose.yaml` regelt de container, het netwerk, de persistente data en de
verbinding met Keycloak en de frontend. Start of bouw alle services volgens
de [root-README](../README.md#applicaties-starten-met-podman-compose).

## Actie en API

Zie [Swagger UI][swagger] en de gegenereerde [OpenAPI-specificatie][api].
Beide zijn zonder login bereikbaar; voor het uitvoeren van acties is een
bearer access token nodig via **Authorize**.

[swagger]: https://pi.thomasmichels.nl/api/swagger-ui.html
[api]: https://pi.thomasmichels.nl/api/v3/api-docs