package com.jetbrains.python.debugger.pydev.transport;

import java.io.IOException;
import java.net.SocketException;
import java.util.Date;


import consulo.logging.Logger;
import com.jetbrains.python.debugger.pydev.ProtocolFrame;
import com.jetbrains.python.debugger.pydev.RemoteDebugger;

/**
 * @author Alexander Koshevoy
 */
public abstract class BaseDebuggerTransport implements DebuggerTransport
{
	private static final Logger LOG = Logger.getInstance(BaseDebuggerTransport.class);

	protected final Object mySocketObject = new Object();

	protected final RemoteDebugger myDebugger;

	protected BaseDebuggerTransport(RemoteDebugger debugger)
	{
		myDebugger = debugger;
	}

	@Override
	public boolean sendFrame(ProtocolFrame frame)
	{
		logFrame(frame, true);

		try
		{
			byte[] packed = frame.pack();
			return sendMessageImpl(packed);
		}
		catch(SocketException se)
		{
			myDebugger.disconnect();
			myDebugger.fireCommunicationError();
		}
		catch(IOException e)
		{
			LOG.error(e);
		}
		return false;
	}

	protected abstract boolean sendMessageImpl(byte[] packed) throws IOException;

	public static void logFrame(ProtocolFrame frame, boolean out)
	{
		if(LOG.isDebugEnabled())
		{
			LOG.debug(String.format("%1$tH:%1$tM:%1$tS.%1$tL %2$s %3$s\n", new Date(), (out ? "<<<" : ">>>"), frame));
		}
	}
}
