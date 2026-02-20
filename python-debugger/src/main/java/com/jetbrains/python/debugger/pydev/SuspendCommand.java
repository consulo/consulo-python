package com.jetbrains.python.debugger.pydev;

/**
 * @author traff
 */
public class SuspendCommand extends AbstractThreadCommand {
  protected SuspendCommand(RemoteDebugger debugger, String threadId) {
    super(debugger, SUSPEND_THREAD, threadId);
  }
}
