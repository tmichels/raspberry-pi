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
Je kunt alle containers tegelijk starten
met podman-compose in plaats van ze allemaal apart op te starten.
Compose maakt het gedeelde netwerk `pi-app` aan als het nog niet bestaat.
Richt de proxy eerst eenmalig in volgens de
[proxyhandleiding](proxy/README.md#rootless-inrichten).
Voer daarna vanuit de repositoryroot uit:

```sh
podman-compose up -d --build
```

## Gedeeld Podman-netwerk

Bij gebruik van Compose wordt `pi-app` automatisch aangemaakt en beheerd.
Bij `podman-compose down` wordt het netwerk ook verwijderd als er geen
containers meer aan gekoppeld zijn.

Als je de losse `podman run`-commando's uit de handleidingen gebruikt, maak
dan op de Pi het netwerk eenmalig aan als het nog niet bestaat. Doe dit als
dezelfde gebruiker die deze containers start, voordat je de containers aanmaakt:

```sh
podman network create pi-app
```
