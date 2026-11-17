// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.validation;

import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.validation.PyAnnotationHolder;
import com.jetbrains.python.validation.PyAnnotatorBase;
import consulo.document.util.TextRange;
import consulo.language.editor.annotation.AnnotationBuilder;
import consulo.language.editor.annotation.HighlightSeverity;
import consulo.language.editor.impl.inspection.ProblemDescriptorImpl;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.intention.IntentionAction;
import consulo.language.editor.intention.QuickFixWrapper;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import org.jspecify.annotations.Nullable;

import java.util.List;

public class UnsupportedFeatures extends PyAnnotatorBase {
    @Override
    protected void annotate(PsiElement element, PyAnnotationHolder holder) {
        element.accept(new MyVisitor(holder, List.of(LanguageLevel.forElement(element))));
    }

    private static class MyVisitor extends PyCompatibilityVisitor {
        private final PyAnnotationHolder myHolder;

        private MyVisitor(PyAnnotationHolder holder, List<LanguageLevel> versionsToProcess) {
            super(versionsToProcess);
            myHolder = holder;
        }

        @Override
        protected void registerProblem(PsiElement node,
                                       TextRange range,
                                       String message,
                                       boolean asError,
                                       LocalQuickFix... fixes) {
            if (range.isEmpty()) {
                return;
            }

            HighlightSeverity severity = asError ? HighlightSeverity.ERROR : HighlightSeverity.WARNING;
            if (fixes.length > 0) {
                AnnotationBuilder annotationBuilder = myHolder.newAnnotation(severity, message).range(range);
                for (LocalQuickFix fix : fixes) {
                    if (fix != null) {
                        annotationBuilder = annotationBuilder.withFix(createIntention(node, message, fix));
                    }
                }
                annotationBuilder.create();
            }
            else {
                myHolder.newAnnotation(severity, message).range(range).create();
            }
        }

        private static IntentionAction createIntention(PsiElement node,
                                                       String message,
                                                       LocalQuickFix localQuickFix) {
            return createIntention(node, null, message, localQuickFix);
        }

        private static IntentionAction createIntention(PsiElement node,
                                                       @Nullable TextRange range,
                                                       String message,
                                                       LocalQuickFix localQuickFix) {
            LocalQuickFix[] quickFixes = {localQuickFix};
            ProblemDescriptor descr =
                new ProblemDescriptorImpl(node, node, LocalizeValue.of(message), quickFixes, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, true,
                    range, true);

            return QuickFixWrapper.wrap(descr, 0);
        }
    }
}
