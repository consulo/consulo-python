/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.debugger;

import java.util.ArrayList;
import java.util.List;

import jakarta.annotation.Nonnull;


public abstract class PyConcurrencyEvent
{
	protected final
	@Nonnull
	String myThreadId;
	protected final
	@Nonnull
	String myPid;
	protected final
	@Nonnull
	String myName;
	protected
	@Nonnull
	EventType myType;
	protected
	@Nonnull
	String myFileName;
	protected
	@Nonnull
	Integer myLine;
	protected final boolean myIsAsyncio;
	protected long myTime; // microseconds
	protected
	@Nonnull
	List<PyStackFrameInfo> myFrames;

	public enum EventType
	{
		CREATE, ACQUIRE_BEGIN, ACQUIRE_END, RELEASE, START, JOIN, STOP
	}

	public PyConcurrencyEvent(long time, @Nonnull String threadId, @Nonnull String name, boolean isAsyncio)
	{
		myTime = time;
		myThreadId = threadId;
		myPid = threadId.split("_")[0];
		myName = name;
		myIsAsyncio = isAsyncio;
		myType = EventType.CREATE;
		myFileName = "";
		myLine = 0;
		myFrames = new ArrayList<>();
	}

	public void setType(@Nonnull EventType type)
	{
		myType = type;
	}

	@Nonnull
	public String getThreadId()
	{
		return myThreadId;
	}

	@Nonnull
	public EventType getType()
	{
		return myType;
	}

	@Nonnull
	public String getThreadName()
	{
		return myName;
	}

	@Nonnull
	public abstract String getEventActionName();

	public abstract boolean isThreadEvent();

	public void setFileName(@Nonnull String fileName)
	{
		myFileName = fileName;
	}

	@Nonnull
	public String getFileName()
	{
		return myFileName;
	}

	public void setLine(@Nonnull Integer line)
	{
		myLine = line;
	}

	public long getTime()
	{
		return myTime;
	}

	public void setTime(long time)
	{
		myTime = time;
	}

	@Nonnull
	public Integer getLine()
	{
		return myLine;
	}

	@Nonnull
	public String getPid()
	{
		return myPid;
	}

	@Nonnull
	public List<PyStackFrameInfo> getFrames()
	{
		return myFrames;
	}

	public void setFrames(@Nonnull List<PyStackFrameInfo> frames)
	{
		myFrames = frames;
	}

	public boolean isAsyncio()
	{
		return myIsAsyncio;
	}
}
