package com.jetbrains.python.console.transport;

public class DirectedMessage {
    public enum MessageDirection {
        REQUEST,
        RESPONSE
    }

    private final MessageDirection myDirection;
    private final byte[] myContent;

    public DirectedMessage(MessageDirection direction, byte[] content) {
        myDirection = direction;
        myContent = content;
    }

    public MessageDirection getDirection() {
        return myDirection;
    }

    public byte[] getContent() {
        return myContent;
    }
}
