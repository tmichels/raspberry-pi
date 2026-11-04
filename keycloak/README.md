# Keycloak

Opstartcommando:
```bash
podman run --replace -d --name keycloak \
  -p 9000:8080 \
  -v keycloak-data:/opt/keycloak/data \
  -e KC_HTTP_ENABLED=true \
  -e KC_HOSTNAME=http://192.168.178.96:9000 \
  -e KC_BOOTSTRAP_ADMIN_USERNAME=admin \
  -e KC_BOOTSTRAP_ADMIN_PASSWORD='admin' \
  docker.io/keycloak/keycloak:26.8.1 start
```

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
| Valid redirect URIs | `http://localhost:5173` en `http://localhost:4173` voor lokale ontwikkeling; voeg de exacte HTTPS-origin van de gedeployde frontend toe |
| Valid post logout redirect URIs | Dezelfde origins als hierboven |
| Web origins | Dezelfde origins als hierboven |

Gebruik expliciete URL's, geen wildcard `*`. De frontend gebruikt zijn eigen
origin als login- en logoutcallback. Maak een gebruiker in realm `donna`
met een wachtwoord.

Voeg onder **Client scopes > pi-dedicated > Mappers** een mapper van type
**Audience** toe met **Included Client Audience** = `pi`,
**Add to access token** = On en **Add to ID token** = Off.
Deze mapper moet standaard worden toegepast, niet alleen via een optionele
scope. Zonder audience `pi` weigert de backend het token met HTTP 401.

De standaard Keycloak-URL in React is `http://192.168.178.96:9000`.
De backend gebruikt bijbehorende issuer
`http://192.168.178.96:9000/realms/donna`. Met `VITE_KEYCLOAK_URL`
(frontend, tijdens bouwen/starten van Vite) en `KEYCLOAK_ISSUER_URI`
(backend, bij starten) kun je die adressen wijzigen.
De issuer moet exact overeenkomen met de `iss`-claim van Keycloak; vervang hem
niet door een intern containeradres. De backend moet die URL kunnen bereiken
om de publieke sleutels van Keycloak op te halen.

## HTTP en HTTPS

Het bovenstaande opstartcommando is voor lokale ontwikkeling. Open de frontend
lokaal via `http://localhost:5173`: localhost geldt als secure context in de
browser. Een frontend op `http://<lan-ip>:8081` of `http://<lan-ip>:5173` is dat
niet; de Keycloak-adapter heeft de Web Crypto API nodig voor PKCE en werkt daar
niet. Voor toegang vanaf je telefoon of een andere computer moet de frontend
via HTTPS met een vertrouwd certificaat worden aangeboden.

Bied dan ook Keycloak via HTTPS aan (bijvoorbeeld met een TLS-reverse-proxy),
stel `KC_HOSTNAME` in op die publieke HTTPS-URL en gebruik bij TLS-terminatie
de passende `KC_PROXY_HEADERS`-instelling van Keycloak. Pas de frontend-URL,
backend-issuer en redirect-/logout-/web-origininstellingen samen aan.
Een HTTPS-frontend kan geen HTTP-Keycloak gebruiken vanwege mixed-content-
beperkingen. Gebruik buiten lokale ontwikkeling sterke admincredentials en
stel de onversleutelde poorten niet publiek beschikbaar.