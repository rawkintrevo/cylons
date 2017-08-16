

from pprint import pprint

import radio

## Radio Controller

rc = radio.RadioController("Gold", "/home/rawkintrevo/gits/cylon-blog/data/radio-config/devices.txt")

# we save/load a device list so that call-signs remain consistent. otherwise they get
# assigned in the order of discover, which is OK- excpet I painted my drones with their
# numbers.
#rc.loadDeviceList("/home/rawkintrevo/gits/cylon-blog/data/radio-config/devices.txt")

# detects all usb-wireless cards which we'll be using for antennas
rc.getUsbBusNetworkInterfaces()

# we can clear all connections.
rc.deleteAllConnectionsStartingWith("Gold")


# pprint(rc.listConnections())
# pprint(rc.known_interfaces[0].scan())


# k -> how many new devices we're looking for, when we've found them all, we'll continue.
rc.scanUntilKFound(k= 3, seconds= 40)

#1ed9 - Gold1
#1ee4 - Gold2
#2135 - Gold3

rc.connectAllAvailableDevices()

# Error: Device 'wlx18d6c711c228' (/org/freedesktop/NetworkManager/Devices/5) disconnecting failed: This device is not active
# Error: not all devices disconnected.
# todo - replace Device.isActive with nmcli probe