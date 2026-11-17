// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.postfix;

import consulo.application.dumb.DumbAware;
import consulo.language.editor.postfixTemplate.PostfixTemplateProvider;
import consulo.language.psi.PsiElement;

public class PyIsInstancePostfixTemplate extends StringBasedPostfixTemplate implements DumbAware {
    public PyIsInstancePostfixTemplate(PostfixTemplateProvider provider) {
        super("isinstance", "isinstance(expr, )", PyPostfixUtils.selectorAllExpressionsWithCurrentOffset(), provider);
    }

    @Override
    public String getTemplateString(PsiElement element) {
        return "isinstance($expr$, $END$)";
    }

    @Override
    protected PsiElement getElementToRemove(PsiElement expr) {
        return expr;
    }

    @Override
    protected boolean shouldReformat() {
        return false;
    }
}
