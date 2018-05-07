package com.jetbrains.python.debugger.pydev;


import javax.annotation.Nonnull;

public class RemoveBreakpointCommand extends LineBreakpointCommand {

  public RemoveBreakpointCommand(final RemoteDebugger debugger, @Nonnull final String type, final String file, final int line) {
    super(debugger, type, REMOVE_BREAKPOINT, file, line);
  }
}
