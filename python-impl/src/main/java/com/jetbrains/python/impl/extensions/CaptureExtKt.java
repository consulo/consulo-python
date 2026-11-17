// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.extensions;

import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyParameterList;
import consulo.language.pattern.PsiElementPattern;
import consulo.language.psi.PsiElement;

import static consulo.language.pattern.PlatformPatterns.psiElement;

public final class CaptureExtKt {
    private CaptureExtKt() {
    }

    /**
     * Place right after "def" in class method. Useful to provide custom completions
     */
    public static <T extends PsiElement> PsiElementPattern.Capture<T> afterDefInMethod(PsiElementPattern.Capture<T> capture) {
        return capture.withLanguage(PythonLanguage.getInstance())
            .and(psiElement().inside(psiElement(PyFunction.class).inside(psiElement(PyClass.class))))
            .and(psiElement().afterLeaf("def"));
    }

    /**
     * Place right after "def" in function or method. Useful to provide custom completions
     */
    public static <T extends PsiElement> PsiElementPattern.Capture<T> afterDefInFunction(PsiElementPattern.Capture<T> capture) {
        return capture.withLanguage(PythonLanguage.getInstance())
            .and(psiElement().inside(PyFunction.class))
            .and(psiElement().afterLeaf("def"));
    }


    public static <T extends PsiElement> PsiElementPattern.Capture<T> inParameterList(PsiElementPattern.Capture<T> capture) {
        return capture.withLanguage(PythonLanguage.getInstance()).and(psiElement().inside(PyParameterList.class));
    }

    public static <T extends PsiElement> PsiElementPattern.Capture<T> inArgumentList(PsiElementPattern.Capture<T> capture) {
        return capture.withLanguage(PythonLanguage.getInstance()).and(psiElement().inside(PyArgumentList.class));
    }
}
