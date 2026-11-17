// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.postfix;

import consulo.application.dumb.DumbAware;
import consulo.language.editor.postfixTemplate.PostfixTemplateProvider;
import consulo.language.psi.PsiElement;

/**
 * Prefixes a whole expression statement with a keyword such as {@code raise} or {@code yield}:
 * {@code expr.raise} -> {@code raise expr}.
 */
public class PyStatementKeywordPostfixTemplate extends StringBasedPostfixTemplate implements DumbAware {
    private final String myKeyword;

    public PyStatementKeywordPostfixTemplate(String keyword, PostfixTemplateProvider provider) {
        super(keyword, keyword + " expr", PyPostfixUtils.selectorTopmost(), provider);
        myKeyword = keyword;
    }

    @Override
    public String getTemplateString(PsiElement element) {
        return myKeyword + " $expr$$END$";
    }

    @Override
    protected boolean shouldReformat() {
        return false;
    }
}
