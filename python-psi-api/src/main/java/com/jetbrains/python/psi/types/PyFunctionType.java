// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types;

import com.jetbrains.python.psi.PyCallable;
import com.jetbrains.python.psi.PyFunction;
import org.jspecify.annotations.Nullable;

/**
 * Type of a particular function that is represented as a {@link PyCallable} in the PSI tree like lambda or function.
 * Use {@link #getCallable()} to get it.
 *
 */
public interface PyFunctionType extends PyCallableType {
    /**
     * @return actual callable element line function or lambda
     */
    @Override
    PyCallable getCallable();

    @Override
    PyFunctionType dropSelf(TypeEvalContext context);

    @Override
    default PyFunction.@Nullable Modifier getModifier() {
        PyCallable callable = getCallable();
        return callable instanceof PyFunction ? ((PyFunction)callable).getModifier() : null;
    }
}
