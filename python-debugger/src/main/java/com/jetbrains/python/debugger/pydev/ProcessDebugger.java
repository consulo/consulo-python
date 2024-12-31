package com.jetbrains.python.debugger.pydev;

import java.util.Collection;
import java.util.List;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import consulo.execution.debug.breakpoint.SuspendPolicy;
import consulo.execution.debug.frame.XValueChildrenList;
import com.jetbrains.python.console.pydev.PydevCompletionVariant;
import com.jetbrains.python.debugger.ArrayChunk;
import com.jetbrains.python.debugger.PyDebugValue;
import com.jetbrains.python.debugger.PyDebuggerException;
import com.jetbrains.python.debugger.PyReferringObjectsValue;
import com.jetbrains.python.debugger.PyThreadInfo;

/**
 * @author traff
 */
public interface ProcessDebugger
{
	String handshake() throws PyDebuggerException;

	PyDebugValue evaluate(String threadId, String frameId, String expression, boolean execute) throws PyDebuggerException;

	PyDebugValue evaluate(String threadId, String frameId, String expression, boolean execute, boolean trimResult) throws PyDebuggerException;

	void consoleExec(String threadId, String frameId, String expression, PyDebugCallback<String> callback);

	XValueChildrenList loadFrame(String threadId, String frameId) throws PyDebuggerException;

	// todo: don't generate temp variables for qualified expressions - just split 'em
	XValueChildrenList loadVariable(String threadId, String frameId, PyDebugValue var) throws PyDebuggerException;

	ArrayChunk loadArrayItems(String threadId, String frameId, PyDebugValue var, int rowOffset, int colOffset, int rows, int cols, String format) throws PyDebuggerException;

	void loadReferrers(String threadId, String frameId, PyReferringObjectsValue var, PyDebugCallback<XValueChildrenList> callback);

	PyDebugValue changeVariable(String threadId, String frameId, PyDebugValue var, String value) throws PyDebuggerException;

	@Nullable
	String loadSource(String path);

	Collection<PyThreadInfo> getThreads();

	void execute(@Nonnull AbstractCommand command);

	void suspendAllThreads();

	void suspendThread(String threadId);

	/**
	 * Disconnects current debug process. Closes all resources.
	 */
	void close();

	boolean isConnected();

	void waitForConnect() throws Exception;

	/**
	 * Disconnects currently connected process. After that it can wait for the next.
	 */
	void disconnect();

	void run() throws PyDebuggerException;

	void smartStepInto(String threadId, String functionName);

	void resumeOrStep(String threadId, ResumeOrStepCommand.Mode mode);

	void setTempBreakpoint(@Nonnull String type, @Nonnull String file, int line);

	void removeTempBreakpoint(@Nonnull String file, int line);

	void setBreakpoint(@Nonnull String typeId, @Nonnull String file, int line, @Nullable String condition, @Nullable String logExpression, @Nullable String funcName, @Nonnull SuspendPolicy policy);

	void removeBreakpoint(@Nonnull String typeId, @Nonnull String file, int line);

	void setShowReturnValues(boolean isShowReturnValues);

	void addCloseListener(RemoteDebuggerCloseListener remoteDebuggerCloseListener);

	List<PydevCompletionVariant> getCompletions(String threadId, String frameId, String prefix);

	String getDescription(String threadId, String frameId, String cmd);


	void addExceptionBreakpoint(ExceptionBreakpointCommandFactory factory);

	void removeExceptionBreakpoint(ExceptionBreakpointCommandFactory factory);

	void suspendOtherThreads(PyThreadInfo thread);

}
