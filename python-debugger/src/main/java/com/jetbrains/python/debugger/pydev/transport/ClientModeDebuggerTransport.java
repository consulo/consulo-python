package com.jetbrains.python.debugger.pydev.transport;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import consulo.logging.Logger;
import consulo.util.io.CharsetToolkit;
import com.jetbrains.python.debugger.IPyDebugProcess;
import com.jetbrains.python.debugger.PyDebuggerException;
import com.jetbrains.python.debugger.pydev.AbstractCommand;
import com.jetbrains.python.debugger.pydev.ProtocolFrame;
import com.jetbrains.python.debugger.pydev.RemoteDebugger;

/**
 * {@link DebuggerTransport} implementation that expects a debugging script to behave as a server. The main process of the debugging script
 * and all of the Python processes forked and created within it receives incoming connections on the <b>same port</b> using
 * {@code SO_REUSEPORT} server socket option on Linux (available since 3.9 core version), Mac OS, BSD platforms and {@code SO_REUSEADDR}
 * server socket option on Windows platforms (see {@code start_server(port)} method in <i>pydevd_comm.py</i>).
 * <p>
 * Each Python process within the debugging script requires <b>single connection</b> from the IDE. When a new Python process is created the
 * originator process sends {@link AbstractCommand#PROCESS_CREATED} message to the IDE. The new process binds server socket to the same
 * address and port as the originator process and starts listening for an incoming connection. The IDE tries to establish a new
 * connection with the script.
 * <p>
 * At the last point the following problem could arise. When several processes are created almost simultaneously and they become
 * bound to the single port the IDE could establish the connection to some of the processes twice or more times. The first connection is
 * accepted by the Python process but the others are not. Other connections would stay in <i>completed connection queue</i> until a timeout
 * for a response for the {@link AbstractCommand#RUN} arouse. To solve this problem {@link ClientModeDebuggerTransport} has several
 * states. The transport is created with {@link State#INIT}. After the connection is established the transport object is transferred to
 * {@link State#CONNECTED}. After the first message is received from the debugging script the transport is transferred to
 * {@link State#APPROVED}. After the transport is transferred to {@link State#CONNECTED} a task is scheduled in
 * {@link ClientModeDebuggerTransport#CHECK_CONNECTION_APPROVED_DELAY} to check if the state has been changed to {@link State#APPROVED}. If
 * the state had been changed then the current connection is kept and the normal communication with debugging script is performed. If the
 * state has not been changed for this period of time the transport tries to reconnect and schedules the check again.
 *
 * @author Alexander Koshevoy
 * @see ClientModeMultiProcessDebugger
 */
public class ClientModeDebuggerTransport extends BaseDebuggerTransport
{
	private static final Logger LOG = Logger.getInstance(ClientModeDebuggerTransport.class);

	private static final ScheduledExecutorService myScheduledExecutor = new ScheduledThreadPoolExecutor(1, new ThreadFactory()
	{
		private AtomicInteger num = new AtomicInteger(1);

		@Override
		public Thread newThread(@Nonnull Runnable r)
		{
			return new Thread(r, "Python Debug Script Connection " + num.getAndIncrement());
		}
	});

	private static final int MAX_CONNECTION_TRIES = 10;
	private static final long CHECK_CONNECTION_APPROVED_DELAY = 1000L;
	private static final long SLEEP_TIME_BETWEEN_CONNECTION_TRIES = 150L;

	@Nonnull
	private final IPyDebugProcess myDebugProcess;

	@Nonnull
	private final String myHost;
	private final int myPort;

	@Nonnull
	private volatile State myState = State.INIT;

	@Nullable
	private Socket mySocket;
	@Nullable
	private volatile DebuggerReader myDebuggerReader;

	public ClientModeDebuggerTransport(@Nonnull IPyDebugProcess debugProcess, @Nonnull RemoteDebugger debugger, @Nonnull String host, int port)
	{
		super(debugger);
		myDebugProcess = debugProcess;
		myHost = host;
		myPort = port;
	}

	@Override
	public void waitForConnect() throws IOException
	{
		try
		{
			Thread.sleep(500L);
		}
		catch(InterruptedException e)
		{
			throw new IOException(e);
		}

		if(myState != State.INIT)
		{
			throw new IllegalStateException("Inappropriate state of Python debugger for connecting to Python debugger: " + myState + "; " + State.INIT + " is expected");
		}

		doConnect();
	}

