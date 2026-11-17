// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.validation;

import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.impl.inspections.PythonVisitorFilter;
import consulo.language.Language;
import consulo.language.impl.file.MultiplePsiFilesPerDocumentFileViewProvider;
import consulo.language.psi.PsiFile;

public final class PyMultiplePsiFilesVisitorFilter implements PythonVisitorFilter {
    @Override
    public boolean isSupported(Class visitorClass, PsiFile file) {
        if (visitorClass == PyStringLiteralQuotesAnnotatorVisitor.class &&
            file.getViewProvider() instanceof MultiplePsiFilesPerDocumentFileViewProvider) {
            return false;
        }
        return true;
    }

    // Consulo: PythonVisitorFilter is a LanguageExtension
    @Override
    public Language getLanguage() {
        return PythonLanguage.INSTANCE;
    }
}
