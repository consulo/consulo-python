// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.intelliLang;

import com.jetbrains.python.impl.codeInsight.PyInjectorBase;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyParenthesizedExpression;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.inject.MultiHostRegistrar;
import consulo.language.inject.advanced.BaseInjection;
import consulo.language.inject.advanced.Configuration;
import consulo.language.inject.advanced.InjectedLanguage;
import consulo.language.inject.advanced.InjectorUtils;
import consulo.language.inject.advanced.LanguageInjectionSupport;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import org.jspecify.annotations.Nullable;

/**
 * Consulo: registered once per element class of {@code PyInjectionUtil.ELEMENTS_TO_INJECT_IN} via the nested subclasses,
 * JB registers a single {@code multiHostInjector}.
 */
public abstract class PyConfigurationInjector extends PyInjectorBase {
    @Override
    public void injectLanguages(MultiHostRegistrar registrar, PsiElement context) {
        if (!(context instanceof PyElement)) return;
        registerInjection(registrar, context);
    }

    @Override
    public @Nullable Language getInjectedLanguage(PsiElement context) {
        for (LanguageInjectionSupport support : InjectorUtils.getActiveInjectionSupports()) {
            if (support instanceof PyLanguageInjectionSupport) {
                final Configuration configuration = Configuration.getProjectInstance(context.getProject());
                for (BaseInjection injection : configuration.getInjections(support.getId())) {
                    if (injection.acceptsPsiElement(context)) {
                        return InjectedLanguage.findLanguageById(injection.getInjectedLanguageId());
                    }
                }
            }
        }
        return null;
    }

    @ExtensionImpl
    public static final class ForStringLiteral extends PyConfigurationInjector {
        @Override
        public Class<? extends PsiElement> getElementClass() {
            return PyStringLiteralExpression.class;
        }
    }

    @ExtensionImpl
    public static final class ForParenthesized extends PyConfigurationInjector {
        @Override
        public Class<? extends PsiElement> getElementClass() {
            return PyParenthesizedExpression.class;
        }
    }

    @ExtensionImpl
    public static final class ForBinary extends PyConfigurationInjector {
        @Override
        public Class<? extends PsiElement> getElementClass() {
            return PyBinaryExpression.class;
        }
    }

    @ExtensionImpl
    public static final class ForCall extends PyConfigurationInjector {
        @Override
        public Class<? extends PsiElement> getElementClass() {
            return PyCallExpression.class;
        }
    }

    @ExtensionImpl
    public static final class ForComment extends PyConfigurationInjector {
        @Override
        public Class<? extends PsiElement> getElementClass() {
            return PsiComment.class;
        }
    }
}
