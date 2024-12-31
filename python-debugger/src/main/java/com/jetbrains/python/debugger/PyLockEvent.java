package com.jetbrains.python.debugger;


import jakarta.annotation.Nonnull;

public class PyLockEvent extends PyConcurrencyEvent
{
	private final
	@Nonnull
	String myLockId;

	public PyLockEvent(long time, @Nonnull String threadId, @Nonnull String name, @Nonnull String id, boolean isAsyncio)
	{
		super(time, threadId, name, isAsyncio);
		myLockId = id;
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
			case ACQUIRE_BEGIN:
				sb.append(" acquire started");
				break;
			case ACQUIRE_END:
				sb.append(" acquired");
				break;
			case RELEASE:
				sb.append(" released");
				break;
			default:
				sb.append(" unknown command");
		}
		return sb.toString();
	}

	@Nonnull
	public String getLockId()
	{
		return myLockId;
	}

	@Override
	public boolean isThreadEvent()
	{
		return false;
	}

	@Override
	public String toString()
	{
		return myTime + " " + myThreadId + " PyLockEvent" +
				" myType=" + myType +
				" myLockId= " + myLockId +
				" " + myFileName +
				" " + myLine +
				"<br>";
	}
}
