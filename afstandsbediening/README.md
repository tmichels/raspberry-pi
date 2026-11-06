# Afstandsbediening

De Argon ONE V3 infrarood ontvanger zet het infraroodsignaal van de afstandsbediening om in
elektrische pulsen. De **Linux IR-driver** (`gpio_ir_recv`) leest die pulsen
via GPIO 23 en geeft ze door aan de IR-onderdelen van de Linux-kernel.
Die herkennen het protocol en de knopcode (scancode).

Met **`ir-keytable`** vertellen we Linux welke scancode bij welke toets hoort. We
kunnen er ook mee controleren of een knop wordt ontvangen. Het programma
hoeft niet open te blijven: Linux blijft daarna zelf naar de ontvanger
luisteren.

**`triggerhappy`** wacht op de toets `KEY_F13` en start dan het testscript.
Kort gezegd: de driver ontvangt het signaal, `ir-keytable` stelt de
knopkoppeling in en `triggerhappy` voert de actie uit.

## IR-ontvanger

In `/boot/firmware/config.txt` voeg je toe dat GPIO 23 een IR ontvanger is. Zet onder `[all]`:

```ini
dtoverlay=gpio-ir,gpio_pin=23
```

## Knop koppelen

Met de volgende opdracht worden de scancodes van de afstandsbediening getoond als je een knop indrukt:

```bash
thomas@raspberrypi:~$ ir-keytable -s rc0 -p all -t
3155.326011: lirc protocol(sharp): scancode = 0xcd1
3155.326019: event type EV_MSC(0x04): scancode = 0xcd1
3155.326019: event type EV_SYN(0x00).
```

De knop gaf protocol `sharp` en scancode `0xcd1`. De koppeling kan worden vastgelegd
in `/etc/rc_keymaps/argon.toml`:

```toml
[[protocols]]
name = "remote-test"
protocol = "sharp"

[protocols.scancodes]
"0x0cd1" = "KEY_F13"
```

De keymap wordt vervolgens geladen met:

```sh
sudo ir-keytable -s rc0 -w /etc/rc_keymaps/argon.toml
```

Daarna geeft `ir-keytable -s rc0 -t` `KEY_F13` als output.
```
thomas@raspberrypi:~$ ir-keytable -s rc0 -t
4241.997010: lirc protocol(sharp): scancode = 0xcd1
4241.997020: event type EV_MSC(0x04): scancode = 0xcd1
4241.997020: event type EV_KEY(0x01) key_down: KEY_F13(0x00b7)
4241.997020: event type EV_SYN(0x00).
4242.183010: event type EV_KEY(0x01) key_up: KEY_F13(0x00b7)
4242.183010: event type EV_SYN(0x00).

```

**De geladen koppeling is tijdelijk:** het keymap-bestand blijft bewaard,
maar de koppeling moet na een herstart opnieuw worden geladen.

## Triggerhappy

De service draait standaard als gebruiker `nobody`, maar deze heeft geen toegang
tot /home/thomas. Met `sudo systemctl edit triggerhappy.service` is daarom
een override ingesteld zodat de dienst scripts als `thomas` uitvoert:

```ini
[Service]
ExecStart=
ExecStart=/usr/sbin/thd --triggers /etc/triggerhappy/triggers.d/ --socket /run/thd.socket --user thomas --deviceglob /dev/input/event*
```

Voeg in `/etc/triggerhappy/triggers.d/ir-test.conf` de koppeling tussen de toets en het script toe:

```text
KEY_F13 1 /home/thomas/ir-test.sh
```

Herstart daarna de triggerhappy service: `sudo systemctl restart triggerhappy.service`.
