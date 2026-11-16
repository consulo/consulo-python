// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
/*
 * Copyright 2013-2026 consulo.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.impl.psi.impl;

import com.jetbrains.python.impl.PyStubElementTypes;
import com.jetbrains.python.impl.codeInsight.PyTypingTypeProvider;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.impl.psi.types.PyClassTypeImpl;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyPsiFacade;
import com.jetbrains.python.psi.PyTypeParameter;
import com.jetbrains.python.psi.stubs.PyTypeParameterStub;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;
import consulo.language.psi.stub.IStubElementType;
import consulo.language.util.IncorrectOperationException;
import org.jspecify.annotations.Nullable;

public class PyTypeParameterImpl extends PyBaseElementImpl<PyTypeParameterStub> implements PyTypeParameter {

    public PyTypeParameterImpl(ASTNode astNode) {
        super(astNode);
    }

    public PyTypeParameterImpl(PyTypeParameterStub stub) {
        this(stub, PyStubElementTypes.TYPE_PARAMETER);
    }

    public PyTypeParameterImpl(PyTypeParameterStub stub, IStubElementType nodeType) {
        super(stub, nodeType);
    }

    @Override
    protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
        pyVisitor.visitPyTypeParameter(this);
    }

    @Override
    public @Nullable String getName() {
        PyTypeParameterStub stub = getStub();

        if (stub != null) {
            return stub.getName();
        }
        else {
            return PyTypeParameter.super.getName();
        }
    }

    @Override
    public @Nullable String getBoundExpressionText() {
        PyTypeParameterStub stub = getStub();
        if (stub != null) {
            return stub.getBoundExpressionText();
        }

        return PyTypeParameter.super.getBoundExpressionText();
    }

    @Override
    public @Nullable String getDefaultExpressionText() {
        PyTypeParameterStub stub = getStub();
        if (stub != null) {
            return stub.getDefaultExpressionText();
        }

        return PyTypeParameter.super.getDefaultExpressionText();
    }

    @Override
    public PyTypeParameter.Kind getKind() {
        PyTypeParameterStub stub = getStub();

        if (stub != null) {
            return stub.getKind();
        }
        else {
            return PyTypeParameter.super.getKind();
        }
    }

    @Override
    public PsiElement setName(String name) throws IncorrectOperationException {
        PsiElement identifier = getNameIdentifier();
        if (identifier != null) {
            ASTNode newName = PyUtil.createNewName(this, name);
            ASTNode nameNode = identifier.getNode();

            getNode().replaceChild(nameNode, newName);
        }
        return this;
    }

    @Override
    public @Nullable PyType getType(TypeEvalContext context, TypeEvalContext.Key key) {
        PyPsiFacade facade = PyPsiFacade.getInstance(this.getProject());
        Kind kind = this.getKind();
        PyClass pyClass = switch (kind) {
            case TypeVar -> facade.createClassByQName(PyTypingTypeProvider.TYPE_VAR, this);
            case TypeVarTuple -> facade.createClassByQName(PyTypingTypeProvider.TYPE_VAR_TUPLE, this);
            case ParamSpec -> facade.createClassByQName(PyTypingTypeProvider.PARAM_SPEC, this);
        };
        if (pyClass != null) {
            return new PyClassTypeImpl(pyClass, false);
        }
        // TODO consulo: JB returns PyAnyType.getUnknown(), which is null while the "python.type.any" support is disabled;
        //  PyAnyType is not ported, so Consulo keeps the disabled-support result
        return null;
    }

    @Override
    public @Nullable String getQualifiedName() {
        return QualifiedNameFinder.getQualifiedName(this);
    }
}
