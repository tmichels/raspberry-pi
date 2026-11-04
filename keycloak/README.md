# Keycloak

## Starten achter de HTTPS-proxy

Volg de [HTTPS-handleiding](../proxy/README.md) voor certificaten, Nginx
en lokale naamherkenning. Keycloak draait als losse rootless container;
de proxy bereikt hem via een loopbackpoort op de Pi.

Voer op de Pi, zonder `sudo`, uit bij een bestaande adminaccount in
volume `keycloak-data` en een vrije containernaam:

```sh
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

Bij een eerste start met een leeg volume zijn ook
`KC_BOOTSTRAP_ADMIN_USERNAME` en `KC_BOOTSTRAP_ADMIN_PASSWORD` nodig.

Voeg voor die eerste start aan het bovenstaande `podman run` toe,
vóór de imagenaam:

```sh
  -e KC_BOOTSTRAP_ADMIN_USERNAME=admin \
  -e KC_BOOTSTRAP_ADMIN_PASSWORD="$KC_BOOTSTRAP_ADMIN_PASSWORD" \
```

Open `https://login.thomasmichels.nl/admin/`. Nginx handelt HTTPS af en
geeft het oorspronkelijke protocol door via `X-Forwarded-Proto`.
`KC_PROXY_HEADERS=xforwarded` laat Keycloak de juiste HTTPS-adressen maken.
De binding aan `127.0.0.1` voorkomt rechtstreekse HTTP-toegang vanaf het LAN.

## Authenticatie voor frontend en backend

De applicaties gebruiken OpenID Connect/OAuth 2.0 met realm `donna` en client
`pi`. React gebruikt Authorization Code met PKCE (`S256`); de backend valideert
de handtekening, geldigheid, issuer en audience van het bearer access token.
Alle ingelogde gebruikers van deze realm met audience `pi` krijgen toegang.
Er zijn geen extra rollen vereist.

Configureer in de Keycloak Admin Console realm `donna`, client `pi`:

| Instelling | Waarde |
|---|---|
| Client type | OpenID Connect |
| Client authentication | Off (publieke client, geen secret in React) |
| Standard flow | On |
| Implicit flow | Off |
| Direct access grants | Off |
| PKCE Code Challenge Method (Advanced) | S256 |
| Valid redirect URIs | `https://pi.thomasmichels.nl` en `https://pi.thomasmichels.nl/` |
| Valid post logout redirect URIs | Dezelfde URL's als hierboven |
| Web origins | `https://pi.thomasmichels.nl` |

Gebruik expliciete URL's, geen wildcard `*`. De frontend gebruikt zijn eigen
origin als login- en logoutcallback. Maak een gebruiker in realm `donna`
met een wachtwoord.

Sta beide redirectvarianten toe om een mismatch door de afsluitende slash
te voorkomen. Voor lokale ontwikkeling kun je ook `http://localhost:5173`
en `http://localhost:4173` aan de drie instellingen toevoegen.

Voeg onder **Client scopes > pi-dedicated > Mappers** een mapper van type
**Audience** toe met **Included Client Audience** = `pi`,
**Add to access token** = On en **Add to ID token** = Off.
Deze mapper moet standaard worden toegepast, niet alleen via een optionele
scope. Zonder audience `pi` weigert de backend het token met HTTP 401.

Gebruik `VITE_KEYCLOAK_URL=https://login.thomasmichels.nl` voor de frontend
tijdens bouwen of starten van Vite, en
`KEYCLOAK_ISSUER_URI=https://login.thomasmichels.nl/realms/donna`
bij het starten van de backend. Zonder die variabelen gebruiken de apps
nog de oude HTTP-ontwikkeladressen.
De issuer moet exact overeenkomen met de `iss`-claim van Keycloak; vervang hem
niet door een intern containeradres. De backend moet die URL kunnen bereiken
om de publieke sleutels van Keycloak op te halen.

## HTTP en HTTPS

Voor lokale ontwikkeling kun je de frontend openen via
`http://localhost:5173`: localhost geldt als secure context in de
browser. Een frontend op `http://<lan-ip>:8081` of `http://<lan-ip>:5173` is dat
niet; de Keycloak-adapter heeft de Web Crypto API nodig voor PKCE en werkt daar
niet. Voor toegang vanaf je telefoon of een andere computer moet de frontend
via HTTPS met een vertrouwd certificaat worden aangeboden.

De bovenstaande proxyopzet biedt frontend en Keycloak beide via HTTPS aan.
Een HTTPS-frontend kan geen HTTP-Keycloak gebruiken vanwege mixed-content-
beperkingen. Houd de frontend-URL, backend-issuer en clientinstellingen
consistent en publiceer de interne HTTP-poorten niet op het LAN.