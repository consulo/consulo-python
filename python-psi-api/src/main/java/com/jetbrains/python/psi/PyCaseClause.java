// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import com.jetbrains.python.ast.PyAstCaseClause;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.access.RequiredReadAction;
import org.jspecify.annotations.Nullable;

public interface PyCaseClause extends PyAstCaseClause, PyStatementPart {
    @Override
    @RequiredReadAction
    default @Nullable PyPattern getPattern() {
        return (PyPattern)PyAstCaseClause.super.getPattern();
    }

    @Override
    @RequiredReadAction
    default @Nullable PyExpression getGuardCondition() {
        return (PyExpression)PyAstCaseClause.super.getGuardCondition();
    }

    @Nullable
    PyType getSubjectTypeAfter(TypeEvalContext context);
}
