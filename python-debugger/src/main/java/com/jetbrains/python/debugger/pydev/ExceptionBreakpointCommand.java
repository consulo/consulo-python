package com.jetbrains.python.debugger.pydev;

import javax.annotation.Nonnull;

/**
 * @author traff
 */
public class ExceptionBreakpointCommand extends AbstractCommand {

  @Nonnull
  protected final String myException;


  public ExceptionBreakpointCommand(@Nonnull final RemoteDebugger debugger,
                                     final int commandCode,
                                     @Nonnull String exception) {
    super(debugger, commandCode);
    myException = exception;
  }

  @Override
  protected void buildPayload(Payload payload) {
    payload.add(myException);
  }

  public static ExceptionBreakpointCommand addExceptionBreakpointCommand(@Nonnull final RemoteDebugger debugger, String exception, AddExceptionBreakpointCommand.ExceptionBreakpointNotifyPolicy notifyPolicy) {
    return new AddExceptionBreakpointCommand(debugger, exception, notifyPolicy);
  }

  public static ExceptionBreakpointCommand removeExceptionBreakpointCommand(@Nonnull final RemoteDebugger debugger, String exception) {
    return new ExceptionBreakpointCommand(debugger, REMOVE_EXCEPTION_BREAKPOINT, exception);
  }
}
