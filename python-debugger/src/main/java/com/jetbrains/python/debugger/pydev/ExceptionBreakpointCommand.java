package com.jetbrains.python.debugger.pydev;


/**
 * @author traff
 */
public class ExceptionBreakpointCommand extends AbstractCommand {

  protected final String myException;


  public ExceptionBreakpointCommand(RemoteDebugger debugger,
                                     int commandCode,
                                     String exception) {
    super(debugger, commandCode);
    myException = exception;
  }

  @Override
  protected void buildPayload(Payload payload) {
    payload.add(myException);
  }

  public static ExceptionBreakpointCommand addExceptionBreakpointCommand(RemoteDebugger debugger, String exception, AddExceptionBreakpointCommand.ExceptionBreakpointNotifyPolicy notifyPolicy) {
    return new AddExceptionBreakpointCommand(debugger, exception, notifyPolicy);
  }

  public static ExceptionBreakpointCommand removeExceptionBreakpointCommand(RemoteDebugger debugger, String exception) {
    return new ExceptionBreakpointCommand(debugger, REMOVE_EXCEPTION_BREAKPOINT, exception);
  }
}
