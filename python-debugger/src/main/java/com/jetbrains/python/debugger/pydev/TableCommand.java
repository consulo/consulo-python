// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger.pydev;

import com.jetbrains.python.debugger.pydev.tables.PyDevCommandParameters;
import com.jetbrains.python.tables.TableCommandParameters;
import com.jetbrains.python.tables.TableCommandType;
import com.jetbrains.python.debugger.PyDebuggerException;
import org.jspecify.annotations.Nullable;

public class TableCommand extends AbstractFrameCommand<@Nullable String> {
    private final String myInitExpr;
    private final TableCommandType myCommandType;
    private final @Nullable TableCommandParameters myTableCommandParameters;
    private @Nullable String myCommandResult = null;

    public TableCommand(RemoteDebugger debugger,
                        String threadId,
                        String frameId,
                        String initExpr,
                        TableCommandType commandType,
                        @Nullable TableCommandParameters tableCommandParameters) {
        super(debugger, CMD_TABLE_EXEC, threadId, frameId);
        myInitExpr = initExpr;
        myCommandType = commandType;
        myTableCommandParameters = tableCommandParameters;
    }

    public @Nullable String getCommandResult() {
        return myCommandResult;
    }

    public void setCommandResult(@Nullable String commandResult) {
        myCommandResult = commandResult;
    }

    @Override
    protected void buildPayload(Payload payload) {
        super.buildPayload(payload);
        payload.add(myInitExpr).add(myCommandType.name());

        if (myTableCommandParameters instanceof PyDevCommandParameters parameters) {
            payload.add(parameters.getStart()).add(parameters.getEnd()).add(parameters.getFormat() != null ? parameters.getFormat() : "null");
        }
    }

    @Override
    public boolean isResponseExpected() {
        return true;
    }

    @Override
    protected void processResponse(ProtocolFrame response) throws PyDebuggerException {
        super.processResponse(response);
        myCommandResult = response.getPayload();
    }
}
