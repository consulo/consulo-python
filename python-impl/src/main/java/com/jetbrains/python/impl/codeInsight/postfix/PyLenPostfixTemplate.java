// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.postfix;

import consulo.language.editor.postfixTemplate.PostfixTemplateProvider;

import java.util.Set;

public class PyLenPostfixTemplate extends PyEditablePostfixTemplate {
    public PyLenPostfixTemplate(PostfixTemplateProvider provider) {
        super(
            "len",
            "len",
            "len($EXPR$)$END$",
            "len(expr)",
            Set.of(new PyPostfixTemplateExpressionCondition.PyBuiltinLenApplicable()),
            false,
            provider,
            true
        );
    }
}
