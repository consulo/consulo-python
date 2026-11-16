// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.PyStatementList;
import consulo.language.ast.ASTNode;
import consulo.language.ast.IElementType;
import consulo.language.ast.TokenType;
import consulo.language.impl.ast.ASTFactory;
import consulo.language.impl.ast.TreeElement;
import consulo.language.psi.PsiElement;
import org.jspecify.annotations.Nullable;


public class PyStatementListImpl extends PyLazyParseablePsiElement implements PyStatementList {

    public PyStatementListImpl(IElementType type, @Nullable CharSequence buffer) {
        super(type, buffer);
    }

    public PyStatementListImpl(@Nullable CharSequence buffer) {
        super(PyElementTypes.STATEMENT_LIST, buffer);
    }

    @Override
    protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
        pyVisitor.visitPyStatementList(this);
    }

    @Override
    public PyStatement[] getStatements() {
        return childrenToPsi(PythonDialectsTokenSetProvider.getInstance().getStatementTokens(), PyStatement.EMPTY_ARRAY);
    }

    @Override
    public TreeElement addInternal(TreeElement first, ASTNode last, @Nullable ASTNode anchor, @Nullable Boolean before) {
        if (first.getPsi() instanceof PyStatement && getStatements().length == 1) {
            ASTNode treePrev = getNode().getTreePrev();
            if (treePrev != null && treePrev.getElementType() == TokenType.WHITE_SPACE && !treePrev.textContains('\n')) {
                ASTNode lineBreak = ASTFactory.whitespace("\n");
                treePrev.getTreeParent().replaceChild(treePrev, lineBreak);
            }
        }
        return super.addInternal(first, last, anchor, before);
    }

    @Override
    public void deleteChildInternal(ASTNode child) {
        PsiElement childElement = child.getPsi();
        if (childElement instanceof PyStatement && getStatements().length == 1) {
            childElement.replace(PyElementGenerator.getInstance(getProject()).createPassStatement());
            return;
        }
        super.deleteChildInternal(child);
    }

    @Override
    public String toString() {
        return "PyStatementList";
    }
}
