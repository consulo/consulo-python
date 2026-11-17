// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.postfix;

import consulo.application.dumb.DumbAware;
import consulo.language.editor.postfixTemplate.PostfixTemplateProvider;
import consulo.language.psi.PsiElement;
import org.jspecify.annotations.Nullable;

public class PyReturnPostfixTemplate extends StringBasedPostfixTemplate implements DumbAware {
    public PyReturnPostfixTemplate(PostfixTemplateProvider provider) {
        super("return", "return expr", PyPostfixUtils.selectorTopmost(), provider);
    }


    @Override
    public @Nullable String getTemplateString(PsiElement element) {
        return "return $expr$$END$";
    }
}
