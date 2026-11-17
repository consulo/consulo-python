// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ast;

import com.jetbrains.python.PyNames;
import consulo.annotation.access.RequiredReadAction;

public interface PyAstBoolLiteralExpression extends PyAstLiteralExpression {
    @RequiredReadAction
    default boolean getValue() {
        return PyNames.TRUE.equals(getText());
    }

    @Override
    default void acceptPyVisitor(PyAstElementVisitor pyVisitor) {
        pyVisitor.visitPyBoolLiteralExpression(this);
    }
}
