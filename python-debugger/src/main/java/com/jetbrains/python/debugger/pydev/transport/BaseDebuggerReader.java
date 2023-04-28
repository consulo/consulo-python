package com.jetbrains.python.debugger.pydev.transport;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.concurrent.Future;

import javax.annotation.Nonnull;

import consulo.application.ApplicationManager;
import consulo.logging.Logger;
import consulo.util.lang.TimeoutUtil;
import consulo.process.io.BaseOutputReader;
import com.jetbrains.python.debugger.pydev.RemoteDebugger;

/**
 * @author Alexander Koshevoy
 */
public abstract class BaseDebuggerReader extends BaseOutputReader
{
	private static final Logger LOG = Logger.getInstance(BaseDebuggerReader.class);

	@Nonnull
	private final RemoteDebugger myDebugger;
	@Nonnull
	private StringBuilder myTextBuilder = new StringBuilder();

	public BaseDebuggerReader(@Nonnull InputStream inputStream, @Nonnull Charset charset, @Nonnull RemoteDebugger debugger)
	{
		super(inputStream, charset);
		myDebugger = debugger;
	}

	@Nonnull
	protected RemoteDebugger getDebugger()
	{
		return myDebugger;
	}

	protected void doRun()
	{
		try
		{
			while(true)
			{
				boolean read = readAvailableBlocking();

				if(!read)
				{
					break;
				}
				else
				{
					if(isStopped)
					{
						break;
					}

					TimeoutUtil.sleep(mySleepingPolicy.getTimeToSleep(true));
				}
			}
		}
		catch(Exception e)
		{
			onCommunicationError();
		}
		finally
		{
			close();
			myDebugger.fireExitEvent();
		}
	}

	protected abstract void onCommunicationError();

	@Nonnull
	@Override
	protected Future<?> executeOnPooledThread(@Nonnull Runnable runnable)
	{
		return ApplicationManager.getApplication().executeOnPooledThread(runnable);
	}

	@Override
	protected void close()
	{
		try
		{
			super.close();
		}
		catch(IOException e)
		{
			LOG.error(e);
		}
	}

	@Override
	public void stop()
	{
		super.stop();
		close();
	}

	@Override
	protected void onTextAvailable(@Nonnull String text)
	{
		myTextBuilder.append(text);
		if(text.contains("\n"))
		{
			String[] lines = myTextBuilder.toString().split("\n");
			myTextBuilder = new StringBuilder();

			if(!text.endsWith("\n"))
			{
				myTextBuilder.append(lines[lines.length - 1]);
				lines = Arrays.copyOfRange(lines, 0, lines.length - 1);
			}

			for(String line : lines)
			{
				myDebugger.processResponse(line + "\n");
			}
		}
	}
}
