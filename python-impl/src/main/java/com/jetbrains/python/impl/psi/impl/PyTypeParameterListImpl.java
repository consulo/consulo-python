// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import com.jetbrains.python.impl.PyStubElementTypes;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyTypeParameter;
import com.jetbrains.python.psi.PyTypeParameterList;
import com.jetbrains.python.psi.stubs.PyTypeParameterListStub;
import consulo.language.ast.ASTNode;
import consulo.language.psi.stub.IStubElementType;

import java.util.List;

public class PyTypeParameterListImpl extends PyBaseElementImpl<PyTypeParameterListStub> implements PyTypeParameterList {
    public PyTypeParameterListImpl(ASTNode astNode) {
        super(astNode);
    }

    public PyTypeParameterListImpl(PyTypeParameterListStub stub) {
        this(stub, PyStubElementTypes.TYPE_PARAMETER_LIST);
    }

    public PyTypeParameterListImpl(PyTypeParameterListStub stub, IStubElementType nodeType) {
        super(stub, nodeType);
    }

    @Override
    protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
        pyVisitor.visitPyTypeParameterList(this);
    }


    @Override
    public List<PyTypeParameter> getTypeParameters() {
        return List.of(getStubOrPsiChildren(PyStubElementTypes.TYPE_PARAMETER, new PyTypeParameter[0]));
    }
}
