// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.intentions;

import com.jetbrains.python.psi.PyListLiteralExpression;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.intention.IntentionMetaData;

/**
 * @author Mikhail Golubev
 */
@ExtensionImpl
@IntentionMetaData(ignoreId = "python.PyConvertLiteralToListIntention", categories = "Python", fileExtensions = "py")
public final class PyConvertLiteralToListIntention extends PyBaseConvertCollectionLiteralIntention {
    public PyConvertLiteralToListIntention() {
        super(PyListLiteralExpression.class, "list", "[", "]");
    }
}
