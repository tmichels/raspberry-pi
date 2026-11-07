# Afstandsbediening

De Argon ONE V3 infraroodontvanger is aangesloten op GPIO 23. De Linux-driver
(`gpio_ir_recv`) ontvangt het signaal. **LIRC** leest de signalen via
`/dev/lirc0`, herkent de knoppen aan de hand van een configuratiebestand en
stuurt de herkende knopnamen door naar programma's.

Met **`irexec`** kun je een actie aan zo'n knopnaam koppelen. In tegenstelling
tot de `triggerhappy`-methode is er geen keymap nodig die een scancode aan een
Linux-toets zoals `KEY_F13` koppelt. Een knopnaam zoals `KEY_F13` in de LIRC-
configuratie is alleen een label voor die IR-knop; er wordt geen toetsenbord-
gebeurtenis gegenereerd.

## IR-ontvanger

Zorg dat `/boot/firmware/config.txt` onder `[all]` deze regel bevat:

```ini
dtoverlay=gpio-ir,gpio_pin=23
```

Herstart de Raspberry Pi nadat je deze configuratie hebt gewijzigd.

## LIRC installeren en de ontvanger testen

Installeer LIRC:

```sh
sudo apt update
sudo apt install lirc
```

Controleer of de ontvanger beschikbaar is:

```sh
ls -l /dev/lirc0
```

Stop `lircd` en de bijbehorende socket tijdelijk zodat de testprogramma's de
ontvanger kunnen gebruiken. Zonder de socket te stoppen kan `lircd` opnieuw
worden gestart zodra een client verbinding maakt. Selecteer daarna de ruwe
IR-modus:

```sh
sudo systemctl stop lircd.service lircd.socket
sudo ir-keytable -s rc0 -p lirc
sudo mode2 --driver default --device /dev/lirc0
```

De expliciete driver `default` zorgt dat de test niet de eventueel ingestelde
driver `devinput` gebruikt. Deze test werkt dus ook voordat je
`/etc/lirc/lirc_options.conf` verderop hebt aangepast.

Controleer dat `Using device: /dev/lirc0` verschijnt. Druk op een knop van de
afstandsbediening. Je hoort regels met `pulse` en `space` te zien.
Stop `mode2` met `Ctrl+C`. Als `/dev/lirc0` niet bestaat of
`mode2` geen pulsen toont, controleer dan eerst de overlay en de aansluiting
van de ontvanger.

## Knoppen opnemen

Neem de knoppen op in een LIRC-configuratiebestand:

```sh
sudo irrecord --driver default --device /dev/lirc0 /tmp/argon.lircd.conf
```

Volg de aanwijzingen van `irrecord` en druk tijdens het opnemen op de knoppen
die je wilt gebruiken. Geef de testknop bijvoorbeeld de naam `KEY_F13`.
Installeer daarna het gegenereerde bestand:

```sh
sudo install -D -m 644 /tmp/argon.lircd.conf \
  /etc/lirc/lircd.conf.d/argon.lircd.conf
```

Controleer in dat bestand de naam in de regel `name`. Stel die naam in op
`argon`, of gebruik de bestaande naam verderop bij `remote`. Controleer ook
dat de knop de naam `KEY_F13` heeft; pas de `button`-regel verderop aan als je
bij het opnemen een andere naam hebt gekozen.

Open de configuratie met `sudoedit /etc/lirc/lirc_options.conf` en pas in de
bestaande sectie `[lircd]` de driver en het apparaat aan. Deze stap is nog
nodig voor `lircd`, ook als de ontvangsttest met `mode2` al werkte:

```ini
[lircd]
driver = default
device = /dev/lirc0
```

Start LIRC en controleer de ontvangen knopnamen:

```sh
sudo systemctl enable --now lircd.socket lircd.service
irw
```

Druk op de opgenomen knop. `irw` hoort onder andere de knopnaam en de remote-
naam te tonen, bijvoorbeeld `KEY_F13 argon`. Stop `irw` met `Ctrl+C`.

## Actie aan een knop koppelen

Maak `/etc/lirc/lircrc` aan met `sudoedit /etc/lirc/lircrc` en voeg de actie
toe:

```text
begin
    prog = irexec
    remote = argon
    button = KEY_F13
    config = /home/thomas/ir-test.sh
end
```

`remote` moet overeenkomen met de `name` in `argon.lircd.conf`; `button` moet
overeenkomen met de opgenomen knopnaam. Zorg dat het script uitvoerbaar is:

```sh
chmod +x /home/thomas/ir-test.sh
```

Test de koppeling door `irexec /etc/lirc/lircrc` uit te voeren. Druk op de
knop en controleer of het script start; stop `irexec` daarna met `Ctrl+C`.

Voor automatisch starten bij het opstarten kan de meegeleverde
`irexec.service` worden gebruikt. Laat die als gebruiker `thomas` draaien,
zodat het script niet als root wordt uitgevoerd:

```sh
sudo systemctl edit irexec.service
```

Voeg in de editor toe:

```ini
[Service]
User=thomas
```

Schakel de service in en start hem:

```sh
sudo systemctl enable --now irexec.service
```

De instelling `ir-keytable -s rc0 -p lirc` kan na een herstart opnieuw nodig
zijn. Controleer dan eerst met `mode2` of LIRC nog ruwe pulsen ontvangt.
