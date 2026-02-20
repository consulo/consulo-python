package com.jetbrains.python.debugger;


public class PyStackFrameInfo {

  private final String myThreadId;
  private final String myId;
  private final String myName;
  private final PySourcePosition myPosition;

  public PyStackFrameInfo(String threadId, String id, String name, PySourcePosition position) {
    myThreadId = threadId;
    myId = id;
    myName = name;
    myPosition = position;
  }

  public String getThreadId() {
    return myThreadId;
  }

  public String getId() {
    return myId;
  }

  public String getName() {
    return myName;
  }

  public PySourcePosition getPosition() {
    return myPosition;
  }

}
