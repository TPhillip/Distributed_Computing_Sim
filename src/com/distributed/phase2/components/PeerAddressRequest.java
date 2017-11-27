package com.distributed.phase2.components;

import java.io.Serializable;
import java.net.InetSocketAddress;

public class PeerAddressRequest implements Serializable {

    public PeerAddressRequest(String clientName, InetSocketAddress requesterAddress){
        this.clientName = clientName;
        this.requesterAddress = requesterAddress;
    }

    public String getClientName() {
        return clientName;
    }

    public InetSocketAddress getRequesterAddress() {
        return requesterAddress;
    }

    private String clientName;
    private InetSocketAddress requesterAddress;
}
