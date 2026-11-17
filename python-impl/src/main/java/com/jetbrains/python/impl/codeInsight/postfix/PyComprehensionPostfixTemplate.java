// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.postfix;

import com.jetbrains.python.impl.codeInsight.liveTemplates.CollectionElementNameMacro;
import consulo.language.editor.postfixTemplate.PostfixTemplateProvider;
import consulo.language.editor.template.Template;
import consulo.language.editor.template.TextExpression;
import consulo.language.editor.template.VariableNode;
import consulo.language.editor.template.macro.MacroCallNode;
import consulo.language.psi.PsiElement;

import java.util.Set;

/**
 * Builds a list/set/generator comprehension over an iterable expression, e.g. {@code nums.compl} -> {@code [n for n in nums]}.
 */
public class PyComprehensionPostfixTemplate extends PyEditablePostfixTemplate {
    public PyComprehensionPostfixTemplate(String name, String template, String example, PostfixTemplateProvider provider) {
        super(
            name,
            name,
            template,
            example,
            Set.of(new PyPostfixTemplateExpressionCondition.PyIterable()),
            false,
            provider,
            true
        );
    }

    @Override
    protected void addTemplateVariables(PsiElement element, Template template) {
        super.addTemplateVariables(element, template);
        MacroCallNode varName = new MacroCallNode(new CollectionElementNameMacro());
        varName.addParameter(new VariableNode("EXPR", null));
        template.addVariable("VAR", varName, new TextExpression("e"), true);
        template.addVariable("VAR_EXPR", new VariableNode("VAR", null), new VariableNode("VAR", null), true);
    }
}
