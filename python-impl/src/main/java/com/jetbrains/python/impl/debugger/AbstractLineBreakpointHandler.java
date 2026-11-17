// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.debugger;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import consulo.execution.debug.XSourcePosition;
import consulo.execution.debug.breakpoint.XBreakpointHandler;
import consulo.execution.debug.breakpoint.XBreakpointProperties;
import consulo.execution.debug.breakpoint.XBreakpointType;
import consulo.execution.debug.breakpoint.XLineBreakpoint;

import java.util.List;
import java.util.Map;

public class AbstractLineBreakpointHandler extends XBreakpointHandler<XLineBreakpoint<XBreakpointProperties>> {
    protected final PyDebugProcess myDebugProcess;
    private final Map<XLineBreakpoint<XBreakpointProperties>, XSourcePosition> myBreakPointPositions = Maps.newHashMap();

    public AbstractLineBreakpointHandler(
      Class<? extends XBreakpointType<XLineBreakpoint<XBreakpointProperties>, ?>> breakpointTypeClass,
      PyDebugProcess debugProcess) {
        super(breakpointTypeClass);
        myDebugProcess = debugProcess;
    }

    public void reregisterBreakpoints() {
        List<XLineBreakpoint<XBreakpointProperties>> breakpoints = Lists.newArrayList(myBreakPointPositions.keySet());
        for (XLineBreakpoint<XBreakpointProperties> breakpoint : breakpoints) {
            unregisterBreakpoint(breakpoint, false);
            registerBreakpoint(breakpoint);
        }
    }

    @Override
    public void registerBreakpoint(XLineBreakpoint<XBreakpointProperties> breakpoint) {
        XSourcePosition position = breakpoint.getSourcePosition();
        if (position != null && position.getFile().isValid()) {
            myDebugProcess.addBreakpoint(myDebugProcess.getPositionConverter().convertToPython(position), breakpoint);
            myBreakPointPositions.put(breakpoint, position);
        }
    }

    @Override
    public void unregisterBreakpoint(XLineBreakpoint<XBreakpointProperties> breakpoint, boolean temporary) {
        XSourcePosition position = myBreakPointPositions.get(breakpoint);
        if (position != null && position.getFile().isValid()) {
            myDebugProcess.removeBreakpoint(myDebugProcess.getPositionConverter().convertToPython(position));
            myBreakPointPositions.remove(breakpoint);
        }
    }
}
