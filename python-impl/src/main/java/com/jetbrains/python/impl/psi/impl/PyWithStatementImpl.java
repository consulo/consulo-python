// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import com.jetbrains.python.ast.PyAstWithStatement;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyWithItem;
import com.jetbrains.python.psi.PyWithStatement;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiNamedElement;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

// TODO consulo: com.intellij.psi.PsiListLikeElement missing
public class PyWithStatementImpl extends PyElementImpl implements PyWithStatement {
    public PyWithStatementImpl(ASTNode astNode) {
        super(astNode);
    }

    @Override
    protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
        pyVisitor.visitPyWithStatement(this);
    }

    public @Nullable PsiNamedElement getNamedElement(String the_name) {
        return PyUtil.IterHelper.findName(getNamedElements(), the_name);
    }

    @Override
    public PyWithItem[] getWithItems() {
        return childrenToPsi(PyAstWithStatement.WITH_ITEM, PyWithItem.EMPTY_ARRAY);
    }

    public List<PyWithItem> getComponents() {
        return Arrays.asList(getWithItems());
    }

    @Override
    public boolean isSuppressingExceptions(TypeEvalContext context) {
        for (PyWithItem it : getWithItems()) {
            if (it.isSuppressingExceptions(context)) {
                return true;
            }
        }
        return false;
    }
}
