// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ast;

/**
 * Represents a literal dict expression, e.g. <tt>{'a': 1}</tt>
 */
public interface PyAstDictLiteralExpression extends PyAstSequenceExpression {
    @Override
    PyAstKeyValueExpression[] getElements();

    @Override
    default void acceptPyVisitor(PyAstElementVisitor pyVisitor) {
        pyVisitor.visitPyDictLiteralExpression(this);
    }
}
