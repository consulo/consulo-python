// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger.pydev;

import com.jetbrains.python.debugger.PyDebuggerException;
import org.jspecify.annotations.Nullable;

public class LoadSourceCommand extends AbstractCommand {
    private final String myPath;

    private String myContent = null;

    protected LoadSourceCommand(RemoteDebugger debugger, String path) {
        super(debugger, LOAD_SOURCE);
        myPath = path;
    }

    @Override
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

    public @Nullable String getContent() {
        return myContent;
    }
}
