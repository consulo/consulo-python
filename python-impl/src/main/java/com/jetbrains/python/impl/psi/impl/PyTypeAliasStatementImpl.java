// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import consulo.ui.image.Image;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.language.ast.ASTNode;
import consulo.navigation.ItemPresentation;
import consulo.language.psi.PsiElement;
import consulo.language.psi.stub.IStubElementType;
import consulo.language.util.IncorrectOperationException;
import com.jetbrains.python.impl.PyStubElementTypes;
import com.jetbrains.python.impl.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.impl.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyPsiFacade;
import com.jetbrains.python.psi.PyTypeAliasStatement;
import com.jetbrains.python.psi.PyTypeParameterList;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.psi.stubs.PyTypeAliasStatementStub;
import com.jetbrains.python.psi.types.PyAnyType;
import com.jetbrains.python.impl.psi.types.PyClassTypeImpl;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jspecify.annotations.Nullable;


public class PyTypeAliasStatementImpl extends PyBaseElementImpl<PyTypeAliasStatementStub> implements PyTypeAliasStatement {
    public PyTypeAliasStatementImpl(ASTNode astNode) {
        super(astNode);
    }

    public PyTypeAliasStatementImpl(PyTypeAliasStatementStub stub) {
        this(stub, PyStubElementTypes.TYPE_ALIAS_STATEMENT);
    }

    public PyTypeAliasStatementImpl(PyTypeAliasStatementStub stub, IStubElementType nodeType) {
        super(stub, nodeType);
    }


    @Override
    protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
        pyVisitor.visitPyTypeAliasStatement(this);
    }

    @Override
    public @Nullable String getTypeExpressionText() {
        PyTypeAliasStatementStub stub = getStub();

        if (stub != null) {
            return stub.getTypeExpressionText();
        }
        else {
            PyExpression typeExpression = getTypeExpression();
            return typeExpression != null ? typeExpression.getText() : null;
        }
    }

    @Override
    public @Nullable PyTypeParameterList getTypeParameterList() {
        return getStubOrPsiChild(PyStubElementTypes.TYPE_PARAMETER_LIST);
    }

    @Override
    public String getName() {
        PyTypeAliasStatementStub stub = getStub();

        if (stub != null) {
            return stub.getName();
        }
        else {
            PsiElement identifier = getNameIdentifier();
            return identifier != null ? identifier.getText() : null;
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
        PyClass typeAliasTypeClass = facade.createClassByQName(PyTypingTypeProvider.TYPE_ALIAS_TYPE, this);
        if (typeAliasTypeClass != null) {
            return new PyClassTypeImpl(typeAliasTypeClass, false);
        }
        return PyAnyType.getUnknown();
    }

    @Override
    public ItemPresentation getPresentation() {
        return new PyElementPresentation(this);
    }

    // TODO consulo: Consulo resolves PSI icons through PyIconDescriptorUpdater, this method is kept for parity with JB
    public Image getIcon(int flags) {
        return PlatformIconGroup.nodesType();
    }

    @Override
    public int getTextOffset() {
        @Nullable PsiElement name = getNameIdentifier();
        return name != null ? name.getTextOffset() : super.getTextOffset();
    }

    @Override
    public void subtreeChanged() {
        super.subtreeChanged();
        ControlFlowCache.clear(this);
    }

    @Override
    public @Nullable String getQualifiedName() {
        return QualifiedNameFinder.getQualifiedName(this);
    }
}
