// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.validation;

import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.PythonRuntimeService;
import com.jetbrains.python.psi.PyExpressionCodeFragment;
import consulo.annotation.component.ExtensionImpl;

@ExtensionImpl
public class PyDevConsoleAsyncContextProvider implements PyImplicitAsyncContextProvider {
    @Override
    public boolean isImplicitAsyncContext(ScopeOwner scopeOwner) {
        // Top-level expressions in the Python console are allowed to contain "await", "async with" and "async for"
        return scopeOwner instanceof PyExpressionCodeFragment && PythonRuntimeService.getInstance().isInPydevConsole(scopeOwner);
    }
}
