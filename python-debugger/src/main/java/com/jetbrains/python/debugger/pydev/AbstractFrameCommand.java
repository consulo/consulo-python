package com.jetbrains.python.debugger.pydev;


public abstract class AbstractFrameCommand<T> extends AbstractThreadCommand<T>
{

	private final String myFrameId;

	protected AbstractFrameCommand(RemoteDebugger debugger, int commandCode, String threadId, String frameId)
	{
		super(debugger, commandCode, threadId);
		myFrameId = frameId;
	}

	@Override
	protected void buildPayload(Payload payload)
	{
		super.buildPayload(payload);
		payload.add(myFrameId);
	}
}
