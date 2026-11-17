// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.validation;

import com.jetbrains.python.psi.PyTypeParameter;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import org.jspecify.annotations.Nullable;

@ExtensionImpl
public class Pep8TypeParameterProblemSuppressor implements Pep8ProblemSuppressor {

    public static final String UNEXPECTED_SPACES_AROUND_KEYWORD = "E251";
    public static final String MISSING_WHITESPACE_AROUND_OPERATOR = "E225";

    @Override
    public boolean isProblemSuppressed(Pep8ExternalAnnotator.Problem problem,
                                       PsiFile file,
                                       @Nullable PsiElement targetElement) {
        if (targetElement != null) {
            return ((problem.getCode().equals(UNEXPECTED_SPACES_AROUND_KEYWORD)
                || problem.getCode().equals(MISSING_WHITESPACE_AROUND_OPERATOR))
                && targetElement.getParent() instanceof PyTypeParameter);
        }
        return false;
    }
}
