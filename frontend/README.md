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

## Deployen met Podman Compose

De frontend draait als rootless Compose-service `pi-frontend`. Compose bouwt
de container en verbindt die met de backend op het interne netwerk. Alleen
`127.0.0.1:8081` wordt op de Pi gepubliceerd; de HTTPS-proxy bereikt de
frontend daar en biedt hem aan op `https://pi.thomasmichels.nl`. Start of
bouw de services volgens de
[root-README](../README.md#applicaties-starten-met-podman-compose).
Configureer de redirect-, logout- en web-originwaarden in Keycloak volgens
de [Keycloak-handleiding](../keycloak/README.md).

## Build en tests

```sh
npm test
npm run build
BACKEND_URL=http://127.0.0.1:8080 npm run preview
```

De build controleert TypeScript en schrijft naar `dist`.
De preview is bereikbaar op `http://localhost:4173`.
