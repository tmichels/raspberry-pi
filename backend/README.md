# Raspberry Pi backend

Een zelfstandige Java/Spring Boot-API voor vaste Linux-acties. Alle endpoints
vereisen een Keycloak bearer access token, behalve de publieke
GET/HEAD-endpoints `/api/music/playlist` en `/api/music/stream`.

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
afspeelbaar. GET en HEAD zijn publiek beschikbaar zonder bearer token of
cookie, zodat native muziekapps de URL's zelfstandig kunnen gebruiken.
Een meegestuurd ongeldig bearer token kan wel HTTP 401 opleveren.
De bibliotheek (`GET /api/music`) en albumhoezen blijven beveiligd.

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
HTTP 404. Een meegestuurd ongeldig bearer token geeft HTTP 401. I/O-fouten, inclusief
een ontbrekende muziekroot, gebruiken de bestaande HTTP 500-foutafhandeling.
Ongeldige trackpaden en I/O-fouten krijgen een `application/problem+json`
antwoord zonder bestandspaden in de fouttekst.

De muziekmap moet door vertrouwde processen worden beheerd: padcontrole en
het openen door Spring zijn geen atomaire bewerking. Gelijktijdige vervanging
van bestanden of directories tijdens het streamen wordt niet afgevangen.

## Bestaande albumafspeellijst openen

`GET /api/music/playlist?path=<album.path>` leest het bestaande `.m3u`-bestand
rechtstreeks uit de albummap. Gebruik `Album.path` uit de bibliotheek als
UTF-8 URL-gecodeerde queryparameter. Er moet precies één bestand met deze
extensie zijn (hoofdletterongevoelig); subdirectories worden niet doorzocht.
De API genereert nooit een trackvolgorde uit bestandsnamen of tags.
Een expliciete lege `path=` verwijst naar de muziekroot, overeenkomstig
`Album.path=""` bij tracks direct in de root. Een ontbrekende parameter of
een pad met alleen witruimte wordt geweigerd.

Het antwoord is een standaard extended M3U in UTF-8:
`audio/x-mpegurl; charset=UTF-8`, met `Content-Disposition: inline` en
een veilige UTF-8 `.m3u`-bestandsnaam. Daardoor vraagt de browser niet om een
download, maar geeft hij de afspeellijst aan de speler die op het apparaat
voor M3U is ingesteld. Is er geen speler voor M3U ingesteld, dan kan de
browser het bestand alsnog opslaan.

- De volgorde en duplicaten uit het bronbestand blijven behouden.
- Geldige `#EXTINF:<duur>,<titel>`-regels blijven behouden (duur `-1` of een
  niet-negatief, eventueel decimaal getal). De uitvoer begint met `#EXTM3U`.
- BOM, CRLF, lege regels en gewone commentaarregels worden ondersteund.
  Gewone opmerkingen worden niet overgenomen; andere `#EXT...`-directieven
  (zoals HLS/VLC-opties), ongeldige/dubbele of verweesde EXTINF worden geweigerd.
- Lokale relatieve bestandsnamen/subpaden en Windows-padscheidingstekens
  worden ondersteund. `..` mag alleen binnen de muziekroot blijven.
  Absolute lokale paden (ook Windows-drivepaden), netwerkpaden en URI's
  zoals `http:`, `https:` en `file:` worden niet ondersteund.
- Elke track moet bestaan en aan dezelfde extensie-, diepte- en
  symlinkcontroles voldoen als streaming. Er is geen basename-zoekactie,
  dubbele URL-decodering of stilzwijgend overslaan van ongeldige regels.
- Elke trackregel wordt een absolute, correct UTF-8-gecodeerde URL naar
  `/api/music/stream?path=...`, zonder tokens. Dit blijft werken wanneer
  de afspeellijst wordt opgeslagen of op een ander apparaat wordt geopend.

Een ontbrekend album of ontbrekende M3U geeft HTTP 404. Een ongeldig of absoluut
albumpad (ook alleen witruimte), traversal buiten de root, symlinks, te diepe
albumpaden, meerdere M3U-bestanden, lege of ongeldige afspeellijsten (ook
ongeldige UTF-8) en ontbrekende of onveilige tracks geven HTTP 400. Albumdirectories mogen maximaal
vier niveaus diep zijn, zodat de M3U en tracks binnen de bibliotheekdiepte
van vijf niveaus vallen. De geconfigureerde muziekroot zelf mag een symlink
zijn. Een ontbrekende/kapotte root en overige I/O-fouten geven de bestaande
HTTP 500. Validatiefouten en I/O-fouten gebruiken `application/problem+json`
zonder lokale bestandspaden.

### Publieke toegang en reverse proxy

**Playlist en stream zijn publiek, zowel op LAN als op internet.** Iedereen
die de server en een geldig pad kan bereiken, kan muziek downloaden.
Deze toegang is niet beperkt tot ingelogde gebruikers of het lokale netwerk.
Andere HTTP-methoden en overige applicatie-endpoints blijven beveiligd.

`server.forward-headers-strategy=framework` laat Spring de oorspronkelijke
requestorigin verwerken. De vertrouwde reverse proxy moet `Host`,
`X-Forwarded-Proto`, `X-Forwarded-Host` en `X-Forwarded-Port` correct zetten;
HTTPS, niet-standaardpoorten en de applicatiecontext blijven zo in de
absolute stream-URL's behouden. Er is geen hardcoded host of clientparameter
voor een basis-URL.

**Vertrouwensgrens:** stel de backend uitsluitend via een vertrouwde proxy
beschikbaar die door clients aangeleverde forwarded-headers overschrijft of
verwijdert (ook `Forwarded` en eventuele `X-Forwarded-Prefix`). Directe
onvertrouwde toegang kan anders de origin in gegenereerde playlists beïnvloeden.
De muziekmap moet, net als bij streaming, door vertrouwde processen worden
beheerd; padcontrole en het lezen zijn niet atomair.
