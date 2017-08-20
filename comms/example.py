
import radiocontroller
from namespace import NameSpace

## Radio Controller

# 1. Detect Antennas
rc = radiocontroller.RadioController("Gold", "/home/rawkintrevo/gits/cylon-blog/data/radio-config/devices.txt")

#1ed9 - Gold1
#1ee4 - Gold2
#2135 - Gold3

# 2. Pick One Antenna (doesn't need to be unique), Scan for SSIDs
rc.scanUntilKFound(k= 1, seconds= 40)


# 3. For each SSID, create a drone- assign to one antenna, and namespace

import os

os.environ["CYLON_HOME"] = "/home/rawkintrevo/gits/cylon-blog"

cylon_home = os.getenv("CYLON_HOME")
if cylon_home == None:
    print "Please set CYLON_HOME"

from time import sleep
max_supported = min(len(rc.known_interfaces), len(rc.availableDrones))

print "delete old name spaces"
for ssid in rc.managedDrones:
    ns = NameSpace(ssid.split()[-1])
    ns.safelyDelete()




for antenna, ssid in zip(rc.known_interfaces[:max_supported], rc.availableDrones[:max_supported]):
    name =ssid.split()[-1]
    print name
    print antenna.interface
    # Delete previous connection if exists
    if name in [con['name'] for con in rc.listConnections()]:
        print antenna.execute(['nmcli', 'con', 'delete', name])
    sleep(1)
    # Create Connection to Drone
    print antenna.execute(["nmcli", "con", "add",
                     "con-name",  name,
                     "ifname", antenna.interface,
                     "type", "wifi",
                     "autoconnect", "false",
                     "ssid", ssid,
                     "ip4", "192.168.100.101/24", "gw4", "192.168.100.1",
                     "wifi-sec.key-mgmt", "wpa-psk",
                     "wifi-sec.psk", "12345678"], in_name_space=False)
    # Connect Drone -- doesn't matter if it's connected or not, bc the virtual interface on the namespace is the one that needs to be up
    # antenna.execute(["nmcli", "con", "up", name, "ifname", antenna.interface], in_name_space=False)
    # Add Namespace
    ns = NameSpace(name)
    # ns.delete()
    print ns.safelyAdd()
    antenna.namespace = ns
    rc.managedDrones[ssid].assigned_iface = antenna
    # Turn off antenna in root name space
    print antenna.execute(['ip', "link", "set", "dev", antenna.interface, "down"], in_name_space=False, use_sudo=True)
    # add it to namespace
    print antenna.execute(["ip", "link", "set", "dev", antenna.interface, "netns", ns.name], use_sudo=True, in_name_space= False)
    # turn it on in namespace
    print antenna.execute(["ip", "link", "set", "dev", antenna.interface, "up"], in_name_space=True)
    print antenna.execute(["ip", "link", "set", "dev", "lo", "up"], in_name_space=True)
    # print antenna.execute(["nmcli", "con", "up", name], in_name_space=True)
    print antenna.execute(["wpa_supplicant", "-B", "-D", "nl80211,wext", "-i", antenna.interface,
                    "-c", "/home/rawkintrevo/gits/cylon-blog/%s.conf" % name], in_name_space=True)
    print antenna.execute(["iw", antenna.interface, "link"], in_name_space=True)

    print antenna.execute([
        "java", "-Dfile.encoding=UTF-8", "-jar" , cylon_home +
                        "/bin/drone-cam2kafka-1.0-SNAPSHOT-jar-with-dependencies.jar",
                        "-i", "rtsp://192.168.100.1:554/cam1/mpeg4",
                        "-k", "test",
                        "-t", "test"
        ], in_name_space= True)
# wlx18d6c711c228
print antenna.execute(['ifconfig', antenna.interface, "up"], in_name_space=True)

print antenna.execute(['ifdown', antenna.interface], in_name_space=True)
print antenna.execute(['ifup', "-v", antenna.interface], in_name_space=True)
# 3a. Add virtual interface to namespaces

# 4. Execute Kafka Jar on Namespace.

for ssid, drone in rc.managedDrones.iteritems():
    if drone.assigned_iface == None:
        continue
    if drone.isAvailable():
        print drone.callsign
        drone.assigned_iface.execute([
            "java -Dfile.encoding=UTF-8 -jar " +
            cylon_home + "/bin/drone-cam2kafka-1.0-SNAPSHOT-jar-with-dependencies.jar" +
            " -i rtsp://192.168.100.1:554/cam1/mpeg4 -k " + drone.callsign +
            " -t test"],
            # "java", "-Dfile.encoding=UTF-8", "-jar" , cylon_home +
            #                 "/bin/drone-cam2kafka-1.0-SNAPSHOT-jar-with-dependencies.jar",
            #                 "-i", "rtsp://192.168.100.1:554/cam1/mpeg4",
            #                 "--kafkaKey ", drone.callsign,
            #                 "-t", "test"],
                                     use_sudo=True)
        print "http://localhost:8090/cylon/cam/test/" + drone.callsign
## Results should be at test/Gold1]
# http://localhost:8090/cylon/cam/test/Gold1


# ip netns exec 2135 java -Dfile.encoding=UTF-8 -jar /home/rawkintrevo/gits/cylon-blog/bin/drone-cam2kafka-1.0-SNAPSHOT-jar-with-dependencies.jar -i rtsp://192.168.100.1:554/cam1/mpeg4 -k  Gold-3 -t test

