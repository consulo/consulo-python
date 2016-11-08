package com.jetbrains.python.debugger.pydev.transport;

import java.io.IOException;

import org.jetbrains.annotations.NotNull;
import com.jetbrains.python.debugger.pydev.ProtocolFrame;

/**
 * @author Alexander Koshevoy
 */
public interface DebuggerTransport
{
	void waitForConnect() throws IOException;

	void close();

	boolean sendFrame(@NotNull ProtocolFrame frame);

	boolean isConnected();

	void disconnect();

	void messageReceived(@NotNull ProtocolFrame frame);
}
