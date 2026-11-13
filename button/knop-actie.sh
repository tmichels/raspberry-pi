printf "%s Start download script\n" "$(date '+%F %T')"
python3 /home/thomas/TomTomWatchCli/bluetooth/tomtom-bluetooth.py download
printf "%s Einde download script met status %d\n" "$(date '+%F %T')" $?
