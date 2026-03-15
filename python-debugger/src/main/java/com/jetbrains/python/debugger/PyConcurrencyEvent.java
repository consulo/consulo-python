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



public abstract class PyConcurrencyEvent
{
	protected final
	String myThreadId;
	protected final
	String myPid;
	protected final
	String myName;
	protected
	EventType myType;
	protected
	String myFileName;
	protected
	Integer myLine;
	protected final boolean myIsAsyncio;
	protected long myTime; // microseconds
	protected
	List<PyStackFrameInfo> myFrames;

	public enum EventType
	{
		CREATE, ACQUIRE_BEGIN, ACQUIRE_END, RELEASE, START, JOIN, STOP
	}

	public PyConcurrencyEvent(long time, String threadId, String name, boolean isAsyncio)
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

	public void setType(EventType type)
	{
		myType = type;
	}

	public String getThreadId()
	{
		return myThreadId;
	}

	public EventType getType()
	{
		return myType;
	}

	public String getThreadName()
	{
		return myName;
	}

	public abstract String getEventActionName();

	public abstract boolean isThreadEvent();

	public void setFileName(String fileName)
	{
		myFileName = fileName;
	}

	public String getFileName()
	{
		return myFileName;
	}

	public void setLine(Integer line)
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

	public Integer getLine()
	{
		return myLine;
	}

	public String getPid()
	{
		return myPid;
	}

	public List<PyStackFrameInfo> getFrames()
	{
		return myFrames;
	}

	public void setFrames(List<PyStackFrameInfo> frames)
	{
		myFrames = frames;
	}

	public boolean isAsyncio()
	{
		return myIsAsyncio;
	}
}
