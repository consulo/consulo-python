package com.jetbrains.python.console.transport;

import consulo.logging.Logger;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.UncheckedIOException;

public abstract class TCumulativeTransport extends TTransport {
    public static final Logger LOG = Logger.getInstance(TCumulativeTransport.class);

    private final PipedOutputStream myOutputStream = new PipedOutputStream();

    private final PipedInputStream myPipedInputStream;

    private final ByteBuf myMessageBuffer = Unpooled.buffer();

    protected TCumulativeTransport() {
        try {
            myPipedInputStream = new PipedInputStream(myOutputStream);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public PipedOutputStream getOutputStream() {
        return myOutputStream;
    }

    @Override
    public void open() {
    }

    @Override
    public final void write(byte[] buf, int off, int len) {
        myMessageBuffer.writeBytes(buf, off, len);
    }

    @Override
    public final void flush() {
        int length = myMessageBuffer.readableBytes();
        byte[] content = new byte[length];
        myMessageBuffer.readBytes(content);
        myMessageBuffer.clear();

        writeMessage(content);
    }

    public abstract void writeMessage(byte[] content);

    @Override
    public void close() {
        LOG.debug("Closing cumulative transport");

        try {
            myOutputStream.close();
            myPipedInputStream.close();
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public final int read(byte[] buf, int off, int len) throws TTransportException {
        try {
            return myPipedInputStream.read(buf, off, len);
        }
        catch (IOException e) {
            throw new TTransportException(TTransportException.UNKNOWN, e);
        }
    }
}
