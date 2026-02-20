package com.jetbrains.python.debugger.pydev;


import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import consulo.execution.debug.breakpoint.SuspendPolicy;

public class SetBreakpointCommand extends LineBreakpointCommand
{
	private
	@Nullable
	final String myCondition;
	private
	@Nullable
	final String myLogExpression;
	private
	@Nullable
	final String myFuncName;
	private
	@Nonnull
	final SuspendPolicy mySuspendPolicy;

	public SetBreakpointCommand(@Nonnull RemoteDebugger debugger, @Nonnull String type, @Nonnull String file, int line)
	{
		this(debugger, type, file, line, null, null, null, SuspendPolicy.NONE);
	}


	public SetBreakpointCommand(@Nonnull RemoteDebugger debugger,
			@Nonnull String type,
			@Nonnull String file,
			int line,
			@Nullable String condition,
			@Nullable String logExpression,
			@Nullable String funcName,
			@Nonnull SuspendPolicy policy)
	{
		super(debugger, type, SET_BREAKPOINT, file, line);
		myCondition = condition;
		myLogExpression = logExpression;
		myFuncName = funcName;
		mySuspendPolicy = policy;
	}

	@Override
	protected void buildPayload(Payload payload)
	{
		super.buildPayload(payload);
		payload.add(buildCondition(myFuncName)).add(mySuspendPolicy.name()).add(buildCondition(myCondition)).add(buildCondition(myLogExpression));
	}

	@Nonnull
	private static String buildCondition(String expression)
	{
		String condition;

		if(expression != null)
		{
			condition = expression.replaceAll("\n", NEW_LINE_CHAR);
			condition = condition.replaceAll("\t", TAB_CHAR);
		}
		else
		{
			condition = "None";
		}
		return condition;
	}
}
