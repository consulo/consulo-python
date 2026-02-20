/*
 * Author: atotic
 * Created on Mar 23, 2004
 * License: Common Public License v1.0
 */
package com.jetbrains.python.debugger.pydev;

import com.google.common.collect.Maps;
import com.jetbrains.python.console.pydev.PydevCompletionVariant;
import com.jetbrains.python.debugger.*;
import com.jetbrains.python.debugger.pydev.transport.ClientModeDebuggerTransport;
import com.jetbrains.python.debugger.pydev.transport.DebuggerTransport;
import com.jetbrains.python.debugger.pydev.transport.ServerModeDebuggerTransport;
import consulo.application.util.SystemInfo;
import consulo.execution.debug.breakpoint.SuspendPolicy;
import consulo.execution.debug.frame.XValueChildrenList;
import consulo.execution.ui.console.ConsoleViewContentType;
import consulo.logging.Logger;
import consulo.util.collection.Lists;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.net.ServerSocket;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.jetbrains.python.debugger.pydev.transport.BaseDebuggerTransport.logFrame;


public class RemoteDebugger implements ProcessDebugger
{
	private static final int RESPONSE_TIMEOUT = 60000;

	private static final Logger LOG = Logger.getInstance("#com.jetbrains.python.pydev.remote.RemoteDebugger");

	private static final String LOCAL_VERSION = "0.1";
	public static final String TEMP_VAR_PREFIX = "__py_debug_temp_var_";

	private static final SecureRandom ourRandom = new SecureRandom();

	private final IPyDebugProcess myDebugProcess;

	private int mySequence = -1;
	private final Object mySequenceObject = new Object(); // for synchronization on mySequence
	private final Map<String, PyThreadInfo> myThreads = new ConcurrentHashMap<>();
	private final Map<Integer, ProtocolFrame> myResponseQueue = new HashMap<>();
	private final TempVarsHolder myTempVars = new TempVarsHolder();

	private Map<Pair<String, Integer>, String> myTempBreakpoints = Maps.newHashMap();


	private final List<RemoteDebuggerCloseListener> myCloseListeners = Lists.newLockFreeCopyOnWriteList();

	@Nonnull
	private final DebuggerTransport myDebuggerTransport;

	public RemoteDebugger(@Nonnull IPyDebugProcess debugProcess, @Nonnull String host, int port)
	{
		myDebugProcess = debugProcess;
		myDebuggerTransport = new ClientModeDebuggerTransport(debugProcess, this, host, port);
	}

	public RemoteDebugger(@Nonnull IPyDebugProcess debugProcess, @Nonnull ServerSocket socket, int timeout)
	{
		myDebugProcess = debugProcess;
		myDebuggerTransport = new ServerModeDebuggerTransport(this, socket, timeout);
	}

	protected RemoteDebugger(@Nonnull IPyDebugProcess debugProcess, @Nonnull DebuggerTransport debuggerTransport)
	{
		myDebugProcess = debugProcess;
		myDebuggerTransport = debuggerTransport;
	}

	public IPyDebugProcess getDebugProcess()
	{
		return myDebugProcess;
	}

	@Override
	public boolean isConnected()
	{
		return myDebuggerTransport.isConnected();
	}

	@Override
	public void waitForConnect() throws Exception
	{
		myDebuggerTransport.waitForConnect();
	}

	private void writeToConsole(PyIo io)
	{
		ConsoleViewContentType contentType;
		if(io.getCtx() == 2)
		{
			contentType = ConsoleViewContentType.ERROR_OUTPUT;
		}
		else
		{
			contentType = ConsoleViewContentType.NORMAL_OUTPUT;
		}
		myDebugProcess.printToConsole(io.getText(), contentType);
	}

	@Override
	public String handshake() throws PyDebuggerException
	{
		VersionCommand command = new VersionCommand(this, LOCAL_VERSION, SystemInfo.isUnix ? "UNIX" : "WIN");
		command.execute();
		String version = command.getRemoteVersion();
		if(version != null)
		{
			version = version.trim();
		}
		return version;
	}

	@Override
	public PyDebugValue evaluate(String threadId, String frameId, String expression, boolean execute) throws PyDebuggerException
	{
		return evaluate(threadId, frameId, expression, execute, true);
	}


	@Override
	public PyDebugValue evaluate(String threadId, String frameId, String expression, boolean execute, boolean trimResult) throws PyDebuggerException
	{
		EvaluateCommand command = new EvaluateCommand(this, threadId, frameId, expression, execute, trimResult);
		command.execute();
		return command.getValue();
	}

