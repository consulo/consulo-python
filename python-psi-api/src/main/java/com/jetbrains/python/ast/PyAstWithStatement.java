// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ast;

import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.ast.impl.PyUtilCore;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.ast.TokenSet;
import consulo.language.psi.PsiNamedElement;
import consulo.language.psi.util.PsiTreeUtil;

import java.util.ArrayList;
import java.util.List;

public interface PyAstWithStatement extends PyAstCompoundStatement, PyAstNamedElementContainer, PyAstStatementListContainer {
    TokenSet WITH_ITEM = TokenSet.create(PyElementTypes.WITH_ITEM);

    @Override
    @RequiredReadAction
    default List<PsiNamedElement> getNamedElements() {
        PyAstWithItem[] items = PsiTreeUtil.getChildrenOfType(this, PyAstWithItem.class);
        List<PsiNamedElement> result = new ArrayList<>();
        if (items != null) {
            for (PyAstWithItem item : items) {
                PyAstExpression targetExpression = item.getTarget();
                List<PyAstExpression> expressions = PyUtilCore.flattenedParensAndTuples(targetExpression);
                for (PyAstExpression expression : expressions) {
                    if (expression instanceof PsiNamedElement) {
                        result.add((PsiNamedElement)expression);
                    }
                }
            }
        }
        return result;
    }

    @RequiredReadAction
    default PyAstWithItem[] getWithItems() {
        return childrenToPsi(WITH_ITEM, PyAstWithItem.EMPTY_ARRAY);
    }

    @Override
    @RequiredReadAction
    default PyAstStatementList getStatementList() {
        PyAstStatementList statementList = childToPsi(PyElementTypes.STATEMENT_LIST);
        assert statementList != null : "Statement list missing for with statement " + getText();
        return statementList;
    }

    @RequiredReadAction
    default boolean isAsync() {
        return getNode().findChildByType(PyTokenTypes.ASYNC_KEYWORD) != null;
    }

    @Override
    default void acceptPyVisitor(PyAstElementVisitor pyVisitor) {
        pyVisitor.visitPyWithStatement(this);
    }
}
