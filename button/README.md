# Button listener-service

Kopieer op de Raspberry Pi het servicebestand naar systemd:

```bash
sudo cp /home/thomas/raspberry-pi/button/button-listener.service /etc/systemd/system/button-listener.service
```

Laad de configuratie opnieuw en start de service meteen. `enable` zorgt
ervoor dat de service ook bij volgende starts van de Pi wordt uitgevoerd:

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now button-listener.service
```

Controleer de status of bekijk de log:

```bash
sudo systemctl status button-listener.service
sudo journalctl -u button-listener.service -f
```

De service draait als gebruiker `thomas` en krijgt via `SupplementaryGroups=input`
toegang tot inputapparaten. 
