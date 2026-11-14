package com.jetbrains.python.debugger.pydev;


/**
 * @author traff
 */
public class AddExceptionBreakpointCommand extends ExceptionBreakpointCommand
{
	final ExceptionBreakpointNotifyPolicy myNotifyPolicy;

	public AddExceptionBreakpointCommand(RemoteDebugger debugger, String exception, ExceptionBreakpointNotifyPolicy notifyPolicy)
	{
		super(debugger, ADD_EXCEPTION_BREAKPOINT, exception);
		myNotifyPolicy = notifyPolicy;
	}

	@Override
	protected void buildPayload(Payload payload)
	{
		super.buildPayload(payload);
		// pydevd: 1 - on each raise, 2 - only on the first raise, 0 - not on raise
		int notifyOnRaise = myNotifyPolicy.isNotifyOnlyOnFirst() ? 2 : myNotifyPolicy.isNotifyAlways() ? 1 : 0;
		payload.add(notifyOnRaise).add(myNotifyPolicy.isNotifyOnTerminate()).add(myNotifyPolicy.isIgnoreLibraries());
	}

	public static class ExceptionBreakpointNotifyPolicy
	{
		private final boolean myNotifyAlways;
		private final boolean myNotifyOnTerminate;
		private final boolean myNotifyOnlyOnFirst;
		private final boolean myIgnoreLibraries;

		public ExceptionBreakpointNotifyPolicy(boolean notifyAlways, boolean notifyOnTerminate, boolean notifyOnlyOnFirst)
		{
			this(notifyAlways, notifyOnTerminate, notifyOnlyOnFirst, false);
		}

		public ExceptionBreakpointNotifyPolicy(boolean notifyAlways, boolean notifyOnTerminate, boolean notifyOnlyOnFirst, boolean ignoreLibraries)
		{
			myNotifyAlways = notifyAlways;
			myNotifyOnTerminate = notifyOnTerminate;
			myNotifyOnlyOnFirst = notifyOnlyOnFirst;
			myIgnoreLibraries = ignoreLibraries;
		}

		public boolean isNotifyAlways()
		{
			return myNotifyAlways;
		}

		public boolean isNotifyOnTerminate()
		{
			return myNotifyOnTerminate;
		}

		public boolean isNotifyOnlyOnFirst()
		{
			return myNotifyOnlyOnFirst;
		}

		public boolean isIgnoreLibraries()
		{
			return myIgnoreLibraries;
		}
	}
}
