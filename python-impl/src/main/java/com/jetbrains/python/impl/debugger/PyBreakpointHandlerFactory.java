// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.impl.debugger;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import consulo.execution.debug.breakpoint.XBreakpointHandler;

@ExtensionAPI(ComponentScope.APPLICATION)
public abstract class PyBreakpointHandlerFactory {
    public static final ExtensionPointName<PyBreakpointHandlerFactory> EP_NAME = ExtensionPointName.create(PyBreakpointHandlerFactory.class);

    public abstract XBreakpointHandler createBreakpointHandler(PyDebugProcess process);
}
