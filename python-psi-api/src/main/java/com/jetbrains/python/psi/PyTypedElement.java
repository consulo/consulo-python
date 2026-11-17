// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import consulo.annotation.access.RequiredReadAction;
import com.jetbrains.python.ast.PyAstTypedElement;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jspecify.annotations.Nullable;

/**
 * Optionally typed Python element.
 *
 */
public interface PyTypedElement extends PyAstTypedElement, PyElement {
    @RequiredReadAction
    default @Nullable PyType getType(TypeEvalContext context) {
        return context.getType(this);
    }

    @Nullable
    PyType getType(TypeEvalContext context, TypeEvalContext.Key key);
}
