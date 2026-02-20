package com.jetbrains.python.debugger.pydev;

import jakarta.annotation.Nonnull;

/**
 * @author traff
 */
public abstract class LineBreakpointCommand extends AbstractCommand
{
	private final String myType;
	@Nonnull
	protected final String myFile;
	protected final int myLine;


	public LineBreakpointCommand(@Nonnull RemoteDebugger debugger, String type, int commandCode, @Nonnull String file, int line)
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
