package com.jetbrains.python.debugger.pydev.transport;

import java.io.IOException;
import java.net.SocketException;
import java.util.Date;

import jakarta.annotation.Nonnull;

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

	@Nonnull
	protected final RemoteDebugger myDebugger;

	protected BaseDebuggerTransport(@Nonnull RemoteDebugger debugger)
	{
		myDebugger = debugger;
	}

	@Override
	public boolean sendFrame(@Nonnull final ProtocolFrame frame)
	{
		logFrame(frame, true);

		try
		{
			final byte[] packed = frame.pack();
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
