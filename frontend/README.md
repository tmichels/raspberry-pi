# Raspberry Pi frontend

React/TypeScript-applicatie met Vite.
Access tokens blijven in het geheugen en worden voor API-verzoeken zo nodig
vernieuwd. De clientconfiguratie staat in de
[Keycloak-handleiding](../keycloak/README.md).

## Lokaal starten

Vereist Node.js 26 LTS met npm en een draaiende backend.

`frontend/.env` bevat het backendadres voor lokaal ontwikkelen.
Vite leest dit bestand automatisch.
Gebruik in IntelliJ een npm-runconfiguratie voor `dev`, met `frontend` als
werkmap.

```sh
npm ci
npm run dev
```

Open `http://localhost:5173`; je wordt doorgestuurd naar Keycloak.
De Keycloak-URL staat vast in `src/auth.ts` en is in elke omgeving hetzelfde.

De frontend gebruikt PKCE (*Proof Key for Code Exchange*) bij het inloggen.
De browser maakt voor elke login een willekeurige code en stuurt eerst alleen
de hash daarvan naar Keycloak. Bij het inwisselen van de ontvangen
autorisatiecode voor tokens moet hij de oorspronkelijke code meesturen.
Daardoor kan iemand die alleen de autorisatiecode onderschept deze niet
zelf inwisselen. Een client secret in de frontend is hiervoor niet nodig.

De Keycloak-library gebruikt hiervoor de Web Crypto API van de browser.
Die is alleen beschikbaar in een *secure context*: HTTPS, of HTTP op
localhost voor lokale ontwikkeling.

## Deployen met Podman

Gebruik op de Pi een losse rootless container op netwerk `pi-app`.
Stel de [HTTPS-proxy](../proxy/README.md) in en volg de
[Keycloak-handleiding](../keycloak/README.md) voor de Keycloak-container en
clientinstellingen. Maak vooraf het
[gedeelde Podman-netwerk](../README.md#gedeeld-podman-netwerk) aan en start de
[backend](../backend/README.md#container-met-podman).
De onderstaande commando's gaan uit van een eerste start, zonder bestaande
frontendcontainer.

Bouw en start vanuit `frontend`:

```sh
podman build -t localhost/pi-frontend . &&
podman run -d --replace --name pi-frontend \
  --network pi-app \
  -p 127.0.0.1:8081:8080 \
  -e BACKEND_URL=http://pi-backend:8080 \
  localhost/pi-frontend
```

Open `https://pi.thomasmichels.nl`; de interne HTTP-poort is niet bereikbaar
vanaf het LAN, want `-p 127.0.0.1:8081:8080` bindt deze alleen aan het
binnen de Pi. De HTTPS-proxy draait met `--network host` en kan daardoor
de frontend via `127.0.0.1:8081` bereiken. Andere apparaten verbinden via
HTTPS met de proxy, die het verzoek intern doorstuurt naar de frontend.
Configureer de exacte redirect-, logout- en web-originwaarden in Keycloak
volgens de Keycloak-handleiding.

`BACKEND_URL` bevat het backendadres met protocol en poort, zonder pad of
afsluitende slash. Nginx stuurt `/api` door naar dit adres.
Podman vertaalt `pi-backend` binnen `pi-app` naar de backendcontainer.
Daardoor hoeft de backend geen poort op de Pi te publiceren.

### Bijwerken

```sh
podman build -t localhost/pi-frontend . &&
podman stop pi-frontend &&
podman rm pi-frontend
```

Voer daarna het startcommando opnieuw uit.

## Build en tests

```sh
npm test
npm run build
BACKEND_URL=http://127.0.0.1:8080 npm run preview
```

De build controleert TypeScript en schrijft naar `dist`.
De preview is bereikbaar op `http://localhost:4173`.
