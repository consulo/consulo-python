// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.validation;

import com.jetbrains.python.validation.PyAnnotationHolder;
import com.jetbrains.python.validation.PyAnnotatorBase;
import consulo.application.dumb.DumbAware;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;

import java.util.List;


// AST-based syntax annotator that does not rely on resolve.
// Planned to move to thin-client (visitors are to be converted to `PyAstElementVisitor`).
public final class PySyntaxAnnotator extends PyAnnotatorBase implements DumbAware {
    @Override
    public void annotate(PsiElement psiElement, PyAnnotationHolder holder) {
        List<PsiElementVisitor> visitors = List.of(
            new PyAssignTargetAnnotatorVisitor(holder),
            new PyTypeAnnotationTargetAnnotatorVisitor(holder),
            new PyParameterListAnnotatorVisitor(holder),
            new PyReturnYieldAnnotatorVisitor(holder),
            new PyBreakContinueAnnotatorVisitor(holder),
            new PyGlobalAnnotatorVisitor(holder),
            new PyImportAnnotatorVisitor(holder),
            new PyAsyncAwaitAnnotatorVisitor(holder),
            new PyAstNumericLiteralAnnotatorVisitor(holder),
            new PyGeneratorInArgumentListAnnotatorVisitor(holder),
            new PyStarAnnotatorVisitor(holder),
            new PyStringLiteralQuotesAnnotatorVisitor(holder),
            new PyFStringsAnnotatorVisitor(holder),
            new PyPatternAnnotatorVisitor(holder),
            new PyTryExceptAnnotatorVisitor(holder),
            new PyTypeParameterListAnnotatorVisitor(holder)
        );
        for (PsiElementVisitor visitor : visitors) {
            psiElement.accept(visitor);
        }
    }
}
