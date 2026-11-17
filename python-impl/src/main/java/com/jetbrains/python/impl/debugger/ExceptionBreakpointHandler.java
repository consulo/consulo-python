// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.debugger;

import consulo.execution.debug.breakpoint.XBreakpoint;
import consulo.execution.debug.breakpoint.XBreakpointHandler;
import consulo.execution.debug.breakpoint.XBreakpointType;


public abstract class ExceptionBreakpointHandler<T extends ExceptionBreakpointProperties<?>> extends XBreakpointHandler<XBreakpoint<T>> {

    private final PyDebugProcess myDebugProcess;

    public ExceptionBreakpointHandler(PyDebugProcess debugProcess, Class<? extends XBreakpointType<XBreakpoint<T>, T>> breakpointTypeClass) {
        super(breakpointTypeClass);
        myDebugProcess = debugProcess;
    }

    @Override
    public void registerBreakpoint(XBreakpoint<T> breakpoint) {
        myDebugProcess.addExceptionBreakpoint(breakpoint);
    }

    @Override
    public void unregisterBreakpoint(XBreakpoint<T> breakpoint, boolean temporary) {
        myDebugProcess.removeExceptionBreakpoint(breakpoint);
    }
}
