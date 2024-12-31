package com.jetbrains.python.debugger;


import jakarta.annotation.Nonnull;

public class PyThreadEvent extends PyConcurrencyEvent
{
	private final
	@Nonnull
	String myParentThreadId;

	public PyThreadEvent(Long time, @Nonnull String threadId, @Nonnull String name, boolean isAsyncio)
	{
		super(time, threadId, name, isAsyncio);
		myParentThreadId = "";
	}

	public PyThreadEvent(long time, @Nonnull String threadId, @Nonnull String name, @Nonnull String parentThreadId, boolean isAsyncio)
	{
		super(time, threadId, name, isAsyncio);
		myParentThreadId = parentThreadId;
	}

	@Nonnull
	@Override
	public String getEventActionName()
	{
		StringBuilder sb = new StringBuilder();
		switch(myType)
		{
			case CREATE:
				sb.append(" created");
				break;
			case START:
				sb.append(" started");
				break;
			case JOIN:
				sb.append(" called join");
				break;
			case STOP:
				sb.append(" stopped");
				break;
			default:
				sb.append(" unknown command");
		}
		return sb.toString();
	}

	@Nonnull
	public String getParentThreadId()
	{
		return myParentThreadId;
	}

	@Override
	public boolean isThreadEvent()
	{
		return true;
	}

	@Override
	public String toString()
	{
		return myTime + " " + myThreadId + " PyThreadEvent" +
				" myType=" + myType +
				" " + myFileName +
				" " + myLine +
				"<br>";
	}
}
