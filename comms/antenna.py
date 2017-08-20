
from namespace import NameSpace
import subprocess

class Antenna:
    def __init__(self, interface):
        self.interface = interface
        self.namespace = None
        #self.setupNameSpace()

    def isConnected(self):
        """

        :return: Bool - Is the device connected
        """
        return "disconnected" not in self.execute("nmcli device show " + self.interface, use_sudo=True, in_name_space=False)

    def isCurrentlyConnectedToCallSign(self):
        """

        :return: Str: Call Sign of current connection
        """
        if self.isConnected():
            self.isCurrentlyConnectedToConnection().split("-On-")[0]

    def isCurrentlyConnectedToConnection(self):
        """

        :return: str: connection name
        """
        if self.isConnected():
            return [line for line in self.execute("nmcli device show " + self.interface, use_sudo=True).split("\n") if "GENERAL.CONNECTION:" in line][0].split(":")[1].strip()


    def connect(self, connection_name):
        self.execute("nmcli con up " + connection_name)

    def disconnect(self):
        if self.isConnected():
            self.execute("nmcli connection down " + self.isCurrentlyConnectedToConnection())

    def scan(self):
        """
        :return: A list of the network names found. List[String]
        """
        try:
            self.execute('nmcli device wifi rescan', use_sudo=True)
        except Exception as e:
            print "failed to rescan"
        return list(set([line.split("Infra")[0].strip() for line in self.execute('nmcli device wifi list', use_sudo=False).split('\n') if "Infra" in line]))

    def scanForSSID(self, ssid):
        return ssid in self.scan()

    def addConnection(self, ssid, callsign):
        connName = "%s-On-%s" % (callsign, self.interface)
        print "Creating: %s" % connName
        self.execute(["nmcli", "con", "add",
                                 "con-name",  connName,
                                 "ifname", self.interface,
                                 "type", "wifi",
                                 "ssid", ssid ,
                                 "ip4", "192.168.100.101/24", "gw4", "192.168.100.1"], in_name_space=False)
        self.execute(["nmcli", "con", "modify", connName, "wifi-sec.key-mgmt", "wpa-psk"], in_name_space=False)
        self.execute(["nmcli", "con", "modify", connName, "wifi-sec.psk", "12345678"], in_name_space=False)

    def setupNameSpace(self):
        self.namespace = NameSpace(self.interface)
        self.namespace.safelyAdd()
        self.execute('sudo ip link set dev ' + self.interface + ' netns ' + self.namespace.name, in_name_space= False)
        self.execute("sudo ip link set lo up", in_name_space=False, use_sudo=True )

    def execute(self, command, in_name_space= True, use_sudo = False):
        if self.namespace == None:
            in_name_space = False
        if in_name_space:
            return self.namespace.execute(command, use_sudo= True)
        else:
            if isinstance(command, str):
                if use_sudo:
                    command = "sudo " + command
                return subprocess.check_output(command.split())
            if isinstance(command, list):
                if use_sudo:
                    command = ["sudo"] + command
                return subprocess.check_output(command)