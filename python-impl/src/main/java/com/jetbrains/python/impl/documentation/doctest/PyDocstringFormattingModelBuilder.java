// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.documentation.doctest;

import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.codeStyle.AbstractBlock;
import consulo.language.codeStyle.Block;
import consulo.language.codeStyle.CustomFormattingModelBuilder;
import consulo.language.codeStyle.DocumentBasedFormattingModel;
import consulo.language.codeStyle.FormattingContext;
import consulo.language.codeStyle.FormattingModel;
import consulo.language.codeStyle.Spacing;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;

@ExtensionImpl
public class PyDocstringFormattingModelBuilder implements CustomFormattingModelBuilder {
    @Override
    public boolean isEngagedToFormat(PsiElement context) {
        PsiFile containingFile = context.getContainingFile();
        return containingFile != null && containingFile.getLanguage() == PyDoctestLanguageDialect.getInstance();
    }

    @Override
    public FormattingModel createModel(FormattingContext formattingContext) {
        return new DocumentBasedFormattingModel(new PyDocstringBlock(formattingContext),
            formattingContext.getCodeStyleSettings(),
            formattingContext.getContainingFile());
    }

    @Override
    public Language getLanguage() {
        return PyDoctestLanguageDialect.getInstance();
    }

    public static class PyDocstringBlock extends AbstractBlock {
        public PyDocstringBlock(FormattingContext formattingContext) {
            super(formattingContext.getNode(), null, null);
        }

        @Override
        public @Nullable Spacing getSpacing(@Nullable Block child1, Block child2) {
            return null;
        }

        @Override
        public boolean isLeaf() {
            return true;
        }

        @Override
        protected List<Block> buildChildren() {
            return Collections.emptyList();
        }
    }
}
