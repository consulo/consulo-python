// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight;

import consulo.language.Language;
import consulo.language.inject.MultiHostInjector;
import consulo.language.inject.MultiHostRegistrar;
import consulo.language.psi.PsiElement;
import org.jspecify.annotations.Nullable;

/**
 * Consulo: {@link MultiHostInjector} is registered per element class ({@link #getElementClass()}), so JB
 * {@code elementsToInjectIn() = PyInjectionUtil.ELEMENTS_TO_INJECT_IN} is expressed by one subclass per element class.
 */
public abstract class PyInjectorBase implements MultiHostInjector {
    @Override
    public void injectLanguages(MultiHostRegistrar registrar, PsiElement context) {
        registerInjection(registrar, context);
    }

    public abstract @Nullable Language getInjectedLanguage(PsiElement context);

    protected PyInjectionUtil.InjectionResult registerInjection(MultiHostRegistrar registrar, PsiElement context) {
        Language language = getInjectedLanguage(context);
        if (language != null) {
            PsiElement element = PyInjectionUtil.getLargestStringLiteral(context);
            if (element != null) {
                return PyInjectionUtil.registerStringLiteralInjection(element, registrar, language);
            }
        }
        return PyInjectionUtil.InjectionResult.EMPTY;
    }
}
