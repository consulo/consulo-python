package com.jetbrains.python.debugger.pydev.transport;

import java.io.IOException;

import jakarta.annotation.Nonnull;

import com.jetbrains.python.debugger.pydev.ProtocolFrame;

/**
 * @author Alexander Koshevoy
 */
public interface DebuggerTransport
{
	void waitForConnect() throws IOException;

	void close();

	boolean sendFrame(@Nonnull ProtocolFrame frame);

	boolean isConnected();

	void disconnect();

	void messageReceived(@Nonnull ProtocolFrame frame);
}
