// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ast;

import consulo.annotation.access.RequiredReadAction;

import static com.jetbrains.python.ast.PyAstElementKt.findChildByClass;

public interface PyAstCapturePattern extends PyAstPattern {
    @Override
    default boolean isIrrefutable() {
        return true;
    }

    @RequiredReadAction
    default PyAstTargetExpression getTarget() {
        PyAstTargetExpression target = findChildByClass(this, PyAstTargetExpression.class);
        if (target == null) {
            throw new IllegalArgumentException(this + ": target cannot be null");
        }
        return target;
    }

    @Override
    default void acceptPyVisitor(PyAstElementVisitor pyVisitor) {
        pyVisitor.visitPyCapturePattern(this);
    }
}
