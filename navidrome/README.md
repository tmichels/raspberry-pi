# Navidrome

Navidrome biedt de muziekcollectie op de Raspberry Pi aan via een webspeler.
Het draait als een zelfstandige stack met rootless Podman Compose, los van
de overige services in dit project.

## Configuratie

Op de Pi moeten Podman en `podman-compose` beschikbaar zijn. Zet Zet de vatiabelen met `NAVIDROME_BIND_IP` en `MUSIC_DIRECTORY` in `.env`.

De muziekmap is alleen-lezen gekoppeld. De database, instellingen en cache
worden opgeslagen in een persistent Podman-volume. De deelfunctie staat uit;
luisteren vereist een Navidrome-account.

## Starten

Voer op de Pi in deze map uit:

```sh
podman-compose up -d
```
