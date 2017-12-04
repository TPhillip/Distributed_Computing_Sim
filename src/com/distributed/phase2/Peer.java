package com.distributed.phase2;

import com.distributed.phase2.components.MessageObject;
import com.distributed.phase2.components.PeerAddressRequest;
import com.distributed.phase2.components.PeerAllocationRequest;
import com.distributed.phase2.components.PeerNotFoundException;

import java.io.*;
import java.net.*;

public class Peer {
    private InetAddress serverRouterAddress;
    private InetSocketAddress peerAddress;
    //port-num determines action since request is only a serialized object
    //ports[0]: allocate new peer
    //ports[1]: process peer lookup request
    //ports[2]: peer discovery/announce
    //ports[3]: peer discovery request
    private int[] ports = {5555, 5556, 5557, 5558};
    private String name;
    private ObjectInputStream messageObjectReciever, discoveryObjectReciever;
    //static filewriter member allows all peers in metrics class to write to the same log file.
    private static BufferedWriter fileWriter;

    //constructor used when peer is run individually from PeerRunner class
    public Peer() {
        MessageListener messageListenerThread = new MessageListener();
        messageListenerThread.start();
        try {
            fileWriter = new BufferedWriter(new FileWriter("log.txt", true));
        }catch (IOException e){
            System.err.println("Unable to open log file");
        }
    }

    //getters and setters....
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public InetAddress getServerRouterAddress() {
        return serverRouterAddress;
    }

    //constructor used when peer is run from the Metrics class
    public Peer(String name, String serverRouter) {
        MessageListener messageListenerThread = new MessageListener();
        messageListenerThread.start();
        try {
            //instantiate fileWriter if this is the first peer to be created in metrics
            if (fileWriter == null)
                fileWriter = new BufferedWriter(new FileWriter("log.txt", true));
            this.name = name;
            publish(InetAddress.getByName(serverRouter));
        } catch (UnknownHostException e) {
            e.printStackTrace();
            return;
        } catch (IOException e){
            System.err.println("Unable to open log file");
        }
    }

    //method announces this peer's name and IP address and port to the ServerRouter
    public void publish(InetAddress routerAddress) {
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
    public InetSocketAddress resolvePeer(String peerName) throws IOException, ClassNotFoundException, InterruptedException, PeerNotFoundException {
        //Socket used to send request to ServerSocket listening in ServerRouter's AllocateThread
        Socket socket = new Socket(serverRouterAddress, ports[2]);
        ObjectOutputStream objectOutputStream = new ObjectOutputStream(socket.getOutputStream());
        long startTime = System.currentTimeMillis();
        objectOutputStream.writeObject(new PeerAddressRequest(peerName, peerAddress));
        objectOutputStream.close();
        socket.close();

        //peer listens for server response after sending request
        //this could cause problems if multiple peers try to resolve on the same host at the same time
        //handle BindException recursively, wait and try again
        try {
            ServerSocket serverSocket = new ServerSocket(ports[3], 0, peerAddress.getAddress());
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
            //System.out.println(String.format("    Lookup completed in %d ms", (endTime - startTime)));
            return otherPeerAddress;
        }catch (BindException e) {
            Thread.sleep(1000);
            return resolvePeer(peerName);
        }
    }

    //method ot send a file
    public void sendFile(String peerName, File file) throws FileNotFoundException, IOException {
        FileInputStream fileInputStream = new FileInputStream(file);
        byte[] fileBytes = new byte[fileInputStream.available()];
        fileInputStream.read(fileBytes);
        MessageObject messageObject = new MessageObject(name, fileBytes, file.getName());
        SendMessage sendMessageThread = new SendMessage(peerName, messageObject);
        sendMessageThread.start();
    }

    //method to send a message to another peer
    public void sendMsg(String peerName, String message) {
        System.out.println(String.format("Sending message to %s...", peerName));
        MessageObject messageObject = new MessageObject(name, message);
        SendMessage sendMessageThread = new SendMessage(peerName, messageObject);
        sendMessageThread.start();
    }

    //sending messages in separate thread, (so large files don't hold up concurrent send operations in Metrics class)
    private class SendMessage extends Thread {
        private String peerName;
        private MessageObject messageObject;

        public SendMessage(String peerName, MessageObject messageObject) {
            this.peerName = peerName;
            this.messageObject = messageObject;
        }

        public void run() {
            try {
                //get the IP address and port of the peer that we're sending the message to
                InetSocketAddress otherPeer = resolvePeer(peerName);

                //connect socket to other peer's ServerSocket
                Socket socket = new Socket(otherPeer.getAddress(), otherPeer.getPort());
                socket.setSoTimeout(3000);
                ObjectOutputStream objectOutputStream = new ObjectOutputStream(socket.getOutputStream());
                //serialize message object and send it to the other peer
                objectOutputStream.writeObject(messageObject);
                objectOutputStream.close();
                socket.close();
                return;
            } catch (SocketTimeoutException e) {
                System.err.println("ServerRouter did not resolve in time, Message was not sent...");
                return;
            } catch (IOException e) {
                System.err.println(String.format("IO error"));
                e.printStackTrace();
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
    }

    //thread listens for incoming message on machine's IP and port from 'peerAddress'
    private class MessageListener extends Thread {
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
                        System.out.println(String.format("\n%s: %s", messageObject.getSender(), messageObject.getData()));
                    if (messageObject.containsFile()) {
                        System.out.println(String.format("Received \"%s\" from %s: (%s bytes)", messageObject.getFileName(), messageObject.getSender(), messageObject.getFileBytes().length));
                        //save transferred file to disk
                        File outFile = new File(messageObject.getFileName());
                        FileOutputStream fileOutputStream = new FileOutputStream(outFile);
                        fileOutputStream.write(messageObject.getFileBytes());
                        fileOutputStream.close();
                    }
                    int length = (messageObject.containsFile() ? messageObject.getFileBytes().length : messageObject.getData().length());
                    //write metrics to log-file
                    fileWriter.write(String.format("%s, %d, %d",messageObject.getFileName(), length, endTime - startTime));
                    fileWriter.newLine();
                    fileWriter.flush();
                    System.out.println(String.format("Message received in %d ms", (endTime - startTime)));
                    messageObjectReciever.close();
                    messageListener.close();
                    System.out.print("peer$: ");
                }
            }catch(IOException e){
                e.printStackTrace();
            }catch(ClassNotFoundException e){
                e.printStackTrace();
            }
        }
    }
}
