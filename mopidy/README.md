# Mopidy

Mopidy speelt muziek van `/home/thomas/muziek` af via de HiFiBerry en wordt
bediend met Mopidy-Mobile. Deze installatie draait als systemd-service op
Ubuntu Server 24.04 of nieuwer.

## Installeren

Controleer de Ubuntu-versie en voeg de Mopidy-pakketsleutel toe:

```sh
cat /etc/os-release
sudo apt update
sudo apt install wget ca-certificates
sudo mkdir -p /etc/apt/keyrings
sudo wget -O /etc/apt/keyrings/mopidy-archive-keyring.gpg \
  https://apt.mopidy.com/mopidy-archive-keyring.gpg
```

Kies één pakketbron:

```sh
# Ubuntu 25.10 of nieuwer
sudo wget -O /etc/apt/sources.list.d/mopidy.sources \
  https://apt.mopidy.com/trixie.sources
```

Installeer Mopidy en de extensies:

```sh
sudo apt update
sudo apt install mopidy mopidy-local python3-pip acl alsa-utils
sudo /usr/bin/python3 -m pip install --break-system-packages mopidy-mobile
```

## Configureren

Open de configuratie:

```sh
sudo nano /etc/mopidy/mopidy.conf
```

Voeg deze instellingen toe of pas de bestaande secties aan:

```ini
[local]
media_dir = /home/thomas/muziek

[http]
hostname = 0.0.0.0

[audio]
output = alsasink device=plughw:sndrpihifiberry,0
```

## Toegang tot muziek

```sh
sudo setfacl -m u:mopidy:--x /home/thomas
sudo -u mopidy ls /home/thomas/muziek
```

Als bestanden of submappen niet leesbaar zijn:

```sh
sudo setfacl -R -m u:mopidy:rX /home/thomas/muziek
sudo find /home/thomas/muziek -type d -exec setfacl -d -m u:mopidy:rX {} +
```

## Starten en muziek scannen

```sh
sudo mopidyctl local scan
sudo systemctl enable mopidy
sudo systemctl restart mopidy
```

Herhaal `sudo mopidyctl local scan` na het toevoegen of verwijderen van muziek.

## Bedienen

Open `http://192.168.178.96:6680/mobile/` op het lokale netwerk. Is het
IP-adres gewijzigd, bekijk het dan met `hostname -I`.

Kies **Library → Local media → Tracks** en tik op een nummer.

## Controleren

```sh
sudo mopidyctl deps | grep -i -A3 mobile
sudo -u mopidy aplay -l
sudo -u mopidy amixer -c sndrpihifiberry
sudo journalctl -u mopidy -n 50 --no-pager
```
