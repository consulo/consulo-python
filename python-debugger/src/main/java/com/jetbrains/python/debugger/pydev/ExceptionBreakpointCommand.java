package com.jetbrains.python.debugger.pydev;

import jakarta.annotation.Nonnull;

/**
 * @author traff
 */
public class ExceptionBreakpointCommand extends AbstractCommand {

  @Nonnull
  protected final String myException;


  public ExceptionBreakpointCommand(@Nonnull RemoteDebugger debugger,
                                     int commandCode,
                                     @Nonnull String exception) {
    super(debugger, commandCode);
    myException = exception;
  }

  @Override
  protected void buildPayload(Payload payload) {
    payload.add(myException);
  }

  public static ExceptionBreakpointCommand addExceptionBreakpointCommand(@Nonnull RemoteDebugger debugger, String exception, AddExceptionBreakpointCommand.ExceptionBreakpointNotifyPolicy notifyPolicy) {
    return new AddExceptionBreakpointCommand(debugger, exception, notifyPolicy);
  }

  public static ExceptionBreakpointCommand removeExceptionBreakpointCommand(@Nonnull RemoteDebugger debugger, String exception) {
    return new ExceptionBreakpointCommand(debugger, REMOVE_EXCEPTION_BREAKPOINT, exception);
  }
}
