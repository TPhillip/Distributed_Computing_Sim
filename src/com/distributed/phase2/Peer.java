package com.distributed.phase2;

import com.distributed.phase2.components.MessageObject;
import com.distributed.phase2.components.PeerAddressRequest;
import com.distributed.phase2.components.PeerAllocationRequest;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

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
    private static String name = "tevin";
    private static ObjectInputStream messageObjectReciever, discoveryObjectReciever;

    public static void main(String[] args){
        serverRouterAddress = InetAddress.getLoopbackAddress();
        peerAddress = new InetSocketAddress(InetAddress.getLoopbackAddress(), 6000);
        MessageListener messageListenerThread = new MessageListener();
        messageListenerThread.start();
        try{
            publish(serverRouterAddress);
            sleep(1000);
            while(true)
                sendMsg("tevin", "Hello World");
        }
        catch (InterruptedException e){
            e.printStackTrace();
        }

    }

    private static void publish(InetAddress serverRouterAddress){
        try{
            Socket socket = new Socket(serverRouterAddress, ports[0]);
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(socket.getOutputStream());
            objectOutputStream.writeObject(new PeerAllocationRequest(name, peerAddress));
            objectOutputStream.close();
            socket.close();
        }catch (IOException e){
            e.printStackTrace();
        }
    }

    private static InetSocketAddress resolvePeer(String peerName) throws IOException, ClassNotFoundException, InterruptedException{
        //peer sends lookup request object
        Socket socket = new Socket(serverRouterAddress, ports[2]);
        ObjectOutputStream objectOutputStream = new ObjectOutputStream(socket.getOutputStream());
        objectOutputStream.writeObject(new PeerAddressRequest(name, peerAddress));
        objectOutputStream.close();
        socket.close();

        InetSocketAddress otherPeerAddress;

        //peer listens for server response
        ServerSocket serverSocket = new ServerSocket(ports[3],0,peerAddress.getAddress());
        Socket discoveryListener = serverSocket.accept();
        discoveryObjectReciever = new ObjectInputStream(discoveryListener.getInputStream());
        otherPeerAddress = (InetSocketAddress) discoveryObjectReciever.readObject();
        discoveryObjectReciever.close();
        discoveryListener.close();
        serverSocket.close();
        return otherPeerAddress;
    }

    private static void sendMsg(String peerName, String message){
        try{
            MessageObject messageObject = new MessageObject(name, message);
            InetSocketAddress otherPeer = resolvePeer(peerName);

            Socket socket = new Socket(otherPeer.getAddress(), otherPeer.getPort());
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(socket.getOutputStream());
            objectOutputStream.writeObject(messageObject);
            objectOutputStream.close();
            socket.close();
            sleep(500);
        }catch (IOException e){
            e.printStackTrace();
        }catch (ClassNotFoundException e){
            e.printStackTrace();
        }catch (InterruptedException e){
            e.printStackTrace();
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
                    System.out.println(String.format("%s :: %s",messageObject.getSender(),messageObject.getData()));
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
