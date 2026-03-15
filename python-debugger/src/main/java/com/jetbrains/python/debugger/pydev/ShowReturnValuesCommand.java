package com.jetbrains.python.debugger.pydev;


public class ShowReturnValuesCommand extends AbstractCommand
{
	private boolean myShowReturnValues;

	public ShowReturnValuesCommand(RemoteDebugger debugger, boolean showReturnValues)
	{
		super(debugger, AbstractCommand.SHOW_RETURN_VALUES);
		myShowReturnValues = showReturnValues;
	}

	@Override
	protected void buildPayload(Payload payload)
	{
		payload.add("SHOW_RETURN_VALUES").add(myShowReturnValues);
	}
}
