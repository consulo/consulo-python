package com.jetbrains.python.debugger.pydev;



public class RemoveBreakpointCommand extends LineBreakpointCommand {

  public RemoveBreakpointCommand(RemoteDebugger debugger, String type, String file, int line) {
    super(debugger, type, REMOVE_BREAKPOINT, file, line);
  }
}
