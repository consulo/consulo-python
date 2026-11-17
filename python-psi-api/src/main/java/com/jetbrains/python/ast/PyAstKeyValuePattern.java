// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ast;

import consulo.annotation.access.RequiredReadAction;
import consulo.util.lang.ObjectUtil;
import org.jspecify.annotations.Nullable;

import static com.jetbrains.python.ast.PyAstElementKt.findNotNullChildByClass;

public interface PyAstKeyValuePattern extends PyAstPattern {
    @RequiredReadAction
    default PyAstPattern getKeyPattern() {
        return findNotNullChildByClass(this, PyAstPattern.class);
    }

    @RequiredReadAction
    default @Nullable PyAstPattern getValuePattern() {
        return ObjectUtil.tryCast(getLastChild(), PyAstPattern.class);
    }

    @Override
    default boolean isIrrefutable() {
        return false;
    }

    @Override
    default void acceptPyVisitor(PyAstElementVisitor pyVisitor) {
        pyVisitor.visitPyKeyValuePattern(this);
    }
}
