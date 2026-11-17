// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import com.jetbrains.python.psi.impl.PyPsiUtils;

import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiNamedElement;
import consulo.language.ast.TokenSet;
import consulo.util.collection.ArrayUtil;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyGlobalStatement;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.impl.psi.PyUtil;
import org.jspecify.annotations.Nullable;

import java.util.List;


// TODO consulo: com.intellij.psi.PsiListLikeElement missing
public class PyGlobalStatementImpl extends PyElementImpl implements PyGlobalStatement {
    private static final TokenSet TARGET_EXPRESSION_SET = TokenSet.create(PyElementTypes.TARGET_EXPRESSION);

    public PyGlobalStatementImpl(ASTNode astNode) {
        super(astNode);
    }

    @Override
    protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
        pyVisitor.visitPyGlobalStatement(this);
    }

    @Override
    public PyTargetExpression[] getGlobals() {
        return childrenToPsi(TARGET_EXPRESSION_SET, PyTargetExpression.EMPTY_ARRAY);
    }

    public @Nullable PsiNamedElement getNamedElement(String the_name) {
        return PyUtil.IterHelper.findName(getNamedElements(), the_name);
    }

    @Override
    public void addGlobal(String name) {
        PyElementGenerator pyElementGenerator = PyElementGenerator.getInstance(getProject());
        add(pyElementGenerator.createComma().getPsi());
        add(pyElementGenerator.createFromText(LanguageLevel.getDefault(), PyGlobalStatement.class, "global " + name).getGlobals()[0]);
    }

    @Override
    public void deleteChildInternal(ASTNode child) {
        if (ArrayUtil.contains(child.getPsi(), getGlobals())) {
            PyPsiUtils.deleteAdjacentCommaWithWhitespaces(this, child.getPsi());
        }
        super.deleteChildInternal(child);
    }

    public List<? extends PsiElement> getComponents() {
        return getNamedElements();
    }
}
