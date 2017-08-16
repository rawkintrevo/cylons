

import subprocess
from time import sleep

class Device:
    def __init__(self, ssid, callsign):
        self.ssid = ssid
        self.callsign = callsign
        self.assigned_iface = ""
        self.isAvailable = False


class RadioController:
    def __init__(self, squadronPrefix, deviceListPath = None, known_interfaces = []):
        self.deviceListPath = deviceListPath
        self.known_interfaces = known_interfaces  # List[Antenna]
        self.availableDevices = [] # List[ssid]
        self.loadDeviceList()  # Map[ssid -> Device]
        self.squadronPrefix = squadronPrefix


    ###################################################################################################################
    ## Interface Management
    def checkInterfaceStatuses(self):
        return {antenna.interface : antenna.isConnected()
                for antenna in self.known_interfaces }

    def getFirstFreeAntenna(self):
        return next(antenna for antenna in self.known_interfaces if antenna.isConnected() == False)

    def getUsbBusNetworkInterfaces(self):
        """
        Uses `lshw -c network` to get interface names of all USB based Wifi cards.
        :return: A List[String] of interface names.
        """
        results = subprocess.check_output(["lshw", "-c", "network"])
        raw_interface_output = results.split('*-network')
        interface_dict = {iface.split("\n")[0] : {line.split(":")[0].strip() : line.split(':')[1].strip() for line in iface.split('\n')[1:] if (len(line.split(":")) > 1)} for iface in raw_interface_output}
        interface_list = [v['logical name'] for k,v in interface_dict.iteritems() if ('bus info' in v.keys()) if v['bus info'].startswith("usb")]
        self.known_interfaces = [Antenna(iface) for iface in list(set(self.known_interfaces + interface_list))]

    ###################################################################################################################
    ## Connection Management
    def listConnections(self):
        results = subprocess.check_output(["nmcli", "con"]).split("\n")
        output = [
            {"name": r.split()[0],
             "uuid": r.split()[1],
             "type": r.split()[2],
             "device": r.split()[3]}
            for r in results[1:] if (len(r.split()) > 0)]  # First line is header
        return output

    def deleteConnection(self, uuid):
        subprocess.check_output(["nmcli", "con", "delete", "uuid", uuid])

    def deleteAllConnectionsStartingWith(self, startsWith):
        for conn in self.listConnections():
            if conn['name'].startswith(startsWith):
                self.deleteConnection(conn['uuid'])

    def addConnection(self, ssid, callsign):
        existing_connections = [conn['name'] for conn in self.listConnections()]
        for a in self.known_interfaces:
            # Don't create dupicate connections
            if "%sOn%s" % (callsign, a.interface) in existing_connections:
                continue
            a.addConnection(ssid, callsign)

    def addConnectionsForAllManagedDevices(self):
        for ssid in self.managedDevices.keys():
            self.addConnection(ssid)

    def connectDevice(self, ssid, callsign, antenna):
        connName = "%sOn%s" % (callsign, antenna.interface)
        antenna.disconnect()
        antenna.connect(connName)
        print "%s connected on %s" % (callsign, antenna.interface)
        self.managedDevices[ssid].assigned_interface = antenna.interface

    def connectAllAvailableDevices(self):
        for ssid, device in self.managedDevices.iteritems():
            if device.isAvailable:
                self.connectDevice(device.ssid, device.callsign, self.getFirstFreeAntenna())
    ###################################################################################################################
    ## Device Management

    def saveDeviceList(self):
        if self.deviceListPath is not None:
            with open(self.deviceListPath, 'w') as f:
                f.write("\n".join(["%s:%s" % (ssid, device.callsign) for ssid, device in self.managedDevices.iteritems()]))

    def loadDeviceList(self):
        if self.deviceListPath is None:
            self.managedDevices = {}
        else:
            with open(self.deviceListPath, 'r') as f:
                raw = f.read()
            self.managedDevices = {d.split(":")[0]: Device(d.split(":")[0], d.split(":")[1]) for d in raw.split("\n")}
        print self.managedDevices

    def scanForDevices(self, prefix= "Petrone"):
        a = self.getFirstFreeAntenna()
        self.availableDevices = [ssid for ssid in a.scan() if ssid.startswith(prefix)]
        print self.availableDevices
        for ssid in self.availableDevices:
            if ssid not in self.managedDevices.keys():
                print "Registering %s" % ssid
                self.registerNewDevice(ssid)
            if self.managedDevices[ssid].isAvailable == False:
                print "%s reporting in. (%s)" % (self.managedDevices[ssid].callsign, ssid)
                self.managedDevices[ssid].isAvailable = True

    def registerNewCallSign(self, ssid):
        assert isinstance(ssid, str)
        i = len(self.managedDevices) + 1
        callsign = "%s-%i" % (self.squadronPrefix, i)
        self.managedDevices[ssid] = Device(ssid, callsign)
        self.saveDeviceList()
        return callsign

    def registerNewDevice(self, ssid):
        assert isinstance(ssid, str)
        self.addConnection(ssid, self.registerNewCallSign(ssid))

    def scanUntilKFound(self, k, seconds=20, pause=5):
        devicesFound = 0
        for i in range(0, seconds):
            self.scanForDevices()
            for ssid in self.availableDevices:
                #print "%s reporting in." % self.managedDevices[ssid].callsign
                devicesFound += 1
                if devicesFound >= k:
                    return
            sleep(pause)




class Antenna:
    def __init__(self, interface):
        self.interface = interface

    def isConnected(self):
        if 'inet' in subprocess.check_output(["ifconfig", self.interface]):
            return True
        else:
            return False

    def connect(self, connection_name):
        subprocess.check_output(["nmcli", "connection", "up", "id", connection_name])

    def disconnect(self):
        subprocess.check_output(["nmcli", "device", "disconnect", self.interface])

    def scan(self):
        """
        :return: A list of the network names found. List[String]
        """
        try:
            subprocess.check_output(['nmcli', 'device', 'wifi', 'rescan'])
        except Exception as e:
            print "failed to rescan"
        return list(set([line.split("Infra")[0].strip() for line in subprocess.check_output(['nmcli', 'device', 'wifi', 'list']).split('\n') if "Infra" in line]))

    def addConnection(self, ssid, callsign):
        connName = "%sOn%s" % (callsign, self.interface)
        subprocess.check_output(["nmcli", "con", "add",
                                 "con-name", connName,
                                 "ifname", self.interface,
                                 "type", "wifi",
                                 "ssid", ssid,
                                 "ip4", "192.168.100.101/24", "gw4", "192.168.100.1"])
        subprocess.check_output(["nmcli", "con", "modify", connName, "wifi-sec.key-mgmt", "wpa-psk"])
        subprocess.check_output(["nmcli", "con", "modify", connName, "wifi-sec.psk", "12345678"])