	@Override
	public void consoleExec(String threadId, String frameId, String expression, PyDebugCallback<String> callback)
	{
		ConsoleExecCommand command = new ConsoleExecCommand(this, threadId, frameId, expression);
		command.execute(callback);
	}

	@Override
	public XValueChildrenList loadFrame(String threadId, String frameId) throws PyDebuggerException
	{
		GetFrameCommand command = new GetFrameCommand(this, threadId, frameId);
		command.execute();
		return command.getVariables();
	}

	// todo: don't generate temp variables for qualified expressions - just split 'em
	@Override
	public XValueChildrenList loadVariable(String threadId, String frameId, PyDebugValue var) throws PyDebuggerException
	{
		setTempVariable(threadId, frameId, var);
		GetVariableCommand command = new GetVariableCommand(this, threadId, frameId, var);
		command.execute();
		return command.getVariables();
	}

	@Override
	public ArrayChunk loadArrayItems(String threadId, String frameId, PyDebugValue var, int rowOffset, int colOffset, int rows, int cols, String format) throws PyDebuggerException
	{
		GetArrayCommand command = new GetArrayCommand(this, threadId, frameId, var, rowOffset, colOffset, rows, cols, format);
		command.execute();
		return command.getArray();
	}


	@Override
	public void loadReferrers(String threadId, String frameId, PyReferringObjectsValue var, final PyDebugCallback<XValueChildrenList> callback)
	{
		RunCustomOperationCommand cmd = new GetReferrersCommand(this, threadId, frameId, var);

		cmd.execute(new PyDebugCallback<List<PyDebugValue>>()
		{
			@Override
			public void ok(List<PyDebugValue> value)
			{
				XValueChildrenList list = new XValueChildrenList();
				for(PyDebugValue v : value)
				{
					list.add(v);
				}
				callback.ok(list);
			}

			@Override
			public void error(PyDebuggerException exception)
			{
				callback.error(exception);
			}
		});
	}

	@Override
	public PyDebugValue changeVariable(String threadId, String frameId, PyDebugValue var, String value) throws PyDebuggerException
	{
		setTempVariable(threadId, frameId, var);
		return doChangeVariable(threadId, frameId, var.getEvaluationExpression(), value);
	}

	private PyDebugValue doChangeVariable(String threadId, String frameId, String varName, String value) throws PyDebuggerException
	{
		ChangeVariableCommand command = new ChangeVariableCommand(this, threadId, frameId, varName, value);
		command.execute();
		return command.getNewValue();
	}

	@Override
	@Nullable
	public String loadSource(String path)
	{
		LoadSourceCommand command = new LoadSourceCommand(this, path);
		try
		{
			command.execute();
			return command.getContent();
		}
		catch(PyDebuggerException e)
		{
			return "#Couldn't load source of file " + path;
		}
	}

	private void cleanUp()
	{
		myThreads.clear();
		myResponseQueue.clear();
		synchronized(mySequenceObject)
		{
			mySequence = -1;
		}
		myTempVars.clear();
	}

	// todo: change variable in lists doesn't work - either fix in pydevd or format var name appropriately
	private void setTempVariable(String threadId, String frameId, PyDebugValue var)
	{
		PyDebugValue topVar = var.getTopParent();
		if(!myDebugProcess.canSaveToTemp(topVar.getName()))
		{
			return;
		}
		if(myTempVars.contains(threadId, frameId, topVar.getTempName()))
		{
			return;
		}

		topVar.setTempName(generateTempName());
		try
		{
			doChangeVariable(threadId, frameId, topVar.getTempName(), topVar.getName());
			myTempVars.put(threadId, frameId, topVar.getTempName());
		}
		catch(PyDebuggerException e)
		{
			LOG.error(e);
			topVar.setTempName(null);
		}
	}

	public String generateSaveTempName(String threadId, String frameId)
	{
		String tempName = generateTempName();
		myTempVars.put(threadId, frameId, tempName);
		return tempName;
	}

	private void clearTempVariables(String threadId)
	{
		Map<String, Set<String>> threadVars = myTempVars.get(threadId);
		if(threadVars == null || threadVars.size() == 0)
		{
			return;
		}

		for(Map.Entry<String, Set<String>> entry : threadVars.entrySet())
		{
			Set<String> frameVars = entry.getValue();
			if(frameVars == null || frameVars.size() == 0)
			{
				continue;
			}

			String expression = "del " + StringUtil.join(frameVars, ",");
			try
			{
				evaluate(threadId, entry.getKey(), expression, true);
			}
			catch(PyDebuggerException e)
			{
				LOG.error(e);
			}
		}

		myTempVars.clear(threadId);
	}

