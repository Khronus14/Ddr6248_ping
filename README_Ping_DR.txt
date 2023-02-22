Place com\savarese\rocksaw\net\RawSocket.java in working directory.
Place rocksaw.dll file in working directory.

To compile, in working directory:

javac Ddr6248_ping.java


To execute, in working directory:

java Ddr6248_ping [IP address] [-c <int>] [-i <int>] [-s <int>] [-t <int>]


Ensure you're running program as an administrator and firewalls are turned off.

Syntax explanation.

<IP add>		: IP address to ping
-c <integer>	: stop after receiving <integer> response packets (default until interrupted)
-i <integer>	: wait <integer> seconds between sending each packet (default 1)
-s <integer>	: specify <integer> byte-sized packets to send (default 64 [includes header])
-t <integer>	: program will end in <integer> seconds, regardless of packets received