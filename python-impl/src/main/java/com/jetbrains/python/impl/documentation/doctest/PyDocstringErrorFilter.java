// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.documentation.doctest;

import com.jetbrains.python.impl.documentation.docstrings.DocStringUtil;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.HighlightErrorFilter;
import consulo.language.psi.PsiErrorElement;
import consulo.language.psi.PsiFile;

/**
 * User : ktisha
 *
 * Do not highlight syntax errors in doctests
 */
@ExtensionImpl
public final class PyDocstringErrorFilter extends HighlightErrorFilter {

    @Override
    public boolean shouldHighlightErrorElement(PsiErrorElement element) {
        final PsiFile file = element.getContainingFile();
        if (file instanceof PyDoctestFile) {
            return false;
        }

        return DocStringUtil.getDocstringInjectionHost(file) == null;
    }
}
