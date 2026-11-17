// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.editor;

import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.PyRecursiveElementVisitor;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import consulo.annotation.component.ExtensionImpl;
import consulo.document.Document;
import consulo.language.Language;
import consulo.language.editor.PsiBasedStripTrailingSpacesFilter;
import consulo.language.editor.PsiBasedStripTrailingSpacesFilterFactory;
import consulo.language.psi.PsiFile;

@ExtensionImpl
public final class PyStripTrailingSpacesFilterFactory extends PsiBasedStripTrailingSpacesFilterFactory {
    @Override
    protected PsiBasedStripTrailingSpacesFilter createFilter(Document document) {
        return new PyStripTrailingSpacesFilter(document);
    }

    @Override
    protected boolean isApplicableTo(Language language) {
        return language.isKindOf(PythonLanguage.INSTANCE);
    }

    private static class PyStripTrailingSpacesFilter extends PsiBasedStripTrailingSpacesFilter {
        protected PyStripTrailingSpacesFilter(Document document) {
            super(document);
        }

        @Override
        protected void process(PsiFile psiFile) {
            psiFile.accept(new PyRecursiveElementVisitor() {
                @Override
                public void visitPyStringLiteralExpression(PyStringLiteralExpression node) {
                    if (node.isDocString()) {
                        return;
                    }
                    disableRange(node.getTextRange(), false);
                }
            });
        }
    }
}
