// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.postfix;

import consulo.document.Document;
import consulo.language.editor.postfixTemplate.PostfixTemplateProvider;
import consulo.language.editor.refactoring.postfixTemplate.EditablePostfixTemplateWithMultipleExpressions;
import consulo.language.editor.template.Template;
import consulo.language.psi.PsiElement;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.function.Predicates;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

@SuppressWarnings("PostfixTemplateDescriptionNotFound")
public class PyEditablePostfixTemplate extends EditablePostfixTemplateWithMultipleExpressions<PyPostfixTemplateExpressionCondition> {
    private final boolean myBuiltin;

    public PyEditablePostfixTemplate(String templateId,
                                     String templateName,
                                     Template liveTemplate,
                                     String example,
                                     Set<? extends PyPostfixTemplateExpressionCondition> conditions,
                                     boolean topmost,
                                     PostfixTemplateProvider provider,
                                     boolean builtin) {
        super(templateId, templateName, liveTemplate, example, conditions, topmost, provider);
        myBuiltin = builtin;
    }

    public PyEditablePostfixTemplate(String templateId,
                                     String templateName,
                                     String templateText,
                                     String example,
                                     Set<? extends PyPostfixTemplateExpressionCondition> conditions,
                                     boolean topmost,
                                     PostfixTemplateProvider provider,
                                     boolean builtin) {
        this(templateId, templateName, createTemplate(templateText), example, conditions, topmost, provider, builtin);
    }

    @Override
    protected List<PsiElement> getExpressions(PsiElement context, Document document, int offset) {
        List<PsiElement> expressions = (myUseTopmostExpression
            ? PyPostfixUtils.selectorTopmost()
            : PyPostfixUtils.selectorAllExpressionsWithCurrentOffset()).getExpressions(context, document, offset);
        Predicate<PsiElement> condition =
            Predicates.and(e -> e.getTextRange().getEndOffset() == offset, getExpressionCompositeCondition());
        return ContainerUtil.filter(expressions, condition);
    }

    @Override
    public boolean isBuiltin() {
        return myBuiltin;
    }

    @Override
    public boolean isEditable() {
        for (PyPostfixTemplateExpressionCondition it : getExpressionConditions()) {
            if (!(it != null && (PyPostfixTemplateExpressionCondition.PUBLIC_CONDITIONS.containsKey(it.getId()) ||
                PyPostfixTemplateExpressionCondition.PyClassCondition.ID.equals(it.getId())))) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected PsiElement getTopmostExpression(PsiElement element) {
        List<PsiElement> expressionsInRange = PyPostfixUtils.getAllExpressionsAtOffset(element.getContainingFile(), element.getTextOffset());
        PsiElement lastItem = ContainerUtil.getLastItem(expressionsInRange);
        return lastItem != null ? lastItem : element;
    }
}
