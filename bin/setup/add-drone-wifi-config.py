
import os
import subprocess
from time import sleep

def getListOfUSBBasedInterfaces():
    """
    It is highly reccomended you get some cheap USB Wificards and not monkey around with your primary Wifi.
    :return: List[String] - List of names of Interfaces on USB-Bus
    """
    results = subprocess.check_output(["lshw", "-c", "network"])
    raw_interface_output = results.split('*-network')
    interface_dict = {iface.split("\n")[0] : {line.split(":")[0].strip() : line.split(':')[1].strip() for line in iface.split('\n')[1:] if (len(line.split(":")) > 1)} for iface in raw_interface_output}
    interface_list = [v['logical name'] for k,v in interface_dict.iteritems() if ('bus info' in v.keys()) if v['bus info'].startswith("usb")]
    return interface_list



def getDrones(iface_list):
    """
    :param iface_list: List[String] - Interface Names
    :return: List[String] - Drone SSID Names
    """
    n_drones = raw_input("Turn on all drones.  After they are all on- type the number of drones you have and press [Enter]")
    scan_for= "Petrone" # "ESSID" for all networks
    sleep_between_scans = 5
    max_seconds = 30
    seconds_elapsed = 0
    while True:
        found_ssids = list(set([line.split(":")[1] for line in subprocess.check_output(['iwlist', 'scan']).split('\n') if scan_for in line]))
        print "%i Drones found: %s" % (len(found_ssids), ",".join(found_ssids))
        if (len(found_ssids) >= int(n_drones)):
            break
        if (seconds_elapsed > max_seconds):
            print "Warning, only found %i drones" % len(found_ssids)
            break
        seconds_elapsed += sleep_between_scans
        sleep(sleep_between_scans)
    return found_ssids



def createEtcNetworkInterfacesEntry(iface_list, drone_ssids):
    """
    Creates an entry which the user must append to /etc/network/interfaces, note- will only do as many interfaces or drones
    which are passed to it- which ever is less.
    :param iface_list: List[String] Interfaces to be considered
    :param drone_ssids: List[String] SSID of drones to be considered
    :return: String to be placed in /etc/network/interfaces (this is also printed)
    """
    cylon_home = os.getenv("CYLON_HOME")
    if cylon_home == None:
        print "Please set CYLON_HOME"
        return
    max_supported = min(len(iface_list), len(drone_ssids))
    output_str = ""
    for iface, ssid in zip(iface_list[:max_supported], drone_ssids[:max_supported]):
        output_str += """
auto %s
iface %s inet manual
wpa-roam %s/data/radio-config/wpa_supplicant-%s.conf
iface default inet dhcp
""" % (iface, iface, cylon_home, ssid.split()[-1].replace('"',""))
    print "Please add the following to /etc/network/interfaces:\n\n" + output_str
    return output_str


def createWpaSupplicants(iface_list, drone_ssids):
    cylon_home = os.getenv("CYLON_HOME")
    if cylon_home == None:
        print "Please set CYLON_HOME"
        return
    max_supported = min(len(iface_list), len(drone_ssids))
    for iface, ssid in zip(iface_list[:max_supported], drone_ssids[:max_supported]):
        with open( "%s/data/radio-config/wpa_supplicant-%s.conf" % (cylon_home, ssid.split()[-1].replace('"',"")), 'w') as f:
            f.write(subprocess.check_output(['wpa_passphrase', ssid.replace('"', ""), "12345678"]))


iface_list = getListOfUSBBasedInterfaces()
print "%i interfaces found" % len(iface_list)
drone_ssids = getDrones(iface_list)
createEtcNetworkInterfacesEntry(iface_list, drone_ssids)
createWpaSupplicants(iface_list, drone_ssids)
print "\n\n\n\n All done- you'll probably need to reboot for changes to take effect."

