// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger.pydev;

public class ProcessCreatedMsgReceivedCommand extends AbstractCommand<Void> {
    private final int myCommandSequence;

    /**
     * @param commandSequence the original {@link ProcessCreatedCommand} sequence number
     */
    public ProcessCreatedMsgReceivedCommand(RemoteDebugger debugger, int commandSequence) {
        super(debugger, PROCESS_CREATED_MSG_RECEIVED);
        myCommandSequence = commandSequence;
    }

    @Override
    protected void buildPayload(Payload payload) {
        payload.add(myCommandSequence);
    }
}