	private static String generateTempName()
	{
		return TEMP_VAR_PREFIX + ourRandom.nextInt(Integer.MAX_VALUE);
	}

	@Override
	public Collection<PyThreadInfo> getThreads()
	{
		return Collections.unmodifiableCollection(new ArrayList<>(myThreads.values()));
	}

	int getNextSequence()
	{
		synchronized(mySequenceObject)
		{
			mySequence += 2;
			return mySequence;
		}
	}

	void placeResponse(int sequence, ProtocolFrame response)
	{
		synchronized(myResponseQueue)
		{
			if(response == null || myResponseQueue.containsKey(sequence))
			{
				myResponseQueue.put(sequence, response);
			}
			if(response != null)
			{
				myResponseQueue.notifyAll();
			}
		}
	}

	@Nullable
	ProtocolFrame waitForResponse(int sequence)
	{
		ProtocolFrame response;
		long until = System.currentTimeMillis() + RESPONSE_TIMEOUT;

		synchronized(myResponseQueue)
		{
			do
			{
				try
				{
					myResponseQueue.wait(1000);
				}
				catch(InterruptedException ignore)
				{
				}
				response = myResponseQueue.get(sequence);
			}
			while(response == null && isConnected() && System.currentTimeMillis() < until);
			myResponseQueue.remove(sequence);
		}

		return response;
	}

	@Override
	public void execute(@Nonnull AbstractCommand command)
	{
		if(command instanceof ResumeOrStepCommand)
		{
			String threadId = ((ResumeOrStepCommand) command).getThreadId();
			clearTempVariables(threadId);
		}

		try
		{
			command.execute();
		}
		catch(PyDebuggerException e)
		{
			LOG.error(e);
		}
	}

	boolean sendFrame(ProtocolFrame frame)
	{
		return myDebuggerTransport.sendFrame(frame);
	}

	@Override
	public void suspendAllThreads()
	{
		for(PyThreadInfo thread : getThreads())
		{
			suspendThread(thread.getId());
		}
	}


	@Override
	public void suspendThread(String threadId)
	{
		SuspendCommand command = new SuspendCommand(this, threadId);
		execute(command);
	}

	@Override
	public void close()
	{
		myDebuggerTransport.close();
		fireCloseEvent();
	}

	@Override
	public void disconnect()
	{
		myDebuggerTransport.disconnect();

		cleanUp();
	}

	@Override
	public void run() throws PyDebuggerException
	{
		new RunCommand(this).execute();
	}

	@Override
	public void smartStepInto(String threadId, String functionName)
	{
		SmartStepIntoCommand command = new SmartStepIntoCommand(this, threadId, functionName);
		execute(command);
	}

	@Override
	public void resumeOrStep(String threadId, ResumeOrStepCommand.Mode mode)
	{
		ResumeOrStepCommand command = new ResumeOrStepCommand(this, threadId, mode);
		execute(command);
	}

	@Override
	public void setTempBreakpoint(@Nonnull String type, @Nonnull String file, int line)
	{
		SetBreakpointCommand command = new SetBreakpointCommand(this, type, file, line);
		execute(command);  // set temp. breakpoint
		myTempBreakpoints.put(Pair.create(file, line), type);
	}

	@Override
	public void removeTempBreakpoint(@Nonnull String file, int line)
	{
		String type = myTempBreakpoints.get(Pair.create(file, line));
		if(type != null)
		{
			RemoveBreakpointCommand command = new RemoveBreakpointCommand(this, type, file, line);
			execute(command);  // remove temp. breakpoint
		}
		else
		{
			LOG.error("Temp breakpoint not found for " + file + ":" + line);
		}
	}

	@Override
	public void setBreakpoint(@Nonnull String typeId,
			@Nonnull String file,
			int line,
			@Nullable String condition,
			@Nullable String logExpression,
			@Nullable String funcName,
			@Nonnull SuspendPolicy policy)
	{
		SetBreakpointCommand command = new SetBreakpointCommand(this, typeId, file, line, condition, logExpression, funcName, policy);
		execute(command);
	}


	@Override
	public void removeBreakpoint(@Nonnull String typeId, @Nonnull String file, int line)
	{
		RemoveBreakpointCommand command = new RemoveBreakpointCommand(this, typeId, file, line);
		execute(command);
	}

	@Override
	public void setShowReturnValues(boolean isShowReturnValues)
	{
		ShowReturnValuesCommand command = new ShowReturnValuesCommand(this, isShowReturnValues);
		execute(command);
	}

