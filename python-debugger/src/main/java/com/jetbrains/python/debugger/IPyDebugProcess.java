package com.jetbrains.python.debugger;

import java.io.IOException;

import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.frame.XValueChildrenList;
import com.jetbrains.python.debugger.pydev.PyDebugCallback;

/**
 * @author yole
 */
public interface IPyDebugProcess extends PyFrameAccessor
{
	PyPositionConverter getPositionConverter();

	void threadSuspended(PyThreadInfo thread, boolean updateSourcePosition);

	boolean canSaveToTemp(String name);

	void threadResumed(PyThreadInfo thread);

	void printToConsole(String text, ConsoleViewContentType contentType);

	void init();

	int handleDebugPort(int port) throws IOException;

	void recordSignature(PySignature signature);

	void recordLogEvent(PyConcurrencyEvent event);

	void showConsole(PyThreadInfo thread);

	void loadReferrers(PyReferringObjectsValue var, PyDebugCallback<XValueChildrenList> callback);

	void suspendAllOtherThreads(PyThreadInfo thread);

	boolean isSuspendedOnAllThreadsPolicy();

	void consoleInputRequested(boolean isStarted);

	XDebugSession getSession();
}
