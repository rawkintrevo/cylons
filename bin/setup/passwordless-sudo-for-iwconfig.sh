#!/usr/bin/env bash

echo "This is potentially going to compromise security.  Do this on safe systems only (e.g. containers or personal computers)"
sudo touch /etc/sudoers.d/iwconfig
echo "$USER ALL=(root) NOPASSWD: /sbin/iwconfig" | sudo tee --append /etc/sudoers.d/iwconfig
ls -l /etc/sudoers.d/iwconfig
echo "-r--r----- 1 root root 48 Aug 15 11:30 /etc/sudoers.d/iwconfig"
echo "the proceeding two lines should be exactly the same (except for timestamp)"



