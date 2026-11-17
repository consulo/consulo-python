// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.postfix;

import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.impl.validation.PyImplicitAsyncContextProvider;
import consulo.application.dumb.DumbAware;
import consulo.language.editor.postfixTemplate.PostfixTemplateProvider;
import consulo.language.psi.PsiElement;

import java.util.function.Predicate;

public class PyAwaitPostfixTemplate extends StringBasedPostfixTemplate implements DumbAware {
    // "await" is only valid inside an async function (or an implicit async context such as the Python console).
    private static final Predicate<PsiElement> IN_ASYNC_CONTEXT =
        element -> PyImplicitAsyncContextProvider.isAsyncAllowed(ScopeUtil.getScopeOwner(element));

    public PyAwaitPostfixTemplate(PostfixTemplateProvider provider) {
        super("await", "await expr", PyPostfixUtils.selectorAllExpressionsWithCurrentOffset(IN_ASYNC_CONTEXT), provider);
    }

    @Override
    public String getTemplateString(PsiElement element) {
        return "await $expr$$END$";
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
