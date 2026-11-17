// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.postfix;

import consulo.application.dumb.DumbAware;
import consulo.language.editor.postfixTemplate.PostfixTemplateProvider;
import consulo.language.editor.template.Template;
import consulo.language.editor.template.TextExpression;
import consulo.language.psi.PsiElement;

/**
 * Turns a context-manager expression into a {@code with} block: {@code open(p).with} -> {@code with open(p) as f:}.
 */
public class PyWithPostfixTemplate extends StringBasedPostfixTemplate implements DumbAware {
    public PyWithPostfixTemplate(PostfixTemplateProvider provider) {
        super("with", "with expr as name:", PyPostfixUtils.selectorTopmost(), provider);
    }

    @Override
    public String getTemplateString(PsiElement element) {
        return "with $expr$ as $name$:\n    $END$";
    }

    @Override
    public void setVariables(Template template, PsiElement element) {
        template.addVariable("name", new TextExpression(""), true);
    }

    @Override
    protected boolean shouldReformat() {
        return false;
    }
}
