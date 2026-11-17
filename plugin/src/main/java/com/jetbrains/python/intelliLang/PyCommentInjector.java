// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.intelliLang;

import com.jetbrains.python.impl.codeInsight.PyInjectorBase;
import consulo.language.Language;
import consulo.language.inject.advanced.BaseInjection;
import consulo.language.inject.advanced.InjectedLanguage;
import consulo.language.inject.advanced.InjectorUtils;
import consulo.language.psi.PsiElement;
import org.jspecify.annotations.Nullable;

/**
 * Consulo: abstract, because Consulo's {@code MultiHostInjector} needs {@code getElementClass()};
 * JB does not register this injector either.
 */
public abstract class PyCommentInjector extends PyInjectorBase {
    @Override
    public @Nullable Language getInjectedLanguage(PsiElement context) {
        final BaseInjection injection = InjectorUtils.findCommentInjection(context, "comment", null);
        if (injection != null) {
            return InjectedLanguage.findLanguageById(injection.getInjectedLanguageId());
        }
        return null;
    }
}
