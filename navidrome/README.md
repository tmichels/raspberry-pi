# Navidrome

Navidrome biedt de muziekcollectie op de Raspberry Pi aan via een webspeler.
Het draait als een zelfstandige stack met rootless Podman Compose, los van
de overige services in dit project.

## Configuratie

Op de Pi moeten Podman en `podman-compose` beschikbaar zijn. Stel in `.env`
`NAVIDROME_BIND_IP=127.0.0.1` in en zet `MUSIC_DIRECTORY` op het absolute pad
naar je muziekmap. De HTTPS-proxy draait op dezelfde Pi en bereikt Navidrome
via de loopbackpoort; rechtstreekse toegang vanaf het LAN is niet nodig.

Navidrome gebruikt `ND_BASEURL=/muziek` en is via de
[HTTPS-proxy](../proxy/README.md) bereikbaar op
`https://thomasmichels.nl/muziek/`. De proxy behoudt het pad `/muziek/` bij het
doorsturen naar Navidrome. Het TLS-certificaat moet `thomasmichels.nl` dekken.

De muziekmap is alleen-lezen gekoppeld. De database, instellingen en cache
worden opgeslagen in een persistent Podman-volume. De deelfunctie staat uit;
luisteren vereist een Navidrome-account.

## Starten

Voer op de Pi in deze map uit:

```sh
podman-compose up -d
```

Gebruik dit commando ook na een wijziging aan de Compose-instellingen:
Compose maakt de container waar nodig opnieuw aan. Alleen een herstart
neemt gewijzigde omgevingsvariabelen niet over.

Controleer op de Pi of Navidrome bereikbaar is:

```sh
curl -I http://127.0.0.1:4533/muziek/
```

Herlaad vervolgens de proxy volgens de
[proxyhandleiding](../proxy/README.md#nginx-controleren-en-starten).
