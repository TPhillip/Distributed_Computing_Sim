package com.distributed.phase2;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Scanner;

//this class allows a peer to be run independently, from the command line
public class PeerRunner {
    private static Peer peer;
    public static void main(String[] args){
        peer = new Peer();
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print("peer$: ");
            handleInput(scanner.nextLine());
        }
    }

    //Interprets commands from user providing goofy command-line type interface
    private static void handleInput(String input) {
        String[] commandString = input.split(" ");
        if (commandString.length < 3) {
            System.out.println("[Invalid command !]");
            return;
        }
        switch (commandString[0]) {
            case "send":
                if (peer.getName() == null || peer.getServerRouterAddress() == null) {
                    System.out.println("[Name and serverrouter address must be set first !]");
                    return;
                }
                String message = "";
                for (int i = 2; i < commandString.length; i++)
                    message = message + " " + commandString[i];
                peer.sendMsg(commandString[1], message);
                break;
            case "sendFile":
                try {
                    File file = new File(commandString[2]);
                    peer.sendFile(commandString[1], file);
                    return;
                } catch (FileNotFoundException e) {
                    System.err.println("[File not found !]");
                } catch (IOException e) {
                    System.err.println("[Encountered IO error reading file !]");
                }
            case "set":
                if (commandString[1].equalsIgnoreCase("name")) {
                    if (peer.getName() != null) {
                        System.out.println("[Name is already set !]");
                        return;
                    }
                    peer.setName(commandString[2]);
                    return;
                } else if (commandString[1].equalsIgnoreCase("serverrouter") || commandString[1].equalsIgnoreCase("sr")) {
                    try {
                        if (peer.getName() == null) {
                            System.out.println("[name must be set first !]");
                            return;
                        }
                        if (peer.getServerRouterAddress() != null) {
                            System.out.println("[serverrouter is already set !]");
                            return;
                        }
                        peer.publish(InetAddress.getByName(commandString[2]));
                        return;
                    } catch (UnknownHostException e) {
                        System.out.println("[Invalid address! Try again]");
                        return;
                    }
                } else
                    System.out.println("[Invalid command !]");
                break;
            default:
                System.out.println("[Command not found !]");
        }
    }
}
