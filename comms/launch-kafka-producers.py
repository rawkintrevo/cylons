
import os
import subprocess

# parse /etc/network/interfaces for wpa_supplicant paths.

os.environ["CYLON_HOME"] = "/home/rawkintrevo/gits/cylon-blog"

cylon_home = os.getenv("CYLON_HOME")
if cylon_home == None:
    print "Please set CYLON_HOME"

with open("/etc/network/interfaces", 'r') as f:
    network_interfaces = f.readlines()

last_line = ""
counter = 0
if_cf_ns_triple = []
for line in network_interfaces:
    if cylon_home in line:
        if_cf_ns_triple.append((
            last_line.split()[1],
            line.split()[1],
            "cylon_ns_%i" % counter
        ))
        counter += 1
    last_line = line

for iface, conf, namespace in if_cf_ns_triple:
    # sudo ip netns add ns_1
    print subprocess.check_output(["sudo", "ip", "netns", "add", namespace])
    # sudo ip link set dev wlan2 down
    print subprocess.check_output(['sudo', 'ip', 'link', 'set', 'dev', iface, 'down'])
    # sudo ip link set dev wlan2 netns ns_1
    print subprocess.check_output(['sudo', 'ip', 'link', 'set', 'dev', iface, 'netns', namespace])
    ## sudo ip netns exec ns_1 iwconfig
    # sudo ip netns exec ns_1 ip link set dev wlan2 up
    print subprocess.check_output(['sudo', 'ip', "netns", "exec", namespace,
                             "ip", 'link', 'set', 'dev', iface, "up"])

    ## sudo ip netns exec ns_1 iwconfig
    # sudo ip netns exec ns_1 wpa_supplicant -B -D nl80211,wext -i wlan2 -c ~/wpa_supplicant.conf
    print subprocess.check_output(['sudo', 'ip', "netns", "exec", namespace,
                            "wpa_supplicant", "-B", "-D", "nl80211,wext", "-i", iface,
                            "-c", conf])
    ## sudo ip netns exec ns_1 iwconfig
    # sudo ip netns exec ns_1 sudo dhclient wlan2
    print subprocess.check_output(['sudo', 'ip', "netns", "exec", namespace,
                            "dhclient", iface])
    # sudo ip netns exec ping 192.168.100.1

for iface, conf, namespace in if_cf_ns_triple:
    subprocess.check_output(['sudo', 'ip', "netns", "exec", namespace,
                            "iw", iface, "link"])
    #iw wlan1 link