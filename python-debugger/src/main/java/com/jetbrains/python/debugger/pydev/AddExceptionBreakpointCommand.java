package com.jetbrains.python.debugger.pydev;

import jakarta.annotation.Nonnull;

/**
 * @author traff
 */
public class AddExceptionBreakpointCommand extends ExceptionBreakpointCommand
{
	final ExceptionBreakpointNotifyPolicy myNotifyPolicy;

	public AddExceptionBreakpointCommand(@Nonnull final RemoteDebugger debugger, @Nonnull String exception, @Nonnull ExceptionBreakpointNotifyPolicy notifyPolicy)
	{
		super(debugger, ADD_EXCEPTION_BREAKPOINT, exception);
		myNotifyPolicy = notifyPolicy;
	}

	@Override
	protected void buildPayload(Payload payload)
	{
		super.buildPayload(payload);
		payload.add(myNotifyPolicy.isNotifyOnlyOnFirst() ? 2 : 0).add(myNotifyPolicy.isNotifyOnTerminate()).add(myNotifyPolicy.isIgnoreLibraries());
	}

	public static class ExceptionBreakpointNotifyPolicy
	{
		private final boolean myNotifyOnTerminate;
		private final boolean myNotifyOnlyOnFirst;
		private final boolean myIgnoreLibraries;

		public ExceptionBreakpointNotifyPolicy(boolean notifyOnTerminate, boolean notifyOnlyOnFirst, boolean ignoreLibraries)
		{
			myNotifyOnTerminate = notifyOnTerminate;
			myNotifyOnlyOnFirst = notifyOnlyOnFirst;
			myIgnoreLibraries = ignoreLibraries;
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
