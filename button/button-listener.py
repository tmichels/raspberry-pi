import subprocess
from datetime import datetime

from evdev import InputDevice, categorize, ecodes

# See https://python-evdev.readthedocs.io/en/latest/tutorial.html#reading-events

# Button is technically a keyboard and can be read by its device ID
dev = InputDevice('/dev/input/by-id/usb-WebKey_WebKey-event-kbd')
# Each time the button is pressed, the following sequence is read (as programmed in button hardware)
button_defined_sequence = ['KEY_NUMLOCK', 'KEY_NUMLOCK',
                           'KEY_LEFTCTRL', 'KEY_LEFTCTRL', 'KEY_LEFTSHIFT', 'KEY_LEFTSHIFT',
                           'KEY_LEFTCTRL', 'KEY_LEFTCTRL', 'KEY_LEFTSHIFT', 'KEY_LEFTSHIFT',
                           'KEY_LEFTCTRL', 'KEY_LEFTCTRL', 'KEY_LEFTSHIFT', 'KEY_LEFTSHIFT',
                           'KEY_LEFTCTRL', 'KEY_LEFTCTRL', 'KEY_LEFTSHIFT', 'KEY_LEFTSHIFT',
                           'KEY_LEFTCTRL', 'KEY_LEFTCTRL', 'KEY_LEFTSHIFT', 'KEY_LEFTSHIFT',
                           'KEY_NUMLOCK', 'KEY_NUMLOCK']

keys = []
for event in dev.read_loop():
    if event.type == ecodes.EV_KEY:
        keys.append(categorize(event).keycode)
    if keys == button_defined_sequence:
        keys = []
        print(datetime.now().time(), 'Knop gedrukt')
        subprocess.Popen(["/usr/bin/sh", "/home/thomas/raspberry-pi/button/knop-actie.sh"])
