// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import com.jetbrains.python.impl.psi.types.PyCollectionTypeUtil;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyParenthesizedExpression;
import com.jetbrains.python.psi.PyTupleExpression;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.types.PyAnyType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.language.ast.ASTNode;
import org.jspecify.annotations.Nullable;

public class PyTupleExpressionImpl extends PySequenceExpressionImpl implements PyTupleExpression {
    public PyTupleExpressionImpl(ASTNode astNode) {
        super(astNode);
    }

    @Override
    protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
        pyVisitor.visitPyTupleExpression(this);
    }

    @Override
    public @Nullable PyType getType(TypeEvalContext context, TypeEvalContext.Key key) {
        PyType type = PyCollectionTypeUtil.getTupleLiteralType(this, context);
        return type != null ? type : PyAnyType.getUnknown();
    }

    @Override
    public void deleteChildInternal(ASTNode child) {
        super.deleteChildInternal(child);
        PyExpression[] children = getElements();
        PyElementGenerator generator = PyElementGenerator.getInstance(getProject());
        if (children.length == 1 && PyPsiUtils.getNextComma(children[0]) == null) {
            addAfter(generator.createComma().getPsi(), children[0]);
        }
        else if (children.length == 0 && !(getParent() instanceof PyParenthesizedExpression)) {
            replace(generator.createExpressionFromText(LanguageLevel.forElement(this), "()"));
        }
    }
}
