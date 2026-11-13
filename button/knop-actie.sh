printf "%s Start download script\n" "$(date '+%F %T')"  >> /home/thomas/knop.log
python3 /home/thomas/TomTomWatchCli/bluetooth/tomtom-bluetooth.py download >> /home/thomas/knop.log 2>&1
printf "%s Einde download script met status %d\n" "$(date '+%F %T')" $? >> /home/thomas/knop.log
