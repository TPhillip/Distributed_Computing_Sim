package com.distributed.phase2.components;

import java.io.Serializable;

public class MessageObject implements Serializable {
    private String sender;
    private String data;

    public MessageObject(String sender, String data) {
        this.sender = sender;
        this.data = data;
    }

    public String getSender() {
        return sender;
    }

    public String getData() {
        return data;
    }
}
