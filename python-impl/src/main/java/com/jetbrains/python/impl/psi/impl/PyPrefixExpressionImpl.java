/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.impl.PyElementTypes;
import com.jetbrains.python.impl.PythonDialectsTokenSetProvider;
import com.jetbrains.python.impl.psi.impl.references.PyOperatorReference;
import com.jetbrains.python.impl.psi.types.PyCollectionType;
import com.jetbrains.python.impl.psi.types.PyUnionType;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.access.RequiredReadAction;
import consulo.ide.impl.idea.util.containers.ContainerUtil;
import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiPolyVariantReference;
import consulo.language.psi.PsiReference;
import consulo.language.psi.util.QualifiedName;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class PyPrefixExpressionImpl extends PyElementImpl implements PyPrefixExpression {
    public PyPrefixExpressionImpl(ASTNode astNode) {
        super(astNode);
    }

    @Override
    public PyExpression getOperand() {
        return (PyExpression) childToPsi(PythonDialectsTokenSetProvider.INSTANCE.getExpressionTokens(), 0);
    }

    @Nullable
    @RequiredReadAction
    public PsiElement getPsiOperator() {
        ASTNode node = getNode();
        ASTNode child = node.findChildByType(PyElementTypes.UNARY_OPS);
        return child != null ? child.getPsi() : null;
    }

    @Nonnull
    @Override
    @RequiredReadAction
    public PyElementType getOperator() {
        PsiElement op = getPsiOperator();
        assert op != null;
        return (PyElementType) op.getNode().getElementType();
    }

    @Override
    protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
        pyVisitor.visitPyPrefixExpression(this);
    }

    @Override
    public PsiReference getReference() {
        return getReference(PyResolveContext.noImplicits());
    }

    @Nonnull
    @Override
    public PsiPolyVariantReference getReference(PyResolveContext context) {
        return new PyOperatorReference(this, context);
    }

    @Override
    @RequiredReadAction
    public PyType getType(@Nonnull TypeEvalContext context, @Nonnull TypeEvalContext.Key key) {
        if (getOperator() == PyTokenTypes.NOT_KEYWORD) {
            return PyBuiltinCache.getInstance(this).getBoolType();
        }
        boolean isAwait = getOperator() == PyTokenTypes.AWAIT_KEYWORD;
        if (isAwait) {
            PyExpression operand = getOperand();
            if (operand != null) {
                PyType operandType = context.getType(operand);
                PyType type = getGeneratorReturnType(operandType, context);
                if (type != null) {
                    return type;
                }
            }
        }
        PsiReference ref = getReference(PyResolveContext.noImplicits().withTypeEvalContext(context));
        PsiElement resolved = ref.resolve();
        if (resolved instanceof PyCallable callable) {
            // TODO: Make PyPrefixExpression a PyCallSiteExpression, use getCallType() here and analyze it in PyTypeChecker.analyzeCallSite()
            PyType returnType = callable.getReturnType(context, key);
            return isAwait ? getGeneratorReturnType(returnType, context) : returnType;
        }
        return null;
    }

    @Override
    public PyExpression getQualifier() {
        return getOperand();
    }

    @Nullable
    @Override
    public QualifiedName asQualifiedName() {
        return PyPsiUtils.asQualifiedName(this);
    }

    @Override
    public boolean isQualified() {
        return getQualifier() != null;
    }

    @Override
    @RequiredReadAction
    public String getReferencedName() {
        PyElementType t = getOperator();
        if (t == PyTokenTypes.PLUS) {
            return PyNames.POS;
        }
        else if (t == PyTokenTypes.MINUS) {
            return PyNames.NEG;
        }
        return getOperator().getSpecialMethodName();
    }

    @Override
    @RequiredReadAction
    public ASTNode getNameElement() {
        PsiElement op = getPsiOperator();
        return op != null ? op.getNode() : null;
    }

    @Nullable
    private static PyType getGeneratorReturnType(@Nullable PyType type, @Nonnull TypeEvalContext context) {
        if (type instanceof PyClassLikeType classLikeType && type instanceof PyCollectionType collectionType) {
            // TODO: Understand typing.Generator as well
            String classQName = classLikeType.getClassQName();
            if (PyNames.FAKE_GENERATOR.equals(classQName)) {
                return ContainerUtil.getOrElse(collectionType.getElementTypes(context), 2, null);
            }
            else if (PyNames.FAKE_COROUTINE.equals(classQName)
                || type instanceof PyClassType classType && PyNames.AWAITABLE.equals(classType.getPyClass().getName())) {
                return collectionType.getIteratedItemType();
            }
        }
        else if (type instanceof PyUnionType unionType) {
            List<PyType> memberReturnTypes = new ArrayList<>();
            for (PyType member : unionType.getMembers()) {
                memberReturnTypes.add(getGeneratorReturnType(member, context));
            }
            return PyUnionType.union(memberReturnTypes);
        }
        return null;
    }
}
