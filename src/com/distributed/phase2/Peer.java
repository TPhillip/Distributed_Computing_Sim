package com.distributed.phase2;

import com.distributed.phase2.components.MessageObject;
import com.distributed.phase2.components.PeerAddressRequest;
import com.distributed.phase2.components.PeerAllocationRequest;
import com.distributed.phase2.components.PeerNotFoundException;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.*;
import java.util.Scanner;

import static java.lang.Thread.sleep;

public class Peer {
    private static InetAddress serverRouterAddress;
    private static InetSocketAddress peerAddress;
    //port-num determines action since request is only a serialized object
    //ports[0]: allocate new peer
    //ports[1]: process peer lookup request
    //ports[2]: peer discovery/announce
    //ports[3]: peer discovery request
    private static int[] ports = {5555,5556,5557,5558};
    private static String name;
    private static ObjectInputStream messageObjectReciever, discoveryObjectReciever;

    public static void main(String[] args){
        try {
            peerAddress = new InetSocketAddress(InetAddress.getLocalHost(), 6000);
            MessageListener messageListenerThread = new MessageListener();
            messageListenerThread.start();
        } catch (UnknownHostException e) {
            System.err.println(e.getMessage());
        }

        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print("peer$: ");
            handleInput(scanner.nextLine());
        }
    }

    private static void handleInput(String input) {
        String[] commandString = input.split(" ");
        switch (commandString[0]) {
            case "send":
                if (commandString.length < 3) {
                    System.out.println("[Invalid command !]");
                    return;
                } else if (name == null || serverRouterAddress == null) {
                    System.out.println("[Name and serverrouter address must be set first !]");
                    return;
                }
                String message = "";
                for (int i = 2; i < commandString.length; i++)
                    message = message + " " + commandString[i];
                sendMsg(commandString[1], message);
                break;
            case "set":
                if (commandString.length < 3) {
                    System.out.println("[Invalid command !]");
                    return;
                }
                if (commandString[1].equalsIgnoreCase("name")) {
                    if (name != null) {
                        System.out.println("[Name is already set !]");
                        return;
                    }
                    name = commandString[2];
                    return;
                } else if (commandString[1].equalsIgnoreCase("serverrouter")) {
                    try {
                        if (name == null) {
                            System.out.println("[name must be set first !]");
                            return;
                        }
                        if (serverRouterAddress != null) {
                            System.out.println("[serverrouter is already set !]");
                            return;
                        }
                        publish(InetAddress.getByName(commandString[2]));
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

    private static void publish(InetAddress routerAddress) {
        try{
            Socket socket = new Socket(routerAddress, ports[0]);
            socket.setSoTimeout(3000);
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(socket.getOutputStream());
            objectOutputStream.writeObject(new PeerAllocationRequest(name, peerAddress));
            objectOutputStream.close();
            socket.close();
            serverRouterAddress = routerAddress;
        }catch (IOException e){
            System.err.println("Unable to publish address to serverRouter, aborting...");
        }
    }

    private static InetSocketAddress resolvePeer(String peerName) throws IOException, ClassNotFoundException, InterruptedException, PeerNotFoundException {
        //peer sends lookup request object
        Socket socket = new Socket(serverRouterAddress, ports[2]);
        ObjectOutputStream objectOutputStream = new ObjectOutputStream(socket.getOutputStream());
        objectOutputStream.writeObject(new PeerAddressRequest(peerName, peerAddress));
        objectOutputStream.close();
        socket.close();

        InetSocketAddress otherPeerAddress;

        //peer listens for server response
        ServerSocket serverSocket = new ServerSocket(ports[3],0,peerAddress.getAddress());
        serverSocket.setSoTimeout(2000);
        Socket discoveryListener = serverSocket.accept();
        discoveryObjectReciever = new ObjectInputStream(discoveryListener.getInputStream());
        otherPeerAddress = (InetSocketAddress) discoveryObjectReciever.readObject();
        discoveryObjectReciever.close();
        discoveryListener.close();
        serverSocket.close();
        if (otherPeerAddress.getPort() == 0)
            throw new PeerNotFoundException();
        return otherPeerAddress;
    }

    private static void sendMsg(String peerName, String message){
        System.out.println(String.format("Sending message to %s...", peerName));
        try{
            MessageObject messageObject = new MessageObject(name, message);
            InetSocketAddress otherPeer = resolvePeer(peerName);

            Socket socket = new Socket(otherPeer.getAddress(), otherPeer.getPort());
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(socket.getOutputStream());
            objectOutputStream.writeObject(messageObject);
            objectOutputStream.close();
            socket.close();
            sleep(500);
        } catch (SocketTimeoutException e) {
            System.err.println("ServerRouter did not resolve in time, Message was not sent...");
            return;
        }catch (IOException e){
            System.err.println("Error connecting to ServerRouter for address lookup, Message was not sent...");
            return;
        }catch (ClassNotFoundException e){
            e.printStackTrace();
        }catch (InterruptedException e){
            e.printStackTrace();
        } catch (PeerNotFoundException e) {
            System.err.println(String.format("ServerRouter was unable to find a peer by the name \"%s\", Message was not sent...", peerName));
            return;
        }
    }

    static class MessageListener extends Thread{
        public void run(){
            try{
                while(true){
                    ServerSocket serverSocket = new ServerSocket(peerAddress.getPort(),0,peerAddress.getAddress());
                    Socket messageListener = serverSocket.accept();
                    messageObjectReciever = new ObjectInputStream(messageListener.getInputStream());
                    MessageObject messageObject = (MessageObject)  messageObjectReciever.readObject();
                    System.out.println(String.format("\n%s :: %s", messageObject.getSender(), messageObject.getData()));
                    System.out.print("peer$: ");
                    messageObjectReciever.close();
                    messageListener.close();
                    serverSocket.close();
                }
            }catch(IOException e){

                e.printStackTrace();
            }catch(ClassNotFoundException e){
                e.printStackTrace();
            }
        }
    }
}
