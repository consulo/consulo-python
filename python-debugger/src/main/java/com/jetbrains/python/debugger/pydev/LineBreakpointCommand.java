package com.jetbrains.python.debugger.pydev;


/**
 * @author traff
 */
public abstract class LineBreakpointCommand extends AbstractCommand
{
	private final String myType;
	protected final String myFile;
	protected final int myLine;


	public LineBreakpointCommand(RemoteDebugger debugger, String type, int commandCode, String file, int line)
	{
		super(debugger, commandCode);
		myType = type;
		myFile = file;
		myLine = line;
	}

	@Override
	protected void buildPayload(Payload payload)
	{
		payload.add(myType).add(myFile).add(Integer.toString(myLine));
	}
}
