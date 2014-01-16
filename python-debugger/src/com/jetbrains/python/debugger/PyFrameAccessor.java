package com.jetbrains.python.debugger;

import org.jetbrains.annotations.Nullable;
import com.intellij.xdebugger.frame.XValueChildrenList;

/**
 * Facade to access python variables frame
 *
 * @author traff
 */
public interface PyFrameAccessor {
	PyDebugValue evaluate(final String expression, final boolean execute, boolean doTrunc) throws PyDebuggerException;

	@Nullable
	XValueChildrenList loadFrame() throws PyDebuggerException;

	XValueChildrenList loadVariable(PyDebugValue var) throws PyDebuggerException;

	void changeVariable(PyDebugValue variable, String expression) throws PyDebuggerException;
}
