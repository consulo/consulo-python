// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

// Licensed under the terms of the Eclipse Public License (EPL).
package com.jetbrains.python.debugger.pydev;



public class RemoveBreakpointCommand extends LineBreakpointCommand {

    public RemoveBreakpointCommand(RemoteDebugger debugger, String type, String file, int line) {
        super(debugger, type, REMOVE_BREAKPOINT, file, line);
    }
}
