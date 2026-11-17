// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ast;

import org.jspecify.annotations.Nullable;

/**
 * Represents positional-only parameters delimiter introduced in Python 3.8 (PEP 570).
 */
public interface PyAstSlashParameter extends PyAstParameter {
    String TEXT = "/";

    @Override
    default @Nullable PyAstNamedParameter getAsNamed() {
        return null;
    }

    @Override
    default @Nullable PyAstTupleParameter getAsTuple() {
        return null;
    }

    @Override
    default @Nullable PyAstExpression getDefaultValue() {
        return null;
    }

    @Override
    default boolean hasDefaultValue() {
        return false;
    }

    @Override
    default @Nullable String getDefaultValueText() {
        return null;
    }

    @Override
    default boolean isSelf() {
        return false;
    }

    @Override
    default void acceptPyVisitor(PyAstElementVisitor pyVisitor) {
        pyVisitor.visitPySlashParameter(this);
    }
}
