// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger.pydev;

import consulo.util.lang.Pair;
import consulo.execution.debug.XSourcePosition;
import com.jetbrains.python.debugger.PyDebuggerException;
import org.jspecify.annotations.Nullable;

public class SetNextStatementCommand extends AbstractThreadCommand {
    private final int myLine;
    private final PyDebugCallback<Pair<Boolean, String>> myCallback;
    private final @Nullable String myFunctionName;

    protected SetNextStatementCommand(RemoteDebugger debugger,
                                      String threadId,
                                      XSourcePosition sourcePosition,
                                      @Nullable String functionName,
                                      PyDebugCallback<Pair<Boolean, String>> callback) {
        super(debugger, SET_NEXT_STATEMENT, threadId);
        myLine = sourcePosition.getLine();
        myFunctionName = functionName;
        myCallback = callback;
    }

    @Override
    public boolean isResponseExpected() {
        return true;
    }

    @Override
    protected void processResponse(ProtocolFrame response) throws PyDebuggerException {
        super.processResponse(response);
        try {
            Pair<Boolean, String> result = ProtocolParser.parseSetNextStatementCommand(response.getPayload());
            myCallback.ok(result);
        }
        catch (Exception e) {
            myCallback.error(new PyDebuggerException(response.getPayload()));
        }
    }

    @Override
    protected void buildPayload(Payload payload) {
        super.buildPayload(payload);
        payload.add(myLine + 1).add(buildCondition(myFunctionName));
    }
}
