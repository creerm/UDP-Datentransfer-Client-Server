#!/bin/bash

chmod +x client-udp
chmod +x server-udp

javac -d ./bin/ src/UDPClient.java
javac -d ./bin/ src/UDPServer.java
