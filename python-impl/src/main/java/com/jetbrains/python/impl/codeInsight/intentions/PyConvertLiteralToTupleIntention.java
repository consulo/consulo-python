// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.intentions;

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PySequenceExpression;
import com.jetbrains.python.psi.PyTupleExpression;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.ast.ASTNode;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.PsiElement;

/**
 * @author Mikhail Golubev
 */
@ExtensionImpl
@IntentionMetaData(ignoreId = "python.PyConvertLiteralToTupleIntention", categories = "Python", fileExtensions = "py")
public final class PyConvertLiteralToTupleIntention extends PyBaseConvertCollectionLiteralIntention {
    public PyConvertLiteralToTupleIntention() {
        super(PyTupleExpression.class, "tuple", "(", ")");
    }

    @Override
    protected PsiElement prepareOriginalElementCopy(PsiElement copy) {
        PySequenceExpression sequenceExpression = unwrapCollection(copy);
        PyExpression[] elements = sequenceExpression.getElements();
        if (elements.length == 1) {
            PyExpression onlyElement = elements[0];
            PsiElement next = PyPsiUtils.getNextNonCommentSibling(onlyElement, true);
            if (next != null && next.getNode().getElementType() != PyTokenTypes.COMMA) {
                PyElementGenerator generator = PyElementGenerator.getInstance(copy.getProject());
                ASTNode anchor = onlyElement.getNode().getTreeNext();
                sequenceExpression.getNode().addChild(generator.createComma(), anchor);
            }
        }
        return copy;
    }
}
