# Raspberry Pi frontend

React/TypeScript-applicatie met Vite. De knop maakt via de backend een bestand
aan en toont de exitcode, output of foutmelding.

Voer de commando's uit vanuit `frontend`. Gebruik de applicatie op een
vertrouwd lokaal netwerk.

## Lokaal starten

Vereist Node.js 26 LTS met npm en een draaiende backend.

```sh
npm ci
BACKEND_URL=http://127.0.0.1:8080 npm run dev
```

Open `http://localhost:5173`. Voor toegang vanaf je telefoon voeg je
`-- --host 0.0.0.0` toe en open je `http://<ip-adres-van-je-computer>:5173`.

## Deployen met Podman

Kopieer deze map naar een Raspberry Pi met ARM64 en Podman. Bouw en start:

```sh
podman build -t localhost/pi-frontend .
podman run -d --name pi-frontend \
  -p 8081:8080 \
  -e BACKEND_URL=http://host.containers.internal:8080 \
  localhost/pi-frontend
```

Open `http://<ip-adres-van-de-pi>:8081`.

`BACKEND_URL` bevat het backendadres met protocol en poort, zonder pad of
afsluitende slash. Nginx stuurt `/api` door naar dit adres.
`host.containers.internal` verwijst naar de Pi; de backend moet luisteren
op een interface die vanuit de container bereikbaar is.

Voor een backendcontainer gebruik je een gedeeld Podman-netwerk:

```sh
podman network create pi-app
```

Start de backend met `--network pi-app --name backend`. Voeg aan het
frontendcommando `--network pi-app` toe en gebruik
`-e BACKEND_URL=http://backend:8080`.

### Bijwerken

```sh
podman build -t localhost/pi-frontend .
podman stop pi-frontend
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
| Afgerond commando | HTTP 200 met `{"output":"","exitCode":0}` |
| Serverfout | HTTP-foutstatus met `{"detail":"Foutmelding"}` |

`output` is een string en `exitCode` een getal. Een exitcode ongelijk aan
nul wordt als mislukt commando getoond.
