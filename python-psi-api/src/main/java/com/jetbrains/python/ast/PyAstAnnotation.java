package com.jetbrains.python.ast;

import consulo.annotation.access.RequiredReadAction;
import org.jspecify.annotations.Nullable;

import static com.jetbrains.python.ast.PyAstElementKt.findChildByClass;

public interface PyAstAnnotation extends PyAstElement {
    @RequiredReadAction
    default @Nullable PyAstExpression getValue() {
        return findChildByClass(this, PyAstExpression.class);
    }

    @Override
    default void acceptPyVisitor(PyAstElementVisitor pyVisitor) {
        pyVisitor.visitPyAnnotation(this);
    }
}
