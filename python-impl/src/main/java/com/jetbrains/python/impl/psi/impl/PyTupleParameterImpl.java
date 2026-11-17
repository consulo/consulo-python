// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import consulo.language.ast.ASTNode;
import consulo.navigation.ItemPresentation;
import com.jetbrains.python.impl.PyStubElementTypes;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.PyTupleParameter;
import com.jetbrains.python.psi.stubs.PyTupleParameterStub;
import org.jspecify.annotations.Nullable;

/**
 * Represents a tuple parameter as stubbed element.
 */
public class PyTupleParameterImpl extends PyBaseElementImpl<PyTupleParameterStub> implements PyTupleParameter {

    public PyTupleParameterImpl(ASTNode astNode) {
        super(astNode);
    }

    public PyTupleParameterImpl(PyTupleParameterStub stub) {
        super(stub, PyStubElementTypes.TUPLE_PARAMETER);
    }

    @Override
    public boolean hasDefaultValue() {
        PyTupleParameterStub stub = getStub();
        if (stub != null) {
            return stub.getDefaultValueText() != null;
        }
        return PyTupleParameter.super.hasDefaultValue();
    }

    @Override
    public @Nullable String getDefaultValueText() {
        PyTupleParameterStub stub = getStub();
        if (stub != null) {
            return stub.getDefaultValueText();
        }
        return PyTupleParameter.super.getDefaultValueText();
    }

    @Override
    protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
        pyVisitor.visitPyTupleParameter(this);
    }

    @Override
    public PyParameter[] getContents() {
        return getStubOrPsiChildren(PythonDialectsTokenSetProvider.getInstance().getParameterTokens(), new PyParameter[0]);
    }

    @Override
    public ItemPresentation getPresentation() {
        return new PyElementPresentation(this);
    }
}
