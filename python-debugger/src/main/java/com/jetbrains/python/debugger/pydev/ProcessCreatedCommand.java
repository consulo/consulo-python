package com.jetbrains.python.debugger.pydev;


/**
 * @author Alexander Koshevoy
 */
public class ProcessCreatedCommand extends AbstractCommand<ProcessCreatedCommand>
{
	public ProcessCreatedCommand(RemoteDebugger debugger, int commandCode)
	{
		super(debugger, commandCode);
	}

	@Override
	protected void buildPayload(Payload payload)
	{
	}

	public static boolean isProcessCreatedCommand(int command)
	{
		return command == AbstractCommand.PROCESS_CREATED;
	}
}
