package com.jetbrains.python.psi;

import com.jetbrains.python.ast.PyAstAsPattern;
import consulo.annotation.access.RequiredReadAction;
import org.jspecify.annotations.Nullable;

public interface PyAsPattern extends PyAstAsPattern, PyPattern {
    @Override
    @RequiredReadAction
    default PyPattern getPattern() {
        return (PyPattern)PyAstAsPattern.super.getPattern();
    }

    @Override
    @RequiredReadAction
    default @Nullable PyTargetExpression getTarget() {
        return (PyTargetExpression)PyAstAsPattern.super.getTarget();
    }
}
