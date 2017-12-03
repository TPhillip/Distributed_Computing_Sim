package com.distributed.phase2;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Scanner;

public class Metrics {
    private static Peer[] allPeers;
    private static String otherPeersName;
    private static String serverRouterAddress;
    private static final String fileName = "conceptual.jpg";

    public static void main(String[] args) {
        int peerAmount = 100;

        try {
            serverRouterAddress = InetAddress.getLocalHost().getHostAddress();
            otherPeersName = args[1];
            allPeers = new Peer[peerAmount];

            for (int i = 0; i < peerAmount; i++) {
                allPeers[i] = new Peer(String.format("peer-%s%d", args[0], i + 1), serverRouterAddress);
            }
            Scanner scanner = new Scanner(System.in);
            System.out.print("Press enter to send messages from this peer group.");
            scanner.nextLine();
            for (int i = 0; i < peerAmount; i++) {
                Thread.sleep(1000);
                allPeers[i].sendFile(String.format("peer-%s%d", otherPeersName, i + 1), new File(fileName));
            }
        } catch (UnknownHostException e) {
            System.err.println("unknown host: "+e.getMessage());
            System.exit(1535);
            return;
        } catch (IOException e) {
            System.err.println("IO error "+e.getMessage());
            System.exit(1533);
        } catch (InterruptedException e) {
            //hum....
        }
    }
}
