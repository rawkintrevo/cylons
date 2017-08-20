

import subprocess
from time import sleep

## Our Stuff
from antenna import Antenna
from drone import Drone



class RadioController:
    def __init__(self, squadronPrefix, droneListPath = None, known_interfaces = []):
        self.droneListPath = droneListPath
        self.known_interfaces = [] # sets known_interfaces: List[Antenna]
        self.refreshNetworkInterfaces()
        self.availableDrones = [] # List[ssid]
        self.loadDroneList()  # Map[ssid -> Drone]
        self.squadronPrefix = squadronPrefix


    ###################################################################################################################
    ## Interface Management
    def checkInterfaceStatuses(self):
        self.refreshNetworkInterfaces()
        return {antenna.interface : antenna.isConnected()
                for antenna in self.known_interfaces }

    def getAnyAntenna(self):
        self.refreshNetworkInterfaces()
        if len(self.known_interfaces) > 0:
            return self.known_interfaces[0]
        print "no antennas available"

    def getFirstFreeAntenna(self): # : Antenna
        for antenna in self.known_interfaces:
            if antenna.isConnected == False:
                return antenna
        print "no unconnected antennas available"

        # return self.known_interfaces[0]
        # return next([antenna for antenna in self.known_interfaces if antenna.interface in
        #              subprocess.check_output(["ifconfig"])].__iter__())

    def refreshNetworkInterfaces(self):
        """
        Uses `lshw -c network` to get interface names of all USB based Wifi cards.
        :return: A List[String] of interface names.
        """
        results = subprocess.check_output(["lshw", "-c", "network"])
        raw_interface_output = results.split('*-network')
        interface_dict = {iface.split("\n")[0] : {line.split(":")[0].strip() : line.split(':')[1].strip() for line in iface.split('\n')[1:] if (len(line.split(":")) > 1)} for iface in raw_interface_output}
        interface_list = [a.interface for a in self.known_interfaces] + [v['logical name'] for k,v in interface_dict.iteritems() if ('bus info' in v.keys()) if v['bus info'].startswith("usb")]
        self.known_interfaces = [Antenna(iface) for iface in list(set(interface_list))]

    def assignInterfacesToNamespaces(self):
        for a in self.known_interfaces:
            a.setupNameSpace()

    def disconnectAllAntennas(self):
        for a in self.known_interfaces:
            a.disconnect()

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

    def addConnectionsForAllManagedDrones(self):
        for ssid, dev in self.managedDrones.iteritems():
            self.addConnection(ssid, dev.callsign)

    def connectDrone(self, ssid, callsign, antenna):
        connName = "%s-On-%s" % (callsign, antenna.interface)
        antenna.disconnect()
        existing_connections = [conn['name'] for conn in self.listConnections()]
        if "%s-On-%s" % (callsign, antenna.interface) not in existing_connections:
            print "connecting"
            antenna.addConnection(ssid, callsign)
        antenna.connect(connName)
        print "%s connected on %s" % (callsign, antenna.interface)
        self.managedDrones[ssid.replace("'","")].assigned_iface = antenna

    def connectAllAvailableDrones(self):
        for ssid, drone in self.managedDrones.iteritems():
            if drone.isAvailable:
                self.connectDrone(drone.ssid, drone.callsign, self.getFirstFreeAntenna())

    ###################################################################################################################
    ## Drone Management

    def saveDroneList(self):
        if self.droneListPath is not None:
            with open(self.droneListPath, 'w') as f:
                f.write("\n".join(["%s:%s" % (ssid, drone.callsign) for ssid, drone in self.managedDrones.iteritems()]))

    def loadDroneList(self):
        if self.droneListPath is None:
            self.managedDrones = {}
        else:
            with open(self.droneListPath, 'r') as f:
                raw = f.read()
            self.managedDrones = {d.split(":")[0]: Drone(d.split(":")[0], d.split(":")[1]) for d in raw.split("\n")}
        print self.managedDrones

    def scanForDrones(self, prefix="Petrone"):
        a = self.getAnyAntenna()
        self.availableDrones = [ssid for ssid in a.scan() if ssid.startswith(prefix)]
        print self.availableDrones
        for ssid in self.availableDrones:
            if ssid not in self.managedDrones.keys():
                print "Registering %s" % ssid
                self.registerNewDrone(ssid)
            if self.managedDrones[ssid].assigned_iface == None:
                self.managedDrones[ssid].assigned_iface = a
                print "%s reporting in. (%s)" % (self.managedDrones[ssid].callsign, ssid)
                # self.managedDrones[ssid].isAvailable = True

    def registerNewCallSign(self, ssid):
        assert isinstance(ssid, str)
        i = len(self.managedDrones) + 1
        callsign = "%s-%i" % (self.squadronPrefix, i)
        self.managedDrones[ssid] = Drone(ssid, callsign)
        self.saveDroneList()
        return callsign

    def registerNewDrone(self, ssid):
        assert isinstance(ssid, str)
        self.addConnection(ssid, self.registerNewCallSign(ssid))

    def scanUntilKFound(self, k, seconds=20, pause=5):
        dronesFound = 0
        for i in range(0, seconds):
            self.scanForDrones()
            for ssid in self.availableDrones:
                #print "%s reporting in." % self.managedDrones[ssid].callsign
                dronesFound += 1
                if dronesFound >= k:
                    return
            sleep(pause)



