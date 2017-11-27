package com.distributed.phase2.components;

import java.io.Serializable;
import java.net.InetSocketAddress;

public class PeerAllocationRequest implements Serializable {
    public PeerAllocationRequest(String peerName, InetSocketAddress peerAddress) {
        this.peerName = peerName;
        this.peerAddress = peerAddress;
    }

    public String getPeerName() {
        return peerName;
    }

    public InetSocketAddress getPeerAddress() {
        return peerAddress;
    }

    private String peerName;
    private InetSocketAddress peerAddress;
}
