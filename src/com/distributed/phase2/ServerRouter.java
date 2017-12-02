package com.distributed.phase2;

import com.distributed.phase2.components.PeerAddressRequest;
import com.distributed.phase2.components.PeerAllocationRequest;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class ServerRouter {
    private static Map peerMap;
    private static InetAddress nextServerRouter;
    private static InetAddress serverRouterAddress;
    //port-num determines action since request is only a serialized object
    //ports[0]: allocate new peer
    //ports[1]: process peer lookup request
    //ports[2]: serverRouter discovery request port
    //ports[3]: peer discovery request
    private static int[] ports = {5555,5556,5557,5558};
    private static ObjectInputStream allocationObjectIn, lookupObjectIn;


    public static void main(String[] args){
        try {
            //stores each peers IP/Port under a name
            peerMap = new HashMap<String, InetSocketAddress>();
            serverRouterAddress = InetAddress.getLocalHost();
            //creates and runs thread to listen for peer connection requests
            AllocateThread allocHandler = new AllocateThread(serverRouterAddress);
            allocHandler.start();
            //creates and runs thread to listen for peer lookup requests
            LookupThread lookupHandler = new LookupThread(serverRouterAddress);
            lookupHandler.start();
        } catch (UnknownHostException e) {
            System.err.println(e.getMessage());
        }
        System.out.println(String.format("Binding serverRouter to address %s", serverRouterAddress.toString()));

        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print("serverRouter$: ");
            handleInput(scanner.nextLine());
        }

    }

    public ServerRouter(String otherServerRouter) {
        try {
            nextServerRouter = InetAddress.getByName(otherServerRouter);
            AllocateThread allocHandler = new AllocateThread(serverRouterAddress);
            allocHandler.start();
            //creates and runs thread to listen for peer lookup requests
            LookupThread lookupHandler = new LookupThread(serverRouterAddress);
            lookupHandler.start();
        } catch (UnknownHostException e) {
            e.printStackTrace();
            return;
        }
    }

    //Interprets commands from user providing goofy command-line type interface
    private static void handleInput(String input) {
        String[] commandString = input.split(" ");
        try {
            switch (commandString[0]) {
                case "set":
                    if (commandString.length < 3) {
                        System.out.println("[Invalid command !]");
                        return;
                    } else if (commandString[1].equalsIgnoreCase("serverrouter")) {
                        nextServerRouter = InetAddress.getByName(commandString[2]);
                        System.out.println(String.format("Acknowledged other serverRouter at %s", nextServerRouter));
                    } else
                        System.out.println("[Invalid command !]");
                    break;
                default:
                    System.out.println("[Command not found !]");
            }
        } catch (UnknownHostException e) {
            System.out.println("[Invalid address! Try again]");
            return;
        }
    }

    //thread responds to peers wishing to announce their address to the ServerRouter
    static class AllocateThread extends Thread {
        private ServerSocket allocationSocket;

        //thread constructor opens ServerSocket to listen
        AllocateThread(InetAddress bindAddress){
            try {
                //open ServerSocket for allocation request
                allocationSocket = new ServerSocket(ports[0], 0, bindAddress);
            }catch (IOException e) {
                System.err.println("ServerRouter was unable to listen for allocation requests! Terminating...");
                System.exit(1001);
            }
        }

        //method takes peer name and IP/Port from request object and adds them to the peerMap
        private void allocateClient(PeerAllocationRequest par){
            peerMap.put(par.getPeerName(), par.getPeerAddress());
            System.out.println(String.format("Discovered peer \"%s\" at address %s",par.getPeerName(), par.getPeerAddress().toString()));
        }

        public void run(){
            while(true){
                try{
                    //ServerRouter listens (waits) for peer allocation request
                    Socket allocationClientSocket = allocationSocket.accept();
                    allocationObjectIn = new ObjectInputStream(allocationClientSocket.getInputStream());
                    PeerAllocationRequest peerAllocationRequest = (PeerAllocationRequest) allocationObjectIn.readObject();
                    allocateClient(peerAllocationRequest);
                    allocationObjectIn.close();
                    allocationClientSocket.close();
                }
                catch (IOException e) {
                    e.printStackTrace();
                }catch (ClassNotFoundException e){
                    e.printStackTrace();
                }
            }
        }
    }

    //Thread responds to peers wishing to get the IP of a specific peer
    static class LookupThread extends Thread {
        private InetAddress bindAddress;
        private ServerSocket lookupSocket;

        LookupThread(InetAddress bindAddress){
            try {
                this.bindAddress = bindAddress;
                lookupSocket = new ServerSocket(ports[2], 0, bindAddress);
            }catch (IOException e) {
                System.err.println("ServerRouter was unable to listen for lookup requests! Terminating...");
                System.exit(1002);
            }
        }

        //method replies with IP of peer in 'request'
        private void findPeer(PeerAddressRequest request) throws IOException, InterruptedException{
            System.out.println(String.format("%s requests location of peer %s", request.getRequesterAddress().toString(), request.getClientName()));
            //if this ServerRouter know the peer in the request
            if(peerMap.containsKey(request.getClientName())){
                //send IP to requesting peer (IP is in the request object)
                Socket socket = new Socket(request.getRequesterAddress().getAddress(), ports[3]);
                ObjectOutputStream socketObjectOutputStream = new ObjectOutputStream(socket.getOutputStream());
                socketObjectOutputStream.writeObject(peerMap.get(request.getClientName()));
                System.out.println(String.format("  Request was completed by ServerRouter at %s, peer is at %s", bindAddress.toString(), peerMap.get(request.getClientName()).toString()));
                socketObjectOutputStream.close();
                socket.close();
            //if this serverRouter does not know the peer in the request, but there is another ServerRouter available
            } else if (nextServerRouter != null) {
                //forward request for peer to nextServerRouter. it will respond to the original requester
                Socket socket = new Socket(nextServerRouter, ports[2]);
                ObjectOutputStream objectOutputStream = new ObjectOutputStream(socket.getOutputStream());
                objectOutputStream.writeObject(request);
                System.out.println(String.format("  Request was forwarded to nextServerRouter at %s", nextServerRouter.toString()));
                objectOutputStream.close();
                socket.close();
            }
            //this serverrouter does not know the peer in the request, and there is no other ServerRouter available
            else{
                //the request was unable to be resolved, send an "empty" InetSocketRequest object pointing to localhost
                Socket socket = new Socket(request.getRequesterAddress().getAddress(), ports[3]);
                ObjectOutputStream socketObjectOutputStream = new ObjectOutputStream(socket.getOutputStream());
                //and InetSocketAddress with port == 0 represents a failed lookup attempt
                socketObjectOutputStream.writeObject(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
                System.err.println(String.format("  Unable to resolve request, ServerRouter does not know peer"));
                socketObjectOutputStream.close();
                socket.close();

            }
        }

        public void run(){
            while(true){
                try{
                    //ServerSocket listens (waits) for lookup request
                    Socket lookupClientSocket = lookupSocket.accept();
                    lookupObjectIn = new ObjectInputStream(lookupClientSocket.getInputStream());
                    findPeer((PeerAddressRequest) lookupObjectIn.readObject());
                    lookupObjectIn.close();
                    lookupClientSocket.close();
                }
                catch (IOException e) {
                    e.printStackTrace();
                }catch (ClassNotFoundException e){
                    e.printStackTrace();
                }catch (InterruptedException e){
                    e.printStackTrace();
                }
            }
        }
    }
}
