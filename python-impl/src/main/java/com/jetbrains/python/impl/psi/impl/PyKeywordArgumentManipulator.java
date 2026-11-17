// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import consulo.annotation.component.ExtensionImpl;
import consulo.language.ast.ASTNode;
import consulo.document.util.TextRange;
import consulo.language.psi.AbstractElementManipulator;
import consulo.language.util.IncorrectOperationException;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyKeywordArgument;

@ExtensionImpl
public final class PyKeywordArgumentManipulator extends AbstractElementManipulator<PyKeywordArgument> {
    @Override
    public PyKeywordArgument handleContentChange(PyKeywordArgument element, TextRange range, String newContent)
        throws IncorrectOperationException {
        ASTNode keywordNode = element.getKeywordNode();
        if (keywordNode != null && keywordNode.getPsi().getTextRangeInParent().equals(range)) {
            LanguageLevel langLevel = LanguageLevel.forElement(element);
            PyElementGenerator generator = PyElementGenerator.getInstance(element.getProject());
            PyCallExpression callExpression =
                (PyCallExpression)generator.createExpressionFromText(langLevel, "foo(" + newContent + "=None)");
            PyKeywordArgument kwArg = callExpression.getArgumentList().getKeywordArgument(newContent);
            element.getKeywordNode().getPsi().replace(kwArg.getKeywordNode().getPsi());
            return element;
        }
        throw new IncorrectOperationException("unsupported manipulation on keyword argument");
    }

    @Override
    public Class<PyKeywordArgument> getElementClass() {
        return PyKeywordArgument.class;
    }
}
