// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ast;

import consulo.annotation.access.RequiredReadAction;

import static com.jetbrains.python.ast.PyAstElementKt.findNotNullChildByClass;

public interface PyAstGroupPattern extends PyAstPattern {
    @RequiredReadAction
    default PyAstPattern getPattern() {
        return findNotNullChildByClass(this, PyAstPattern.class);
    }

    @Override
    @RequiredReadAction
    default boolean isIrrefutable() {
        return getPattern().isIrrefutable();
    }

    @Override
    default void acceptPyVisitor(PyAstElementVisitor pyVisitor) {
        pyVisitor.visitPyGroupPattern(this);
    }
}
