package com.distributed.phase2;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Scanner;

//runs batch jobs
public class Metrics {
    private static Peer[] allPeers;
    private static String otherPeersName;
    private static String serverRouterAddress;
    private static String fileName;

    public static void main(String[] args) {
        //sets the amount of peers to be "spun up"
        int peerAmount = 100;
        try {
            //assumes serverRouter for this group of peers will be on the same local machine
            serverRouterAddress = InetAddress.getLocalHost().getHostAddress();
            //The name prefix for each peer in the other peer group that we will expect
            otherPeersName = args[1];
            fileName = args[2];
            allPeers = new Peer[peerAmount];

            //this section creates all the peer objects anf stores them into an array
            for (int i = 0; i < peerAmount; i++) {
                allPeers[i] = new Peer(String.format("peer-%s%d", args[0], i + 1), serverRouterAddress);
            }
            Scanner scanner = new Scanner(System.in);
            System.out.print("Press enter to send messages from this peer group.");
            scanner.nextLine();

            //this section begins sending messages to all the peers in the other group (addressed by otherPeersName and a number)
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
