// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.postfix;

import consulo.application.dumb.DumbAware;
import consulo.language.editor.postfixTemplate.PostfixTemplateProvider;
import consulo.language.psi.PsiElement;

/**
 * Wraps an expression with a call to a built-in such as {@code str}, {@code list}, {@code set}, {@code dict} or {@code tuple}:
 * {@code expr.list} -> {@code list(expr)}.
 */
public class PyCallWrapPostfixTemplate extends StringBasedPostfixTemplate implements DumbAware {
    private final String myFunction;

    public PyCallWrapPostfixTemplate(String function, PostfixTemplateProvider provider) {
        super(function, function + "(expr)", PyPostfixUtils.selectorAllExpressionsWithCurrentOffset(), provider);
        myFunction = function;
    }

    @Override
    public String getTemplateString(PsiElement element) {
        return myFunction + "($expr$)$END$";
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
