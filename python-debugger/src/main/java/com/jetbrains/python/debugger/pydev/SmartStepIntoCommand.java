package com.jetbrains.python.debugger.pydev;


import javax.annotation.Nonnull;

public class SmartStepIntoCommand extends AbstractThreadCommand {
  private String myFuncName;

  public SmartStepIntoCommand(@Nonnull final RemoteDebugger debugger, String threadId,
                              String funcName) {
    super(debugger, SMART_STEP_INTO, threadId);
    myFuncName = funcName;
  }

  @Override
  protected void buildPayload(Payload payload) {
    super.buildPayload(payload);
    payload.add("0").add(myFuncName);
  }

}
