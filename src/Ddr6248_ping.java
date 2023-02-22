/**
 * Program ping a user-provided IP address to determine network latency.
 * Project 2 for CSCI 651
 *
 * @author David D. Robinson, ddr6248@rit.edu
 */

import com.savarese.rocksaw.net.RawSocket;
import java.io.IOException;
import java.net.*;
import java.util.InputMismatchException;

public class Ddr6248_ping {
    public static InetAddress pingAddress;
    public static int count = -1;
    public static int wait = 1;
    public static int pktSize = 64;
    public static long timeout = -1;
    public static String startStr = "\nPinging %s [%s] with %d bytes of data:\n";
    public static String startStrAlt = "\nPinging %s with %d bytes of data:\n";
    public static String replyStr = "Reply from %d.%d.%d.%d: bytes=%d time=%dms TTL=%d\n";
    public static String pingStats = """
            Ping statistics for %s:
                Packets: Sent = %d, Received = %d, Lost = %d (%.1f%% loss)
            """;
    public static String pingTimes = """
            Approximate round trip times in milli-seconds:
                Minimum = %dms, Maximum = %dms, Average = %dms
            """;
    public static int totalPktsReceived = 0;
    public static int totalPktsLost = 0;
    public static int totalPktsSent = 0;
    public static long minTime = 2147483647;
    public static long maxTime = 0;
    public static long totalTime = 0;
    public static boolean needToPrint = true;

    public static void main(String[] args) {
        Ddr6248_ping ping = new Ddr6248_ping();
        ping.parseCLA(args);
        ping.runPing();
    }

    /**
     * Handles command line arguments.
     * @param args command line arguments
     */
    private void parseCLA(String[] args) {
        if (args.length == 0) {
            String err = "Address to ping not provided.";
            usageAndExit(err, true);
        }
        try {
            // set target address
            pingAddress = InetAddress.getByName(args[0]);

            // handle any remaining CLAs
            int index = 1;
            while (index < args.length) {
                switch (args[index]) {
                    case "-c" -> count = Integer.parseInt(args[index + 1]);
                    case "-i" -> wait = Integer.parseInt(args[index + 1]);
                    case "-s" -> pktSize = checkSize(Integer.parseInt(args[index + 1]));
                    case "-t" -> timeout = Integer.parseInt(args[index + 1]);
                    default -> {
                        String err = """
                                Argument not recognized.
                                Refer to documentation for proper usage.
                                """;
                        usageAndExit(err, true);
                    }
                }
                index += 2;
            }
        } catch (InputMismatchException | NumberFormatException exception) {
            String err = "Input type mismatch; verify correct input for each flag.";
            usageAndExit(err, true);
        } catch (UnknownHostException msg) {
            String err = "Host could not be determined. Verify address/name.";
            usageAndExit(err, true);
        }
    }

    /**
     * Function to handle errors.
     * @param message general error message
     * @param userError true iff invalid user input caused error
     */
    private static void usageAndExit(String message, boolean userError) {
        System.err.println(message);
        System.err.println("java Ddr6248_ping [IP address] [-c <int>] [-i <int>]" +
                " [-s <int>] [-t <int>]");
        System.exit(userError ? 1 : 0);
    }

    /**
     * Utility function to validate packet size input.
     * @param size user provided packet size
     * @return final packet size
     */
    private int checkSize(int size) {
        if (size < 8) {
            System.out.println("Packet size must be => 8. Packet size set to 8.");
            size = 8;
        }
        return size;
    }

