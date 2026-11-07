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

## Muziekbibliotheek

`GET /api/music` geeft een `MusicLibrary`-record terug als JSON met
`artists`, per artiest `name` en `albums`, en per album `name` en `tracks`.
De service scant de muziekmap bij iedere aanvraag recursief met de Java
Files-API en leest audiometadata met jaudiotagger, zonder shellcommando's
of `CommandService`. De bestaande commando-endpoints blijven ongewijzigd.

Configureer de map met `pi.music.directory` (standaard `/home/thomas/muziek`)
of de omgevingsvariabele `PI_MUSIC_DIRECTORY`. Compose mount de muziekmap
al alleen-lezen op deze standaardlocatie. Symbolische links worden niet
gevolgd. Artiesten en albums worden op naam gesorteerd, tracks op hun
relatieve bestandspad. De eerste ARTIST-, ALBUM- en TITLE-tag bepalen de
groepering en titel; de mapnamen spelen daarbij geen rol.

Bestanden worden geselecteerd op audio-extensie (hoofdletterongevoelig):
ogg, oga, mp3, flac, mp4, m4a, m4p, wma, wav, ra, rm, m4b, aif, aiff, aifc,
dsf en dff. Andere bestanden worden genegeerd. Een formaat dat de lezer
niet kan verwerken of een beschadigd bestand wordt behandeld als een bestand
zonder tags.

Elke track bevat `title`, `path` (relatief aan de muziekmap), `status` en
`missingTags`. De status is `OK` wanneer alle drie tags aanwezig zijn.
Bij ontbrekende of lege tags is de status `MISSING_TAGS` en vermeldt
`missingTags` de ontbrekende velden (`ARTIST`, `ALBUM`, `TITLE`).
Ontbrekende artiesten/albums worden gegroepeerd onder `Onbekende artiest`
en `Onbekend album`; bij een ontbrekende titel wordt de bestandsnaam gebruikt.
Onleesbare bestanden blijven zichtbaar met status `MISSING_TAGS` en de
bestandsnaam als titel; de oorzaak wordt gelogd.

Een lege muziekmap geeft `{"artists":[]}`. Een ontbrekende of niet leesbare
map geeft HTTP 500 met een ProblemDetail en een foutmelding in de serverlogs.
Ook dit endpoint vereist een geldig bearer access token.