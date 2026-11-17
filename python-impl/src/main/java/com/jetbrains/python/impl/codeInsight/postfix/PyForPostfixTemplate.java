// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.postfix;

import com.jetbrains.python.impl.codeInsight.liveTemplates.CollectionElementNameMacro;
import consulo.language.editor.postfixTemplate.PostfixTemplateProvider;
import consulo.language.editor.template.Template;
import consulo.language.editor.template.TextExpression;
import consulo.language.editor.template.VariableNode;
import consulo.language.editor.template.macro.MacroCallNode;
import consulo.language.psi.PsiElement;

import java.util.Set;

public class PyForPostfixTemplate extends PyEditablePostfixTemplate {
    public PyForPostfixTemplate(String name, PostfixTemplateProvider provider) {
        super(
            name,
            name,
            "for $VAR$ in $EXPR$:\n    $END$",
            "for e in expr",
            Set.of(new PyPostfixTemplateExpressionCondition.PyIterable()),
            false,
            provider,
            true
        );
    }

    @Override
    protected void addTemplateVariables(PsiElement element, Template template) {
        super.addTemplateVariables(element, template);
        MacroCallNode name = new MacroCallNode(new CollectionElementNameMacro());
        name.addParameter(new VariableNode("EXPR", null));
        template.addVariable("VAR", name, new TextExpression("e"), true);
    }
}
