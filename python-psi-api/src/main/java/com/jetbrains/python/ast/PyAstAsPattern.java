package com.jetbrains.python.ast;

import consulo.annotation.access.RequiredReadAction;
import org.jspecify.annotations.Nullable;

import static com.jetbrains.python.ast.PyAstElementKt.findChildByClass;

public interface PyAstAsPattern extends PyAstPattern {
    @RequiredReadAction
    default PyAstPattern getPattern() {
        PyAstPattern pattern = findChildByClass(this, PyAstPattern.class);
        if (pattern == null) {
            throw new IllegalArgumentException(this + ": pattern cannot be null");
        }
        return pattern;
    }

    @RequiredReadAction
    default @Nullable PyAstTargetExpression getTarget() {
        return findChildByClass(this, PyAstTargetExpression.class);
    }

    @Override
    @RequiredReadAction
    default boolean isIrrefutable() {
        return getPattern().isIrrefutable();
    }

    @Override
    default void acceptPyVisitor(PyAstElementVisitor pyVisitor) {
        pyVisitor.visitPyAsPattern(this);
    }
}
