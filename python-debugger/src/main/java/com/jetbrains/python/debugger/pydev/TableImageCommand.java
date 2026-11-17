// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger.pydev;

import com.jetbrains.python.debugger.pydev.tables.PyDevImageCommandParameters;
import com.jetbrains.python.tables.TableCommandParameters;
import com.jetbrains.python.tables.TableCommandType;
import com.jetbrains.python.debugger.PyDebuggerException;
import org.jspecify.annotations.Nullable;

public class TableImageCommand extends AbstractFrameCommand<@Nullable String> {
    private final String myInitExpr;
    private final TableCommandType myCommandType;
    private final @Nullable TableCommandParameters myTableCommandParameters;
    private @Nullable String myCommandResult = null;

    public TableImageCommand(RemoteDebugger debugger,
                             String threadId,
                             String frameId,
                             String initExpr,
                             TableCommandType commandType,
                             @Nullable TableCommandParameters tableCommandParameters) {
        super(debugger, getCommandCode(tableCommandParameters), threadId, frameId);
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
        if (myCommandType == TableCommandType.IMAGE_CHUNK_LOAD) {
            addChunkParameters(payload);
        }
    }

    private void addChunkParameters(Payload payload) {
        if (myTableCommandParameters instanceof PyDevImageCommandParameters parameters) {
            payload.add(parameters.getOffset() != null ? parameters.getOffset() : -1).add(parameters.getImageId());
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

    private static int getCommandCode(@Nullable TableCommandParameters parameters) {
        if (!(parameters instanceof PyDevImageCommandParameters)) {
            return IMAGE_COMMAND_START_LOAD;
        }
        else {
            return IMAGE_COMMAND_CHUNK_LOAD;
        }
    }
}
