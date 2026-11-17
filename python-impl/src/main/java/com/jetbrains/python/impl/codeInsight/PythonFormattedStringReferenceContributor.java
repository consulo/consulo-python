// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight;

import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.pattern.PatternCondition;
import consulo.language.pattern.PsiElementPattern;
import consulo.language.psi.PsiReferenceContributor;
import consulo.language.psi.PsiReferenceRegistrar;
import consulo.language.util.ProcessingContext;

import static consulo.language.pattern.PlatformPatterns.psiElement;


@ExtensionImpl
public final class PythonFormattedStringReferenceContributor extends PsiReferenceContributor {
    static final class Holder {
        private static final PsiElementPattern.Capture<PyStringLiteralExpression> PERCENT_STRING_PATTERN =
            psiElement(PyStringLiteralExpression.class).beforeLeaf(psiElement().withText("%")).withParent(PyBinaryExpression.class);
        static final PsiElementPattern.Capture<PyStringLiteralExpression> FORMAT_STRING_PATTERN =
            psiElement(PyStringLiteralExpression.class)
                .withParent(psiElement(PyReferenceExpression.class)
                    .with(new PatternCondition<>("isFormatFunction") {

                        @Override
                        public boolean accepts(PyReferenceExpression expression, ProcessingContext context) {
                            String expressionName = expression.getName();
                            return expressionName != null && expressionName.equals("format");
                        }
                    }))
                .withSuperParent(2, PyCallExpression.class);
    }

    @Override
    public void registerReferenceProviders(PsiReferenceRegistrar registrar) {

        registrar.registerReferenceProvider(psiElement().andOr(Holder.PERCENT_STRING_PATTERN, Holder.FORMAT_STRING_PATTERN),
            new PythonFormattedStringReferenceProvider());
    }

    @Override
    public Language getLanguage() {
        return PythonLanguage.INSTANCE;
    }
}
