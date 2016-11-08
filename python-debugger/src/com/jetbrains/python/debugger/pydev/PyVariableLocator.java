package com.jetbrains.python.debugger.pydev;

/**
 * IVariableLocator knows how to produce location information
 * for CMD_GET_VARIABLE
 * <p>
 * The location is specified as:
 * <p>
 * thread_id, stack_frame, LOCAL|GLOBAL, attribute*
 */
public interface PyVariableLocator
{
	public String getThreadId();

	public String getPyDBLocation();

}
