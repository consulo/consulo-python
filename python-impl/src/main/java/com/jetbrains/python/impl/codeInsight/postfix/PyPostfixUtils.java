// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.postfix;

import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyExpressionStatement;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyStatement;
import consulo.document.Document;
import consulo.language.editor.postfixTemplate.PostfixTemplatePsiInfo;
import consulo.language.editor.refactoring.postfixTemplate.PostfixTemplateExpressionSelector;
import consulo.language.editor.refactoring.postfixTemplate.PostfixTemplateExpressionSelectorBase;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiUtilCore;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.function.Predicates;

import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

public final class PyPostfixUtils {

    private PyPostfixUtils() {
    }

    public static final PostfixTemplatePsiInfo PY_PSI_INFO = new PostfixTemplatePsiInfo() {
        @Override
        public PsiElement createExpression(PsiElement context, String prefix, String suffix) {
            String text = prefix + context.getText() + suffix;
            return PyElementGenerator.getInstance(context.getProject()).createExpressionFromText(LanguageLevel.forElement(context), text);
        }

        @Override
        public PsiElement getNegatedExpression(PsiElement element) {
            String newText = element instanceof PyBinaryExpression ? "(" + element.getText() + ")" : element.getText();
            return PyElementGenerator.getInstance(element.getProject()).
                createExpressionFromText(LanguageLevel.forElement(element), "not " + newText);
        }
    };


    public static PostfixTemplateExpressionSelector selectorAllExpressionsWithCurrentOffset(Predicate<? super PsiElement> additionalFilter) {
        return new PostfixTemplateExpressionSelectorBase(additionalFilter) {
            @Override
            protected List<PsiElement> getNonFilteredExpressions(PsiElement context, Document document, int newOffset) {
                return getAllExpressionsAtOffset(context.getContainingFile(), newOffset - 1);
            }
        };
    }

    public static List<PsiElement> getAllExpressionsAtOffset(PsiFile file, int offset) {
        PsiElement elementAtCaret = PsiUtilCore.getElementAtOffset(file, offset);
        return PsiTreeUtil.collectParents(elementAtCaret, PyExpression.class, true, e -> e instanceof PyStatement || e instanceof PyFile);
    }

    public static PostfixTemplateExpressionSelector selectorAllExpressionsWithCurrentOffset() {
        return selectorAllExpressionsWithCurrentOffset(Predicates.alwaysTrue());
    }

    public static PostfixTemplateExpressionSelector selectorTopmost() {
        return new PostfixTemplateExpressionSelectorBase(null) {
            @Override
            protected List<PsiElement> getNonFilteredExpressions(PsiElement context, Document document, int offset) {
                PyExpressionStatement exprStatement = PsiTreeUtil.getNonStrictParentOfType(context, PyExpressionStatement.class);
                PyExpression expression = exprStatement != null ? exprStatement.getExpression() : null;
                return ContainerUtil.createMaybeSingletonList(expression);
            }
        };
    }

    public static PostfixTemplateExpressionSelector currentStatementSelector() {
        return new PostfixTemplateExpressionSelectorBase(null) {
            @Override
            protected List<PsiElement> getNonFilteredExpressions(PsiElement context, Document document, int offset) {
                PsiElement elementAtCaret = PsiUtilCore.getElementAtOffset(context.getContainingFile(), offset - 1);
                if (elementAtCaret instanceof PsiComment) {
                    return Collections.emptyList();
                }
                return ContainerUtil.createMaybeSingletonList(PsiTreeUtil.getParentOfType(elementAtCaret, PyStatement.class));
            }
        };
    }
}
