# Raspberry Pi frontend

React/TypeScript-applicatie met Vite. De knop maakt via de backend een bestand
aan en toont de exitcode, output of foutmelding. Inloggen verloopt via Keycloak
(realm `donna`, publieke client `pi`, Authorization Code met PKCE).
De bediening wordt pas na inloggen getoond. Access tokens blijven in het
geheugen en worden voor API-verzoeken zo nodig vernieuwd.

Voer de commando's uit vanuit `frontend`. Gebruik de applicatie op een
vertrouwd lokaal netwerk. Configureer eerst client `pi` zoals beschreven in
[`../keycloak/README.md`](../keycloak/README.md).

## Lokaal starten

Vereist Node.js 26 LTS met npm en een draaiende backend.

`frontend/.env` bevat de instellingen voor lokaal ontwikkelen met Keycloak
op de Pi en de backend op `127.0.0.1:8080`. Vite leest dit bestand automatisch.
Gebruik in IntelliJ een npm-runconfiguratie voor `dev`, met `frontend` als
werkmap. Je kunt hetzelfde bestand ook als environmentbestand laden.
De Pi moet bereikbaar zijn en Keycloak-client `pi` moet de localhost-URL's
toestaan volgens de Keycloak-README.

```sh
npm ci
BACKEND_URL=http://127.0.0.1:8080 npm run dev
```

Open `http://localhost:5173`; je wordt doorgestuurd naar Keycloak.
Het meegeleverde `.env` gebruikt `https://login.thomasmichels.nl`.
Zonder configuratie valt de code terug op `http://192.168.178.96:9000`.
Voor een andere URL:

```sh
VITE_KEYCLOAK_URL=https://login.thomasmichels.nl \
  BACKEND_URL=http://127.0.0.1:8080 npm run dev
```

Toegang via een LAN-IP (bijvoorbeeld vanaf je telefoon) vereist HTTPS voor
frontend en Keycloak; zie de Keycloak-README. Alleen localhost mag voor lokale
ontwikkeling HTTP gebruiken vanwege de Web Crypto-eis van PKCE.

## Deployen met Podman

Gebruik op de Pi losse rootless containers op netwerk `pi-app`.
Volg eerst de [HTTPS-handleiding](../proxy/README.md) voor certificaten,
Nginx, Keycloak en het aanmaken van het netwerk. Start de
[backend](../backend/README.md#container-met-podman) op hetzelfde netwerk.
De onderstaande commando's gaan uit van een eerste start, zonder bestaande
frontendcontainer.

Bouw en start vanuit `frontend`:

```sh
podman build --build-arg VITE_KEYCLOAK_URL=https://login.thomasmichels.nl \
  -t localhost/pi-frontend . &&
podman run -d --name pi-frontend \
  --network pi-app \
  -p 127.0.0.1:8081:8080 \
  -e BACKEND_URL=http://pi-backend:8080 \
  localhost/pi-frontend
```

Open `https://pi.thomasmichels.nl`, niet de interne HTTP-poort.
De loopbackbinding maakt poort 8081 alleen op de Pi bereikbaar voor Nginx.
Configureer de exacte redirect-, logout- en web-originwaarden in Keycloak
volgens de HTTPS-handleiding.
`VITE_KEYCLOAK_URL` zit in de build; geef een wijziging dus bij het bouwen mee,
niet als omgevingsvariabele aan `podman run`. Zonder build-argument wordt de
HTTP-Keycloak-URL uit de lokale configuratie gebruikt.

`BACKEND_URL` bevat het backendadres met protocol en poort, zonder pad of
afsluitende slash. Nginx stuurt `/api` door naar dit adres.
Podman vertaalt `pi-backend` binnen `pi-app` naar de backendcontainer.
Daardoor hoeft de backend geen poort op de Pi te publiceren. Het netwerk
wordt bij `podman run` gekozen, niet in de Containerfile.

### Bijwerken

```sh
podman build --build-arg VITE_KEYCLOAK_URL=https://login.thomasmichels.nl \
  -t localhost/pi-frontend . &&
podman stop pi-frontend &&
podman rm pi-frontend
```

Voer daarna het startcommando opnieuw uit. Ook een gewijzigd backendadres
geef je mee via dat startcommando.

Logs bekijken: `podman logs pi-frontend`.

## Build en tests

```sh
npm test
npm run build
BACKEND_URL=http://127.0.0.1:8080 npm run preview
```

De build controleert TypeScript en schrijft naar `dist`.
De preview is bereikbaar op `http://localhost:4173`.

## API-contract

| Verzoek/resultaat | Formaat |
|---|---|
| Verzoek | `POST /api/commands/touch`, `Content-Type: application/json`, body `{}` |
| Authenticatie | `Authorization: Bearer <access-token>` (geen ID token) |
| Afgerond commando | HTTP 200 met `{"output":"","exitCode":0}` |
| Ongeldig/ontbrekend token | HTTP 401; herlaad de pagina om opnieuw in te loggen |
| Serverfout | HTTP-foutstatus met `{"detail":"Foutmelding"}` |

`output` is een string en `exitCode` een getal. Een exitcode ongelijk aan
nul wordt als mislukt commando getoond.
Een mislukte tokenvernieuwing wordt getoond zonder een API-verzoek te sturen.
API-verzoeken worden niet automatisch herhaald om dubbele uitvoering te voorkomen.
Met **Uitloggen** wordt de sessie bij Keycloak beeindigd.