	private void doConnect() throws IOException
	{
		synchronized(mySocketObject)
		{
			if(mySocket != null)
			{
				try
				{
					mySocket.close();
				}
				catch(IOException e)
				{
					LOG.debug("Failed to close previously opened socket", e);
				}
				finally
				{
					mySocket = null;
				}
			}
		}

		int i = 0;
		boolean connected = false;
		while(!connected && i < MAX_CONNECTION_TRIES)
		{
			i++;
			try
			{
				Socket clientSocket = new Socket();
				clientSocket.setSoTimeout(0);
				clientSocket.connect(new InetSocketAddress(myHost, myPort));

				try
				{
					myDebuggerReader = new DebuggerReader(myDebugger, clientSocket.getInputStream());
				}
				catch(IOException e)
				{
					LOG.debug("Failed to create debugger reader", e);
					throw e;
				}

				synchronized(mySocketObject)
				{
					mySocket = clientSocket;
				}
				connected = true;
			}
			catch(ConnectException e)
			{
				if(i < MAX_CONNECTION_TRIES)
				{
					try
					{
						Thread.sleep(SLEEP_TIME_BETWEEN_CONNECTION_TRIES);
					}
					catch(InterruptedException e1)
					{
						throw new IOException(e1);
					}
				}
			}
		}

		if(!connected)
		{
			myState = State.DISCONNECTED;
			throw new IOException("Failed to connect to debugging script");
		}

		myState = State.CONNECTED;
		LOG.debug("Connected to Python debugger script on #" + i + " attempt");


		try
		{
			myDebugProcess.init();
			myDebugger.run();
		}
		catch(PyDebuggerException e)
		{
			myState = State.DISCONNECTED;
			throw new IOException("Failed to send run command", e);
		}

		myScheduledExecutor.schedule(() -> {
			if(myState == State.CONNECTED)
			{
				try
				{
					LOG.debug("Reconnecting...");
					doConnect();
				}
				catch(IOException e)
				{
					LOG.debug(e);
					myDebugger.fireCommunicationError();
				}
			}
		}, CHECK_CONNECTION_APPROVED_DELAY, TimeUnit.MILLISECONDS);
	}

	@Override
	protected boolean sendMessageImpl(byte[] packed) throws IOException
	{
		synchronized(mySocketObject)
		{
			if(mySocket == null)
			{
				return false;
			}
			final OutputStream os = mySocket.getOutputStream();
			os.write(packed);
			os.flush();
			return true;
		}
	}

	@Override
	public void close()
	{
		try
		{
			DebuggerReader debuggerReader = myDebuggerReader;
			if(debuggerReader != null)
			{
				debuggerReader.stop();
			}
		}
		finally
		{
			synchronized(mySocketObject)
			{
				if(mySocket != null)
				{
					try
					{
						mySocket.close();
					}
					catch(IOException ignored)
					{
					}
				}
			}
		}
	}

	@Override
	public boolean isConnected()
	{
		return myState == State.APPROVED;
	}

	@Override
	public void disconnect()
	{
		// TODO disconnect?
	}

	@Override
	public void messageReceived(@Nonnull ProtocolFrame frame)
	{
		if(myState == State.CONNECTED)
		{
			myState = State.APPROVED;
		}
	}

	private enum State
	{
		/**
		 * Before calling {@link #waitForConnect()}
		 */
		INIT,
		/**
		 * Socket connection to the debugger host:port address established and no messages has been received from the debugging script yet.
		 * The connection might be ephemeral at this point (see {@link ClientModeDebuggerTransport}).
		 */
		CONNECTED,
		/**
		 * Socket connection to the debugger host:port address established and at least one message has been received from the debugging script.
		 * This state means that a script is on the other end had accepted the connection.
		 */
		APPROVED,
		/**
		 * Debugger disconnected
		 */
		DISCONNECTED
	}

	public class DebuggerReader extends BaseDebuggerReader
	{
		public DebuggerReader(@Nonnull RemoteDebugger debugger, @Nonnull InputStream stream) throws IOException
		{
			super(stream, CharsetToolkit.UTF8_CHARSET, debugger); //TODO: correct encoding?
			start(getClass().getName());
		}

		protected void onCommunicationError()
		{
			if(myState == State.APPROVED)
			{
				getDebugger().fireCommunicationError();
			}
		}
	}
}
