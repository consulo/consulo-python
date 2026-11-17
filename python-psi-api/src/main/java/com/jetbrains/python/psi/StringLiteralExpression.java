// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi;

import consulo.document.util.TextRange;
import consulo.language.psi.PsiElement;

/**
 * A generic string literal interface (used both in Python code and template files).
 */
public interface StringLiteralExpression extends PsiElement {
    String getStringValue();
    TextRange getStringValueTextRange();
}