	// for DebuggerReader only
	public void processResponse(@Nonnull String line)
	{
		try
		{
			ProtocolFrame frame = new ProtocolFrame(line);
			logFrame(frame, false);

			myDebuggerTransport.messageReceived(frame);

			if(AbstractThreadCommand.isThreadCommand(frame.getCommand()))
			{
				processThreadEvent(frame);
			}
			else if(AbstractCommand.isWriteToConsole(frame.getCommand()))
			{
				writeToConsole(ProtocolParser.parseIo(frame.getPayload()));
			}
			else if(AbstractCommand.isExitEvent(frame.getCommand()))
			{
				fireCommunicationError();
			}
			else if(AbstractCommand.isCallSignatureTrace(frame.getCommand()))
			{
				recordCallSignature(ProtocolParser.parseCallSignature(frame.getPayload()));
			}
			else if(AbstractCommand.isConcurrencyEvent(frame.getCommand()))
			{
				recordConcurrencyEvent(ProtocolParser.parseConcurrencyEvent(frame.getPayload(), myDebugProcess.getPositionConverter()));
			}
			else if(AbstractCommand.isInputRequested(frame.getCommand()))
			{
				myDebugProcess.consoleInputRequested(ProtocolParser.parseInputCommand(frame.getPayload()));
			}
			else if(ProcessCreatedCommand.isProcessCreatedCommand(frame.getCommand()))
			{
				onProcessCreatedEvent();
			}
			else
			{
				placeResponse(frame.getSequence(), frame);
			}
		}
		catch(Throwable t)
		{
			// shouldn't interrupt reader thread
			LOG.error(t);
		}
	}

	private void recordCallSignature(PySignature signature)
	{
		myDebugProcess.recordSignature(signature);
	}

	private void recordConcurrencyEvent(PyConcurrencyEvent event)
	{
		myDebugProcess.recordLogEvent(event);
	}

	// todo: extract response processing
	private void processThreadEvent(ProtocolFrame frame) throws PyDebuggerException
	{
		switch(frame.getCommand())
		{
			case AbstractCommand.CREATE_THREAD:
			{
				PyThreadInfo thread = parseThreadEvent(frame);
				if(!thread.isPydevThread())
				{  // ignore pydevd threads
					myThreads.put(thread.getId(), thread);
					if(myDebugProcess.getSession().isSuspended() && myDebugProcess.isSuspendedOnAllThreadsPolicy())
					{
						// Sometimes the notification about new threads may come slow from the Python side. We should check if
						// the current session is suspended in the "Suspend all threads" mode and suspend new thread, which hasn't been suspended
						suspendThread(thread.getId());
					}
				}
				break;
			}
			case AbstractCommand.SUSPEND_THREAD:
			{
				PyThreadInfo event = parseThreadEvent(frame);
				PyThreadInfo thread = myThreads.get(event.getId());
				if(thread == null)
				{
					LOG.error("Trying to stop on non-existent thread: " + event.getId() + ", " + event.getStopReason() + ", " + event.getMessage());
					myThreads.put(event.getId(), event);
					thread = event;
				}
				thread.updateState(PyThreadInfo.State.SUSPENDED, event.getFrames());
				thread.setStopReason(event.getStopReason());
				thread.setMessage(event.getMessage());
				boolean updateSourcePosition = true;
				if(event.getStopReason() == AbstractCommand.SUSPEND_THREAD)
				{
					// That means that the thread was stopped manually from the Java side either while suspending all threads
					// or after the "Pause" command. In both cases we shouldn't change debugger focus if session is already suspended.
					updateSourcePosition = !myDebugProcess.getSession().isSuspended();
				}
				myDebugProcess.threadSuspended(thread, updateSourcePosition);
				break;
			}
			case AbstractCommand.RESUME_THREAD:
			{
				String id = ProtocolParser.getThreadId(frame.getPayload());
				PyThreadInfo thread = myThreads.get(id);
				if(thread != null)
				{
					thread.updateState(PyThreadInfo.State.RUNNING, null);
					myDebugProcess.threadResumed(thread);
				}
				break;
			}
			case AbstractCommand.KILL_THREAD:
			{
				String id = frame.getPayload();
				PyThreadInfo thread = myThreads.get(id);
				if(thread != null)
				{
					thread.updateState(PyThreadInfo.State.KILLED, null);
					myThreads.remove(id);
				}
				if(myDebugProcess.getSession().getCurrentPosition() == null)
				{
					for(PyThreadInfo threadInfo : myThreads.values())
					{
						// notify UI of suspended threads left in debugger if one thread finished its work
						if((threadInfo != null) && (threadInfo.getState() == PyThreadInfo.State.SUSPENDED))
						{
							myDebugProcess.threadResumed(threadInfo);
							myDebugProcess.threadSuspended(threadInfo, true);
						}
					}
				}
				break;
			}
			case AbstractCommand.SHOW_CONSOLE:
			{
				PyThreadInfo event = parseThreadEvent(frame);
				PyThreadInfo thread = myThreads.get(event.getId());
				if(thread == null)
				{
					myThreads.put(event.getId(), event);
					thread = event;
				}
				thread.updateState(PyThreadInfo.State.SUSPENDED, event.getFrames());
				thread.setStopReason(event.getStopReason());
				thread.setMessage(event.getMessage());
				myDebugProcess.showConsole(thread);
				break;
			}
		}
	}

