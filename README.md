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
- [Mopidy](mopidy/README.md) — lokale muziek afspelen via de HiFiBerry.

## Waar te beginnen

Voor gebruik op de Pi: stel bij een Raspberry Pi 5 met Argon ONE V3 eerst de
[voeding](#voeding-voor-de-raspberry-pi-5-met-argon-one-v3) in. Voer de
eenmalige proxy-inrichting uit volgens de
[proxyhandleiding](proxy/README.md#rootless-inrichten) en configureer de
Keycloak-realm en client volgens de [Keycloak-handleiding](keycloak/README.md).
Start daarna de services met Compose.

## Voeding voor de Raspberry Pi 5 met Argon ONE V3

Zet beide instellingen om deze melding te voorkomen:

```text
This power supply is not capable of supplying 5A; power to peripherals
will be restricted
```

Zet onder `[all]` in `/boot/firmware/config.txt`:

```ini
usb_max_current_enable=1
```

Voeg `PSU_MAX_CURRENT=5000` toe aan de EEPROM-configuratie met:

```sh
sudo EDITOR=nano rpi-eeprom-config --edit
```

Herstart daarna de Pi. `PSU_MAX_CURRENT=5000` laat de Pi 5 A aannemen; gebruik
dit alleen met een voedingspad dat daadwerkelijk 5 A kan leveren.
Je kunt dit controleren met dit commando (die moet 5000 teruggeven):
```sh
od -An -t u4 --endian=big /proc/device-tree/chosen/power/max_current
```

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