    /**
     * Starts program.
     */
    private void runPing() {
        // print starting line based on form of user provided address
        if (pingAddress.getHostName().startsWith("dns")) {
            System.out.printf(startStrAlt, pingAddress.getHostAddress(), pktSize);
        } else {
            System.out.printf(startStr, pingAddress.getHostName() ,pingAddress.getHostAddress(), pktSize);
        }

        // shutdown hook to print results when program is interrupted from keyboard
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (needToPrint) {
                long avgTime1 = 0;
                if (totalPktsReceived > 0) avgTime1 = totalTime / totalPktsReceived;
                if (minTime == 2147483647) minTime = 0;
                printResults(avgTime1);
            }
        }));

        // variable to calculate total runtime for -t flag
        long prgTimer = System.currentTimeMillis();
        try {
            while (true) {
                // check before and after sending a ping for -t flag timeout
                if (timeout > -1 &&
                        (System.currentTimeMillis() - prgTimer) / 1000 > timeout) break;

                _runPing();
                totalPktsSent++;
                if (count != -1 && totalPktsSent >= count) {
                    break;
                }

                if (timeout > -1 &&
                        (System.currentTimeMillis() - prgTimer) / 1000 > timeout) break;

                Thread.sleep((long) wait * 1000);
            }
        } catch (InterruptedException msg) {
            String err = "Thread interrupted. Please try again.";
            usageAndExit(err, false);
        }

        // calculate final statistics
        long avgTime = 0;
        if (totalPktsReceived > 0) avgTime = totalTime / totalPktsReceived;
        if (minTime == 2147483647) minTime = 0;
        needToPrint = false;
        printResults(avgTime);
    }

    /**
     * Helper function to just handle sockets.
     */
    private void _runPing() {
        long startTime;
        long pktTime;

        try {
            RawSocket rawSocket = new RawSocket();
            rawSocket.open(RawSocket.PF_INET, RawSocket.getProtocolByName("icmp"));
            rawSocket.setReceiveTimeout(2000);

            // create arrays for send/receive data
            byte[] sendData = createICMPPkt();
            byte[] receiveData = new byte[sendData.length + 32];

            // start time and send packet
            startTime = System.currentTimeMillis();
            rawSocket.write(pingAddress, sendData);

            // wait for response, if call times out, catch exception and count lost packet
            rawSocket.read(receiveData);
            pktTime = System.currentTimeMillis() - startTime;

            // parse received packet for output data
            int[] pktInfo = parsePkt(receiveData);
            updateTimes(pktTime);
            addPkt();

            // close open resources
            rawSocket.close();

            System.out.printf(replyStr, pktInfo[2], pktInfo[3], pktInfo[4],
                    pktInfo[5], pktInfo[0], pktTime, pktInfo[1]);
        } catch (SocketException | SecurityException msg) {
            String err = "Socket error. Please try again.";
            usageAndExit(err, false);
        } catch (UnknownHostException msg) {
            String err = "Host name is unknown. Please try again.";
            usageAndExit(err, false);
        } catch (IOException msg) {   // also catching SocketTimeoutException
            System.out.println("Request timed out.");
            lostPkt();
        }
    }

    /**
     * Utility function that creates an ICMP Echo request packet.
     * @return byte array of ICMP packet
     */
    private byte[] createICMPPkt() {
        byte[] sendData = new byte[pktSize];
        sendData[0] = 8;
        sendData[1] = 0;
        sendData[2] = (byte) 0xf7;
        sendData[3] = (byte) 0xff;
        sendData[4] = 0;
        sendData[5] = 0;
        sendData[6] = 0;
        sendData[7] = 0;
        return sendData;
    }

    /**
     * Utility function that pulls relevant data from echo reply
     * @param pktData ICMP reply packet
     * @return array containing packet length, TTL, src/dst addresses
     */
    private int[] parsePkt(byte[] pktData) {
        int[] pktInfo = new int[10];

        // ICMP packet length
        String tempLen = String.format("%02x%02x", pktData[2], pktData[3]);
        pktInfo[0] = Integer.parseInt(tempLen, 16) - 20;

        // TTL
        pktInfo[1] = Integer.parseInt(String.format("%02x", pktData[8]), 16);

        // source and destination addresses
        for (int count = 2; count < pktInfo.length; count++) {
            pktInfo[count] = Integer.parseInt(String.format("%02x", pktData[count + 10]), 16);
        }
        return pktInfo;
    }

    /**
     * Function to update various times.
     * @param pktTime current packet's RTT
     */
    private static void updateTimes(long pktTime) {
        if (pktTime < minTime) minTime = pktTime;
        if (pktTime > maxTime) maxTime = pktTime;
        totalTime += pktTime;
    }

    /**
     * Function to count a received packet.
     */
    private static void addPkt() {totalPktsReceived++;}

    /**
     * Function to count a lost packet.
     */
    private static void lostPkt() {totalPktsLost++;}

    /**
     * Function to calculate and print results of program.
     * @param avgTime average RTT
     */
    private static void printResults(long avgTime) {
        // calculate packet loss percentage
        float pktLostPer = 100 - (((float) totalPktsReceived / totalPktsSent) * 100);

        System.out.println();
        System.out.printf(pingStats, pingAddress.getHostAddress(), totalPktsSent,
                totalPktsReceived, totalPktsLost, pktLostPer);
        System.out.printf(pingTimes, minTime, maxTime, avgTime);
    }
}
