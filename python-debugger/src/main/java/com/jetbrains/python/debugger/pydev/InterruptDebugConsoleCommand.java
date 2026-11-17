// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger.pydev;

public class InterruptDebugConsoleCommand extends AbstractCommand<Object> {
    public InterruptDebugConsoleCommand(RemoteDebugger debugger) {
        super(debugger, INTERRUPT_DEBUG_CONSOLE);
    }

    @Override
    protected void buildPayload(Payload payload) {
    }
}
