# Raspberry Pi bediening

Dit project bestaat uit een React-frontend, een Spring Boot-backend,
Keycloak-authenticatie en een Nginx HTTPS-proxy.

## Handleidingen

- [Frontend](frontend/README.md) — lokaal ontwikkelen, bouwen, tests en de
  frontendcontainer.
- [Backend](backend/README.md) — API, tests en de
  backendcontainer.
- [Keycloak](keycloak/README.md) — Keycloak starten en de realm/client
  configureren.
- [HTTPS-proxy](proxy/README.md) — certificaten en Nginx op de Pi.

## Waar te beginnen

Voor lokale ontwikkeling: configureer eerst Keycloak, start daarna de backend
en frontend volgens hun handleidingen.

Voor gebruik op de Pi: stel eerst de namen en certificaten in en start de
HTTPS-proxy volgens de proxyhandleiding. Configureer daarna Keycloak en start
de backend en frontend volgens hun eigen handleidingen. Controleer de volledige
verbinding pas als alle services actief zijn.

## Applicaties starten met Podman Compose
Je kunt alle rootless containers (Keycloak, backend en frontend) tegelijk starten
met podman-compose in plaats van ze allemaal apart op te starten.
Maak het gedeelde netwerk eenmalig aan als dat nog niet bestaat, en voer daarna vanuit
de repositoryroot uit:

```sh
podman network create pi-app
podman-compose up -d --build
```

Start de proxy daarna apart volgens de
[proxyhandleiding](proxy/README.md#nginx-controleren-en-starten).

## Gedeeld Podman-netwerk

Maak op de Pi het netwerk voor frontend en backend eenmalig aan als het nog
niet bestaat. Doe dit als dezelfde gebruiker die deze
containers start, voordat je de containers aanmaakt:

```sh
podman network create pi-app
```
