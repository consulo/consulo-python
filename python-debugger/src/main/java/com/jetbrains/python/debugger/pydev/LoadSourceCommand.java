package com.jetbrains.python.debugger.pydev;

import com.jetbrains.python.debugger.PyDebuggerException;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author traff
 */
public class LoadSourceCommand extends AbstractCommand {
  private final String myPath;

  private String myContent = null;

  protected LoadSourceCommand(@Nonnull RemoteDebugger debugger, String path) {
    super(debugger, LOAD_SOURCE);
    myPath = path;
  }

  public boolean isResponseExpected() {
    return true;
  }

  @Override
  protected void processResponse(ProtocolFrame response) throws PyDebuggerException {
    super.processResponse(response);
    myContent = ProtocolParser.parseSourceContent(response.getPayload());
  }

  @Override
  protected void buildPayload(Payload payload) {
    payload.add(myPath);
  }

  @Nullable
  public String getContent() {
    return myContent;
  }
}
