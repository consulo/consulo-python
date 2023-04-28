package com.jetbrains.python.debugger;

import consulo.execution.debug.frame.XValueChildrenList;
import com.jetbrains.python.debugger.pydev.PyDebugCallback;

/**
 * @author traff
 */
public class PyReferrersLoader
{
	private final IPyDebugProcess myProcess;

	public PyReferrersLoader(IPyDebugProcess process)
	{
		myProcess = process;
	}

	public void loadReferrers(PyReferringObjectsValue value, PyDebugCallback<XValueChildrenList> callback)
	{
		myProcess.loadReferrers(value, callback);
	}
}
