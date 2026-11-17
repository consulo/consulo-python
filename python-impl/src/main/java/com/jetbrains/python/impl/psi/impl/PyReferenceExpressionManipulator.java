// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import consulo.annotation.component.ExtensionImpl;
import consulo.language.ast.ASTNode;
import consulo.document.util.TextRange;
import consulo.language.psi.AbstractElementManipulator;
import consulo.language.util.IncorrectOperationException;
import com.jetbrains.python.psi.PyReferenceExpression;

@ExtensionImpl
public final class PyReferenceExpressionManipulator extends AbstractElementManipulator<PyReferenceExpression> {
    @Override
    public PyReferenceExpression handleContentChange(PyReferenceExpression element,
                                                     TextRange range,
                                                     String newContent)
        throws IncorrectOperationException {
        return null;
    }

    @Override
    public TextRange getRangeInElement(PyReferenceExpression element) {
        ASTNode nameElement = element.getNameElement();
        int startOffset = nameElement != null ? nameElement.getStartOffset() : element.getTextRange().getEndOffset();
        return new TextRange(startOffset - element.getTextOffset(), element.getTextLength());
    }

    @Override
    public Class<PyReferenceExpression> getElementClass() {
        return PyReferenceExpression.class;
    }
}
