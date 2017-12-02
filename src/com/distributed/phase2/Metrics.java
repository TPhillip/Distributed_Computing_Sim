package com.distributed.phase2;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class Metrics {
    private static Peer[] allPeers;
    private static String otherPeersName;
    private static String serverRouterAddress;

    public static void main(String[] args) {
        int peers = 100;
        
        try {
            serverRouterAddress = InetAddress.getLocalHost().toString();
        } catch (UnknownHostException e) {
            e.printStackTrace();
            return;
        }
        otherPeersName = args[1];

        allPeers = new Peer[peers];

        for (int i = 0; i < peers; i++) {
            allPeers[i] = new Peer(String.format("peer-%s%d", args[0], i + 1), serverRouterAddress);
        }
        System.out.println(allPeers.length);
        for (int i = 0; i < peers; i++) {
            SendMsg sendThread = new SendMsg(i, otherPeersName, "README.md");
            sendThread.start();
        }
    }

    private static class SendMsg extends Thread {
        private String revievingPeer;
        private String fileName;
        private int i;

        public SendMsg(int i, String recievingPeer, String fileName) {
            this.i = i;
            this.revievingPeer = String.format("peer-%s%d", recievingPeer, i);
            this.fileName = fileName;
        }

        public void run() {
            try {
                allPeers[i].sendFile(revievingPeer, new File(fileName));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                return;
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
        }
    }
}
