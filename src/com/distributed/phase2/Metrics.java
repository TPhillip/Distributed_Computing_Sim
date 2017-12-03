package com.distributed.phase2;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class Metrics {
    private static Peer[] allPeers;
    private static String otherPeersName;
    private static String serverRouterAddress;

    public static void main(String[] args) {
        int peers = 10;

        try {
            serverRouterAddress = InetAddress.getLocalHost().getHostAddress();
            otherPeersName = args[1];
            allPeers = new Peer[peers];

            for (int i = 0; i < peers; i++) {
                allPeers[i] = new Peer(String.format("peer-%s%d", args[0], i + 1), serverRouterAddress);
            }
            System.out.println(allPeers.length);
            for (int i = 0; i < peers; i++) {
                Thread.sleep(1000);
                allPeers[i].sendFile(String.format("peer-%s%d", otherPeersName, i + 1), new File("IMG_2481.png"));
            }
        } catch (UnknownHostException e) {
            e.printStackTrace();
            return;
        } catch (IOException e) {
            System.err.println("IO error");
            System.exit(1533);
        } catch (InterruptedException e) {
            //hum....
        }
    }

    private static void writeStat(String fileName, int contentLength, int elapsedTime) {

    }
}
