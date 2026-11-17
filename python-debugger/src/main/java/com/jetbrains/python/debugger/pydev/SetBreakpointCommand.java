// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

// Licensed under the terms of the Eclipse Public License (EPL).
package com.jetbrains.python.debugger.pydev;


import consulo.execution.debug.breakpoint.SuspendPolicy;
import org.jspecify.annotations.Nullable;

public class SetBreakpointCommand extends LineBreakpointCommand {
    private final @Nullable String myCondition;
    private final @Nullable String myLogExpression;
    private final @Nullable String myFuncName;
    private final SuspendPolicy mySuspendPolicy;

    public SetBreakpointCommand(RemoteDebugger debugger,
                                String type,
                                String file,
                                int line) {
        this(debugger, type, file, line, null, null, null, SuspendPolicy.NONE);
    }


    public SetBreakpointCommand(RemoteDebugger debugger,
                                String type,
                                String file,
                                int line,
                                @Nullable String condition,
                                @Nullable String logExpression,
                                @Nullable String funcName,
                                SuspendPolicy policy) {
        super(debugger, type, SET_BREAKPOINT, file, line);
        myCondition = condition;
        myLogExpression = logExpression;
        myFuncName = funcName;
        mySuspendPolicy = policy;
    }

    @Override
    protected void buildPayload(Payload payload) {
        super.buildPayload(payload);
        payload.add(buildCondition(myFuncName)).add(mySuspendPolicy.name()).add(buildCondition(myCondition))
          .add(buildCondition(myLogExpression));
    }
}
