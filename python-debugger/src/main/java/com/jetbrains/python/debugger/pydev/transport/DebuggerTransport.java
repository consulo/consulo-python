package com.jetbrains.python.debugger.pydev.transport;

import com.jetbrains.python.debugger.pydev.ProtocolFrame;

import java.io.IOException;

/**
 * @author Alexander Koshevoy
 */
public interface DebuggerTransport {
    void waitForConnect() throws IOException;

    void close();

    boolean sendFrame(ProtocolFrame frame);

    /**
     * Returns {@code true} if IDE is in process of connecting to the debugger
     * and {@code false} otherwise.
     */
    boolean isConnecting();

    boolean isConnected();

    void disconnect();
}
