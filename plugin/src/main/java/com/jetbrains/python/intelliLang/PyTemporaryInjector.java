// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.intelliLang;

import com.jetbrains.python.impl.codeInsight.PyInjectionUtil;
import com.jetbrains.python.impl.codeInsight.PyInjectorBase;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyParenthesizedExpression;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.inject.MultiHostRegistrar;
import consulo.language.inject.advanced.InjectedLanguage;
import consulo.language.inject.advanced.InjectorUtils;
import consulo.language.inject.advanced.TemporaryPlacesRegistry;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiLanguageInjectionHost;
import org.jspecify.annotations.Nullable;

/**
 * Consulo: registered once per element class of {@code PyInjectionUtil.ELEMENTS_TO_INJECT_IN} via the nested subclasses,
 * JB registers a single {@code multiHostInjector}.
 */
public abstract class PyTemporaryInjector extends PyInjectorBase {
    @Override
    public void injectLanguages(MultiHostRegistrar registrar, PsiElement context) {
        final PyInjectionUtil.InjectionResult result = registerInjection(registrar, context);
        if (result.isInjected()) {
            final TemporaryPlacesRegistry registry = TemporaryPlacesRegistry.getInstance(context.getProject());
            InjectorUtils.registerSupport(registry.getLanguageInjectionSupport(), false, context, getInjectedLanguage(context));
        }
    }

    @Override
    public @Nullable Language getInjectedLanguage(PsiElement context) {
        if (context instanceof PsiLanguageInjectionHost && context instanceof PyElement) {
            PsiFile file = context.getContainingFile();
            InjectedLanguage injectedLanguage = TemporaryPlacesRegistry.getInstance(file.getProject())
                .getLanguageFor((PsiLanguageInjectionHost) context, file);
            if (injectedLanguage != null) {
                return injectedLanguage.getLanguage();
            }
        }
        return null;
    }

    @ExtensionImpl
    public static final class ForStringLiteral extends PyTemporaryInjector {
        @Override
        public Class<? extends PsiElement> getElementClass() {
            return PyStringLiteralExpression.class;
        }
    }

    @ExtensionImpl
    public static final class ForParenthesized extends PyTemporaryInjector {
        @Override
        public Class<? extends PsiElement> getElementClass() {
            return PyParenthesizedExpression.class;
        }
    }

    @ExtensionImpl
    public static final class ForBinary extends PyTemporaryInjector {
        @Override
        public Class<? extends PsiElement> getElementClass() {
            return PyBinaryExpression.class;
        }
    }

    @ExtensionImpl
    public static final class ForCall extends PyTemporaryInjector {
        @Override
        public Class<? extends PsiElement> getElementClass() {
            return PyCallExpression.class;
        }
    }

    @ExtensionImpl
    public static final class ForComment extends PyTemporaryInjector {
        @Override
        public Class<? extends PsiElement> getElementClass() {
            return PsiComment.class;
        }
    }
}