	private PyThreadInfo parseThreadEvent(ProtocolFrame frame) throws PyDebuggerException
	{
		return ProtocolParser.parseThread(frame.getPayload(), myDebugProcess.getPositionConverter());
	}

	private static class TempVarsHolder
	{
		private final Map<String, Map<String, Set<String>>> myData = new HashMap<>();

		public boolean contains(String threadId, String frameId, String name)
		{
			Map<String, Set<String>> threadVars = myData.get(threadId);
			if(threadVars == null)
			{
				return false;
			}

			Set<String> frameVars = threadVars.get(frameId);
			if(frameVars == null)
			{
				return false;
			}

			return frameVars.contains(name);
		}

		protected void put(String threadId, String frameId, String name)
		{
			Map<String, Set<String>> threadVars = myData.get(threadId);
			if(threadVars == null)
			{
				myData.put(threadId, (threadVars = new HashMap<>()));
			}

			Set<String> frameVars = threadVars.get(frameId);
			if(frameVars == null)
			{
				threadVars.put(frameId, (frameVars = new HashSet<>()));
			}

			frameVars.add(name);
		}

		protected Map<String, Set<String>> get(String threadId)
		{
			return myData.get(threadId);
		}

		protected void clear()
		{
			myData.clear();
		}

		protected void clear(String threadId)
		{
			Map<String, Set<String>> threadVars = myData.get(threadId);
			if(threadVars != null)
			{
				threadVars.clear();
			}
		}
	}

	public void addCloseListener(RemoteDebuggerCloseListener listener)
	{
		myCloseListeners.add(listener);
	}

	public void removeCloseListener(RemoteDebuggerCloseListener listener)
	{
		myCloseListeners.remove(listener);
	}

	@Override
	public List<PydevCompletionVariant> getCompletions(String threadId, String frameId, String prefix)
	{
		GetCompletionsCommand command = new GetCompletionsCommand(this, threadId, frameId, prefix);
		execute(command);
		return command.getCompletions();
	}

	@Override
	public String getDescription(String threadId, String frameId, String cmd)
	{
		GetDescriptionCommand command = new GetDescriptionCommand(this, threadId, frameId, cmd);
		execute(command);
		return command.getResult();
	}

	@Override
	public void addExceptionBreakpoint(ExceptionBreakpointCommandFactory factory)
	{
		execute(factory.createAddCommand(this));
	}

	@Override
	public void removeExceptionBreakpoint(ExceptionBreakpointCommandFactory factory)
	{
		execute(factory.createRemoveCommand(this));
	}

	@Override
	public void suspendOtherThreads(PyThreadInfo thread)
	{
		if(!myThreads.containsKey(thread.getId()))
		{
			// It means that breakpoint with "Suspend all" policy was reached in another process
			// and we should suspend all threads in the current process on Java side
			for(PyThreadInfo otherThread : getThreads())
			{
				if(!otherThread.getId().equals(thread.getId()))
				{
					suspendThread(otherThread.getId());
				}
			}
		}
	}

	protected void onProcessCreatedEvent() throws PyDebuggerException
	{
	}

	protected void fireCloseEvent()
	{
		for(RemoteDebuggerCloseListener listener : myCloseListeners)
		{
			listener.closed();
		}
	}

	public void fireCommunicationError()
	{
		for(RemoteDebuggerCloseListener listener : myCloseListeners)
		{
			listener.communicationError();
		}
	}

	// for DebuggerReader only
	public void fireExitEvent()
	{
		for(RemoteDebuggerCloseListener listener : myCloseListeners)
		{
			listener.detached();
		}
	}
}
