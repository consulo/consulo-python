// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import consulo.annotation.access.RequiredReadAction;
import consulo.language.psi.StubBasedPsiElement;
import com.jetbrains.python.ast.PyAstSlashParameter;
import com.jetbrains.python.psi.stubs.PySlashParameterStub;
import org.jspecify.annotations.Nullable;

/**
 * Represents positional-only parameters delimiter introduced in Python 3.8 (PEP 570).
 */
public interface PySlashParameter extends PyAstSlashParameter, PyParameter, StubBasedPsiElement<PySlashParameterStub> {
    @Override
    @RequiredReadAction
    default @Nullable PyNamedParameter getAsNamed() {
        return (PyNamedParameter)PyAstSlashParameter.super.getAsNamed();
    }

    @Override
    @RequiredReadAction
    default @Nullable PyTupleParameter getAsTuple() {
        return (PyTupleParameter)PyAstSlashParameter.super.getAsTuple();
    }

    @Override
    @RequiredReadAction
    default @Nullable PyExpression getDefaultValue() {
        return (PyExpression)PyAstSlashParameter.super.getDefaultValue();
    }
}
