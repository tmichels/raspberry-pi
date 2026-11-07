# Raspberry Pi bediening

Dit project bestaat uit een React-frontend, een Spring Boot-backend,
Keycloak-authenticatie en een Nginx HTTPS-proxy.

## Handleidingen

- [Frontend](frontend/README.md) — lokaal ontwikkelen, bouwen, tests en de
  frontendservice in Compose.
- [Backend](backend/README.md) — API, tests en de
  backendservice in Compose.
- [Keycloak](keycloak/README.md) — de realm/client
  configureren.
- [HTTPS-proxy](proxy/README.md) — certificaten en Nginx op de Pi.

## Waar te beginnen

Voor gebruik op de Pi: voer eerst de eenmalige proxy-inrichting uit volgens de
[proxyhandleiding](proxy/README.md#rootless-inrichten) en configureer de
Keycloak-realm en client volgens de [Keycloak-handleiding](keycloak/README.md).
Start daarna de services met Compose.

## Applicaties starten met Podman Compose

Compose bouwt en start de frontend, backend, Keycloak en proxy als rootless
containers. Het maakt ook het gedeelde netwerk `pi-app` aan. Stel voor een
eerste start op een lege Keycloak-datavolume de variabelen
`KC_BOOTSTRAP_ADMIN_USERNAME` en `KC_BOOTSTRAP_ADMIN_PASSWORD` in.
Voer vanuit de repositoryroot uit:

```sh
podman-compose up -d --build
```

Bijwerken kan met hetzelfde commando. Stop de services met
`podman-compose down`; persistente Keycloak- en backendgegevens blijven in de
Compose-volumes staan.
