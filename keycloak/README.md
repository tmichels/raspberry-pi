# Keycloak

## Starten achter de HTTPS-proxy

Volg de [HTTPS-handleiding](../proxy/README.md) voor certificaten, Nginx
en lokale naamherkenning. Keycloak draait als losse rootless container;
de proxy bereikt hem via een loopbackpoort op de Pi.

Voer op de Pi, uit bij een bestaande adminaccount in
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

Bij een eerste start met een leeg volume zijn deze parameters nodig:
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
`pi`. React gebruikt Authorization Code met PKCE (`S256`). De
[backendhandleiding](../backend/README.md) beschrijft de tokenvalidatie en
toegangsregels.

Configureer in de Keycloak Admin Console realm `donna`, client `pi`:

| Instelling | Waarde |
|---|---|
| Client type | OpenID Connect |
| Client authentication | Off (publieke client, geen secret in React) |
| Standard flow | On |
| Implicit flow | Off |
| Direct access grants | Off |
| PKCE Code Challenge Method (Advanced) | S256 |
| Valid redirect URIs | `https://pi.thomasmichels.nl`, `https://pi.thomasmichels.nl/`, `http://localhost:5173`, `http://localhost:5173/`, `http://localhost:4173` en `http://localhost:4173/` |
| Valid post logout redirect URIs | Dezelfde URL's als hierboven |
| Web origins | `https://pi.thomasmichels.nl`, `http://localhost:5173` en `http://localhost:4173` |

Gebruik expliciete URL's, geen wildcard `*`. De frontend gebruikt zijn eigen
origin als login- en logoutcallback. Maak een gebruiker in realm `donna`
met een wachtwoord.

### Aangeven voor welke applicatie het token bedoeld is

Na het inloggen geeft Keycloak de frontend een access token mee om de backend
aan te roepen. In dat token staat een veld `aud` (*audience*): de applicaties
waarvoor het token bedoeld is. De backend accepteert alleen tokens waarin
`pi` als audience staat. Alleen succesvol inloggen is dus niet voldoende.

Een *mapper* is een Keycloak-instelling die informatie aan tokens toevoegt.
Maak een Audience-mapper voor client `pi`:

1. Selecteer realm **donna** in de Admin Console en open **Clients > pi**.
2. Open het tabblad **Client scopes**, klik op **pi-dedicated** en open
   **Mappers**. Dit is de verzameling tokeninstellingen die specifiek bij
   client `pi` hoort.
3. Kies **Configure a new mapper** en selecteer het type **Audience**.
4. Geef de mapper een herkenbare naam, bijvoorbeeld `pi-audience`, en stel
   de onderstaande waarden in. Klik daarna op **Save**.

| Instelling | Waarde | Betekenis |
|---|---|---|
| Included Client Audience | `pi` | Voegt `pi` toe aan het `aud`-veld. |
| Add to access token | On | Past de instelling toe op het token waarmee de frontend de API aanroept. |
| Add to ID token | Off | Het ID token beschrijft de ingelogde gebruiker en wordt niet gebruikt voor API-verzoeken. |

Door de mapper onder **pi-dedicated** te plaatsen wordt hij automatisch
toegepast bij het inloggen met client `pi`. Plaats hem niet uitsluitend in
een optionele client scope: zo'n scope werkt alleen als de frontend er
expliciet om vraagt. Zonder audience `pi` weigert de backend het access token
met HTTP 401, ook als de gebruiker wel is ingelogd. Log na het wijzigen opnieuw
in om een nieuw token met de audience te krijgen.

Stel de Keycloak-URL en issuer in zoals beschreven in de
[frontend](../frontend/README.md) en [backend](../backend/README.md)-handleidingen.

## HTTP en HTTPS

Voor lokale ontwikkeling kun je de frontend openen via
`http://localhost:5173`: localhost geldt als secure context in de
browser. Een frontend op `http://<lan-ip>:8081` of `http://<lan-ip>:5173` is dat
niet; de Keycloak-adapter heeft de Web Crypto API nodig voor PKCE en werkt daar
niet. Voor toegang vanaf je telefoon of een andere computer moet de frontend
via HTTPS met een vertrouwd certificaat worden aangeboden.

De proxyopzet biedt frontend en Keycloak beide via HTTPS aan.
Een HTTPS-frontend kan geen HTTP-Keycloak gebruiken vanwege mixed-content-
beperkingen. Houd de frontend-URL, backend-issuer en clientinstellingen
consistent en publiceer de interne HTTP-poorten niet op het LAN.