// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import com.jetbrains.python.ast.PyAstAugAssignmentStatement;
import consulo.annotation.access.RequiredReadAction;
import org.jspecify.annotations.Nullable;

public interface PyAugAssignmentStatement
    extends PyAstAugAssignmentStatement, PyStatement, PyTypedElement, PyReferenceOwner, PyQualifiedElement {
    /**
     * this refers to the reference before it has been assigned to
     */
    @Override
    @RequiredReadAction
    default PyExpression getTarget() {
        return (PyExpression)PyAstAugAssignmentStatement.super.getTarget();
    }


    @Override
    @RequiredReadAction
    default @Nullable PyExpression getValue() {
        return (PyExpression)PyAstAugAssignmentStatement.super.getValue();
    }

    @Override
    @RequiredReadAction
    default @Nullable PyExpression getQualifier() {
        return (PyExpression)PyAstAugAssignmentStatement.super.getQualifier();
    }
}
