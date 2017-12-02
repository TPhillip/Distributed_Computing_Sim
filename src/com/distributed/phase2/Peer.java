package com.distributed.phase2;

import com.distributed.phase2.components.MessageObject;
import com.distributed.phase2.components.PeerAddressRequest;
import com.distributed.phase2.components.PeerAllocationRequest;
import com.distributed.phase2.components.PeerNotFoundException;

import java.io.*;
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
        MessageListener messageListenerThread = new MessageListener();
        messageListenerThread.start();
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
                if (name == null || serverRouterAddress == null) {
                    System.out.println("[Name and serverrouter address must be set first !]");
                    return;
                }
                String message = "";
                for (int i = 2; i < commandString.length; i++)
                    message = message + " " + commandString[i];
                sendMsg(commandString[1], message);
                break;
            case "sendFile":
                try {
                    File file = new File(commandString[2]);
                    sendFile(commandString[1], file);
                    return;
                } catch (FileNotFoundException e) {
                    System.err.println("[File not found !]");
                } catch (IOException e) {
                    System.err.println("[Encountered IO error reading file !]");
                }
            case "set":
                if (commandString[1].equalsIgnoreCase("name")) {
                    if (name != null) {
                        System.out.println("[Name is already set !]");
                        return;
                    }
                    name = commandString[2];
                    return;
                } else if (commandString[1].equalsIgnoreCase("serverrouter") || commandString[1].equalsIgnoreCase("sr")) {
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

    //method announces this peer's name and IP address and port to the ServerRouter
    private static void publish(InetAddress routerAddress) {
        try{
            Socket socket = new Socket(routerAddress, ports[0]);
            socket.setSoTimeout(3000);
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(socket.getOutputStream());
            //peerAllocationRequest object simplifies the act of sending multiple values to the ServerRouter
            objectOutputStream.writeObject(new PeerAllocationRequest(name, peerAddress));
            objectOutputStream.close();
            socket.close();
            serverRouterAddress = routerAddress;
        }catch (IOException e){
            System.err.println("Unable to publish address to serverRouter, aborting...");
        }
    }

    //method takes a peer name and attempts to connect to ServerRouter defined by serverRouterAddress and return its ip and port
    private static InetSocketAddress resolvePeer(String peerName) throws IOException, ClassNotFoundException, InterruptedException, PeerNotFoundException {
        //Socket used to send request to ServerSocket listening in ServerRouter's AllocateThread
        Socket socket = new Socket(serverRouterAddress, ports[2]);
        ObjectOutputStream objectOutputStream = new ObjectOutputStream(socket.getOutputStream());
        long startTime = System.currentTimeMillis();
        objectOutputStream.writeObject(new PeerAddressRequest(peerName, peerAddress));
        objectOutputStream.close();
        socket.close();

        //peer listens for server response after sending request
        //this could cause problems if multiple peers try to resolve on the same host at the same time
        ServerSocket serverSocket = new ServerSocket(ports[3],0,peerAddress.getAddress());
        serverSocket.setSoTimeout(2000);
        Socket discoveryListener = serverSocket.accept();
        discoveryObjectReciever = new ObjectInputStream(discoveryListener.getInputStream());
        //de-serialize object containing peer's IP address and port
        InetSocketAddress otherPeerAddress = (InetSocketAddress) discoveryObjectReciever.readObject();
        long endTime = System.currentTimeMillis();
        discoveryObjectReciever.close();
        discoveryListener.close();
        serverSocket.close();
        //If ServerRouter returns an InetSocketAddress with port == 0, then the peer does not exist
        if (otherPeerAddress.getPort() == 0)
            throw new PeerNotFoundException();
        System.out.print(String.format("    Lookup completed in %d ms", (endTime - startTime)));
        return otherPeerAddress;
    }

    private static void sendFile(String peerName, File file) throws FileNotFoundException, IOException {
        FileInputStream fileInputStream = new FileInputStream(file);
        byte[] fileBytes = new byte[fileInputStream.available()];
        fileInputStream.read(fileBytes);
        MessageObject messageObject = new MessageObject(name, fileBytes, file.getName());
        sendMessageObject(peerName, messageObject);
    }

    //method to send a message to another peer
    private static void sendMsg(String peerName, String message){
        System.out.println(String.format("Sending message to %s...", peerName));
        MessageObject messageObject = new MessageObject(name, message);
        sendMessageObject(peerName, messageObject);
    }

    private static void sendMessageObject(String peerName, MessageObject message) {
        try {
            //get the IP address and port of the peer that we're sending the message to
            InetSocketAddress otherPeer = resolvePeer(peerName);

            //connect socket to other peer's ServerSocket
            Socket socket = new Socket(otherPeer.getAddress(), otherPeer.getPort());
            socket.setSoTimeout(3000);
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(socket.getOutputStream());
            //serialize message object and send it to the other peer
            objectOutputStream.writeObject(message);
            objectOutputStream.close();
            socket.close();
        } catch (SocketTimeoutException e) {
            System.err.println("ServerRouter did not resolve in time, Message was not sent...");
            return;
        } catch (IOException e) {
            System.err.println(String.format("IO error: %s", e.getMessage()));
            return;
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (PeerNotFoundException e) {
            System.err.println(String.format("ServerRouter was unable to find a peer by the name \"%s\", Message was not sent...", peerName));
            return;
        }
    }

    //thread listens for incomming message on machine's IP and port from 'peerAddress'
    static class MessageListener extends Thread{
        private ServerSocket serverSocket;
        public MessageListener(){
            try {
                //creating a ServerSocket with port zero will let the system automatically select a random free port,
                //allows us to run multiple peers on a single host
                serverSocket = new ServerSocket(0, 0, InetAddress.getLocalHost());
                peerAddress = new InetSocketAddress(serverSocket.getInetAddress(), serverSocket.getLocalPort());
            }catch (IOException e){
                System.err.println("Peer was unable to listen for connections! Terminating!");
                System.exit(1000);
            }
        }

        public void run(){
            try{
                while(true){
                    //thread execution waits here until message object is received

                    Socket messageListener = serverSocket.accept();
                    long startTime = System.currentTimeMillis();
                    messageObjectReciever = new ObjectInputStream(messageListener.getInputStream());
                    MessageObject messageObject = (MessageObject)  messageObjectReciever.readObject();
                    long endTime = System.currentTimeMillis();
                    if (messageObject.containsMessage())
                        System.out.print(String.format("\n%s: %s\npeer$: ", messageObject.getSender(), messageObject.getData()));
                    if (messageObject.containsFile()) {
                        System.out.println(String.format("Recieved \"%s\" from %s: (%s bytes)", messageObject.getFileName(), messageObject.getSender(), messageObject.getFileBytes().length));
                        File outFile = new File(messageObject.getFileName());
                        FileOutputStream fileOutputStream = new FileOutputStream(outFile);
                        fileOutputStream.write(messageObject.getFileBytes());
                        fileOutputStream.close();
                    }
                    System.out.print(String.format("Message recieved in %d ms\npeer$: ", (endTime - startTime)));
                    messageObjectReciever.close();
                    messageListener.close();
                }
            }catch(IOException e){
                e.printStackTrace();
            }catch(ClassNotFoundException e){
                e.printStackTrace();
            }
        }
    }
}
