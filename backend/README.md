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

Zie [Swagger UI](https://pi.thomasmichels.nl/api/swagger-ui.html) en de gegenereerde [OpenAPI-specificatie](https://pi.thomasmichels.nl/api/v3/api-docs).
Beide zijn zonder login bereikbaar; voor het uitvoeren van acties is een
bearer access token nodig via **Authorize**.

## Audiotracks streamen

`GET /api/music/stream?path=<trackpad>` gebruikt `Track.path` uit
`GET /api/music`, URL-gecodeerd als queryparameter en relatief aan
`pi.music.directory`. Ook tracks met ontbrekende of onleesbare tags zijn
afspeelbaar. Iedere aanvraag vereist hetzelfde bearer access token in de
`Authorization`-header; er is geen token in de URL, cookie-authenticatie of
extra rolvereiste.

Alleen reguliere bestanden met de bibliotheekextensies (hoofdletterongevoelig)
en maximaal vijf padniveaus zijn beschikbaar. Absolute paden, lege/blanke
paden, ongeldige padtekens, traversal buiten de muziekroot en symlinks in het
trackpad worden geweigerd. De geconfigureerde muziekroot zelf mag een symlink
zijn; de werkelijke root wordt gebruikt. Paden worden door Spring als
queryparameter gedecodeerd, niet nogmaals door de applicatie.

| Extensie | Content-Type |
| --- | --- |
| mp3 | audio/mpeg |
| flac | audio/flac |
| wav | audio/wav |
| ogg | audio/ogg |
| m4a | audio/mp4 |
| aac | audio/aac |

Zonder `Range` volgt HTTP 200 met de volledige bestandsinhoud en
`Content-Length`. Spring MVC handelt `Range: bytes=...` af via een
bestandsresource: HTTP 206 met `Content-Range` voor één bereik, of
`multipart/byteranges` voor meerdere bereiken. Open en suffixbereiken worden
ondersteund; `Accept-Ranges: bytes` wordt door Spring toegevoegd.
Ongeldige/onbeschikbare bereiken geven HTTP 416 met `Content-Range: bytes */<lengte>`
en een lege body. De controller valideert met dezelfde Spring-rangefuncties
voordat de resource wordt teruggegeven; geldige bereiken blijven door Spring MVC afgehandeld.
Dit endpoint implementeert geen aanvullende `If-Range`/ETag-afhandeling.

Een ontbrekende of ongeldige `path` geeft HTTP 400; een ontbrekend bestand,
directory, niet-audiobestand of bestand buiten de bibliotheekdiepte geeft
HTTP 404. Een ontbrekend/ongeldig token geeft HTTP 401. I/O-fouten, inclusief
een ontbrekende muziekroot, gebruiken de bestaande HTTP 500-foutafhandeling.
Ongeldige trackpaden en I/O-fouten krijgen een `application/problem+json`
antwoord zonder bestandspaden in de fouttekst.

De muziekmap moet door vertrouwde processen worden beheerd: padcontrole en
het openen door Spring zijn geen atomaire bewerking. Gelijktijdige vervanging
van bestanden of directories tijdens het streamen wordt niet afgevangen.
