package com.distributed.phase2.components;

import java.io.Serializable;

public class MessageObject implements Serializable {
    private String sender;
    private String data;
    private byte[] fileBytes;
    private String fileName;


    public MessageObject(String sender, byte[] fileBytes, String fileName) {
        this.sender = sender;
        this.fileBytes = fileBytes;
        this.fileName = fileName;
    }

    public MessageObject(String sender, String data) {
        this.sender = sender;
        this.data = data;
    }

    public byte[] getFileBytes() {
        return fileBytes;
    }

    public String getFileName() {
        return fileName;
    }

    public String getSender() {
        return sender;
    }

    public String getData() {
        return data;
    }

    public boolean containsFile() {
        return fileBytes != null;
    }

    public boolean containsMessage() {
        return data != null;
    }
}
