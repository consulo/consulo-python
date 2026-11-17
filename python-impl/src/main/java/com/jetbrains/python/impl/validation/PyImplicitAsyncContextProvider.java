// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.validation;

import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.psi.PyFunction;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.application.Application;
import org.jspecify.annotations.Nullable;

@ExtensionAPI(ComponentScope.APPLICATION)
public interface PyImplicitAsyncContextProvider {
    boolean isImplicitAsyncContext(ScopeOwner scopeOwner);

    /**
     * Whether {@code await}, {@code async for} and {@code async with} are allowed in {@code scopeOwner}, i.e. it is an async function or an
     * implicit async context (such as the Python console or a Jupyter notebook).
     */
    static boolean isAsyncAllowed(@Nullable ScopeOwner scopeOwner) {
        if (scopeOwner == null) {
            return false;
        }
        if (scopeOwner instanceof PyFunction function && function.isAsync()) {
            return true;
        }
        return Application.get().getExtensionPoint(PyImplicitAsyncContextProvider.class)
            .findFirstSafe(it -> it.isImplicitAsyncContext(scopeOwner)) != null;
    }
}
