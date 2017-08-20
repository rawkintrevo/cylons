
from antenna import Antenna

class Drone:
    def __init__(self, ssid, callsign):
        self.ssid = ssid
        self.callsign = callsign
        self.assigned_iface = None # Antenna
        # self.isAvailable = False

    def isAvailable(self):
        return self.assigned_iface.scanForSSID(self.ssid)