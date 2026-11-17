// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyParenthesizedExpression;
import com.jetbrains.python.psi.PySequenceExpression;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.pattern.PatternCondition;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.PsiReferenceContributor;
import consulo.language.psi.PsiReferenceProvider;
import consulo.language.psi.PsiReferenceRegistrar;
import consulo.language.util.ProcessingContext;

import static consulo.language.pattern.PlatformPatterns.psiElement;


@ExtensionImpl
public final class PyStdReferenceContributor extends PsiReferenceContributor {
    private static class Holder {
        private static final PatternCondition<PsiElement> IN_OPTIONAL_PARENTHESIS_INSIDE_ASSIGNMENT =
            new PatternCondition<>("in optional parenthesis inside assignment") {
                @Override
                public boolean accepts(PsiElement element, ProcessingContext context) {
                    PsiElement parent = element.getParent();
                    while (parent instanceof PyParenthesizedExpression) {
                        parent = parent.getParent();
                    }
                    return parent instanceof PyAssignmentStatement;
                }
            };
    }

    @Override
    public void registerReferenceProviders(PsiReferenceRegistrar registrar) {
        registerClassAttributeReference(registrar, PyNames.ALL, new PsiReferenceProvider() {
            @Override
            public PsiReference[] getReferencesByElement(PsiElement element,
                                                         ProcessingContext context) {
                return new PsiReference[]{new PyDunderAllReference((PyStringLiteralExpression) element)};
            }
        });

        registerClassAttributeReference(registrar, PyNames.SLOTS, new PsiReferenceProvider() {
            @Override
            public PsiReference[] getReferencesByElement(PsiElement element,
                                                         ProcessingContext context) {
                return new PsiReference[]{new PyDunderSlotsReference((PyStringLiteralExpression) element)};
            }
        });

        registerClassAttributeReference(registrar, PyNames.MATCH_ARGS, new PsiReferenceProvider() {
            @Override
            public PsiReference[] getReferencesByElement(PsiElement element,
                                                         ProcessingContext context) {
                return new PsiReference[]{new PyDunderMatchArgsReference((PyStringLiteralExpression) element)};
            }
        });
    }

    private static void registerClassAttributeReference(PsiReferenceRegistrar registrar,
                                                        String name,
                                                        PsiReferenceProvider provider) {
        registrar.registerReferenceProvider(psiElement(PyStringLiteralExpression.class)
            .withParent(psiElement(PySequenceExpression.class)
                .with(Holder.IN_OPTIONAL_PARENTHESIS_INSIDE_ASSIGNMENT)
                .inside(true, psiElement(PyAssignmentStatement.class)
                    .withFirstChild(psiElement(PyTargetExpression.class).withName(name)))), provider);
    }

    @Override
    public Language getLanguage() {
        return PythonLanguage.INSTANCE;
    }
}
