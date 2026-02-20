package com.jetbrains.python.debugger.pydev;


import jakarta.annotation.Nonnull;

public class RemoveBreakpointCommand extends LineBreakpointCommand {

  public RemoveBreakpointCommand(RemoteDebugger debugger, @Nonnull String type, String file, int line) {
    super(debugger, type, REMOVE_BREAKPOINT, file, line);
  }
}
