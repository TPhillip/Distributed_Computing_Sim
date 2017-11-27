package com.distributed.phase2;

import com.distributed.phase2.components.PeerAddressRequest;
import com.distributed.phase2.components.PeerAllocationRequest;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.*;
import java.util.HashMap;
import java.util.Map;

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
            peerMap = new HashMap<String, InetSocketAddress>();
            serverRouterAddress = InetAddress.getLocalHost();
            AllocateThread allocHandler = new AllocateThread(serverRouterAddress);
            allocHandler.start();
            LookupThread lookupHandler = new LookupThread(serverRouterAddress);
            lookupHandler.start();
        } catch (UnknownHostException e) {
            System.err.println(e.getMessage());
        }
        System.out.println(String.format("Binding serverRouter to address %s", serverRouterAddress.toString()));

    }

    static class AllocateThread extends Thread {
        private InetAddress bindAddress;
        AllocateThread(InetAddress bindAddress){
            this.bindAddress = bindAddress;
        }

        private void allocateClient(PeerAllocationRequest par){
            peerMap.put(par.getPeerName(), par.getPeerAddress());
            System.out.println(String.format("Discovered peer \"%s\" at address %s",par.getPeerName(), par.getPeerAddress().toString()));
        }

        public void run(){
            while(true){
                try{
                    //open ServerSocket for allocation request
                    ServerSocket allocationSocket = new ServerSocket(ports[0],0,bindAddress);
                    Socket allocationClientSocket = allocationSocket.accept();
                    allocationObjectIn = new ObjectInputStream(allocationClientSocket.getInputStream());
                    PeerAllocationRequest peerAllocationRequest = (PeerAllocationRequest) allocationObjectIn.readObject();
                    allocateClient(peerAllocationRequest);
                    allocationObjectIn.close();
                    allocationClientSocket.close();
                    allocationSocket.close();

                }
                catch (IOException e) {
                    e.printStackTrace();
                }catch (ClassNotFoundException e){
                    e.printStackTrace();
                }
            }
        }
    }

    static class LookupThread extends Thread {
        private InetAddress bindAddress;
        LookupThread(InetAddress bindAddress){
            this.bindAddress = bindAddress;
        }

        private void findPeer(PeerAddressRequest request) throws IOException, InterruptedException{
            System.out.println(String.format("%s requests location of peer %s", request.getRequesterAddress().toString(), request.getClientName()));
            if(peerMap.containsKey(request.getClientName())){
                //send resolved InetSocketAddress from peerMap to requestingPeer
                Socket socket = new Socket(request.getRequesterAddress().getAddress(), ports[3]);
                ObjectOutputStream socketObjectOutputStream = new ObjectOutputStream(socket.getOutputStream());
                socketObjectOutputStream.writeObject(peerMap.get(request.getClientName()));
                System.out.println(String.format("  Request was completed by ServerRouter at %s, peer is at %s", bindAddress.toString(), peerMap.get(request.getClientName()).toString()));
                socketObjectOutputStream.close();
                socket.close();
            } else if (nextServerRouter != null) {
                //forward request for peer to nextServerRouter. it will respond to the original requester
                Socket socket = new Socket(nextServerRouter, ports[2]);
                ObjectOutputStream objectOutputStream = new ObjectOutputStream(socket.getOutputStream());
                objectOutputStream.writeObject(request);
                objectOutputStream.close();
                socket.close();
            }
            else{
                //the request was unable to be resolved
                Socket socket = new Socket(request.getRequesterAddress().getAddress(), ports[3]);
                ObjectOutputStream socketObjectOutputStream = new ObjectOutputStream(socket.getOutputStream());
                socketObjectOutputStream.writeObject(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
                System.err.println(String.format("  Unable to resolve request, ServerRouter does not know peer"));
                socketObjectOutputStream.close();
                socket.close();

            }
        }

        public void run(){
            while(true){
                try{
                    //ServerSocket listens for lookup request
                    ServerSocket lookupSocket = new ServerSocket(ports[2],0,bindAddress);
                    Socket lookupClientSocket = lookupSocket.accept();
                    lookupObjectIn = new ObjectInputStream(lookupClientSocket.getInputStream());
                    findPeer((PeerAddressRequest) lookupObjectIn.readObject());

                    lookupObjectIn.close();
                    lookupClientSocket.close();
                    lookupSocket.close();
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
