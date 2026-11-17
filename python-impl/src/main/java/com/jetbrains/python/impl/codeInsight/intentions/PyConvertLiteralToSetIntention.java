// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.intentions;

import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PySequenceExpression;
import com.jetbrains.python.psi.PySetLiteralExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.util.collection.ContainerUtil;

/**
 * @author Mikhail Golubev
 */
@ExtensionImpl
@IntentionMetaData(ignoreId = "python.PyConvertLiteralToSetIntention", categories = "Python", fileExtensions = "py")
public final class PyConvertLiteralToSetIntention extends PyBaseConvertCollectionLiteralIntention {
    public PyConvertLiteralToSetIntention() {
        super(PySetLiteralExpression.class, "set", "{", "}");
    }

    @Override
    protected boolean isAvailableForCollection(PySequenceExpression literal) {
        return !literal.isEmpty() && LanguageLevel.forElement(literal).isAtLeast(LanguageLevel.PYTHON27) && !isInTargetPosition(literal);
    }

    private static boolean isInTargetPosition(PySequenceExpression sequenceLiteral) {
        return ContainerUtil.exists(sequenceLiteral.getElements(), expression -> expression instanceof PyTargetExpression);
    }
}
