#!/usr/bin/env bash


echo "This is potentially going to compromise security.  Do this on safe systems only (e.g. containers or personal computers)"
sudo touch /etc/sudoers.d/ip
echo "$USER ALL=(root) NOPASSWD: /sbin/ip" | sudo tee --append /etc/sudoers.d/ip
ls -l /etc/sudoers.d/ip
echo "-r--r----- 1 root root 42 MMM DD HH:mm /etc/sudoers.d/ip"
echo "the proceeding two lines should be exactly the same (except for timestamp)"



