// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import consulo.annotation.access.RequiredReadAction;
import com.jetbrains.python.ast.PyAstTypeDeclarationStatement;
import org.jspecify.annotations.Nullable;

/**
 * @author Mikhail Golubev
 */
public interface PyTypeDeclarationStatement extends PyAstTypeDeclarationStatement, PyStatement, PyAnnotationOwner {
    @Override
    @RequiredReadAction
    default PyExpression getTarget() {
        return (PyExpression)PyAstTypeDeclarationStatement.super.getTarget();
    }

    @Override
    @RequiredReadAction
    default @Nullable PyAnnotation getAnnotation() {
        return (PyAnnotation)PyAstTypeDeclarationStatement.super.getAnnotation();
    }
}
