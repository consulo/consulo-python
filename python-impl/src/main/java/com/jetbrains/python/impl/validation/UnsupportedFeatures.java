/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.impl.validation;

import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElement;
import consulo.annotation.access.RequiredReadAction;
import consulo.document.util.TextRange;
import consulo.ide.impl.idea.codeInspection.ex.ProblemDescriptorImpl;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.intention.IntentionAction;
import consulo.language.editor.intention.QuickFixWrapper;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * @author Alexey.Ivanov
 */
public class UnsupportedFeatures extends CompatibilityVisitor {
    public UnsupportedFeatures() {
        super(new ArrayList<>());
    }

    @Override
    public void visitPyElement(PyElement node) {
        setVersionsToProcess(Arrays.asList(LanguageLevel.forElement(node)));
    }

    @Override
    @RequiredReadAction
    protected void registerProblem(
        @Nonnull PsiElement node,
        @Nonnull TextRange range,
        @Nonnull String message,
        @Nullable LocalQuickFix localQuickFix,
        boolean asError
    ) {
        if (range.isEmpty()) {
            return;
        }

        if (localQuickFix != null) {
            if (asError) {
                getHolder().createErrorAnnotation(range, message)
                    .registerFix(createIntention(node, LocalizeValue.of(message), localQuickFix));
            }
            else {
                getHolder().createWarningAnnotation(range, message)
                    .registerFix(createIntention(node, LocalizeValue.of(message), localQuickFix));
            }
        }
        else {
            if (asError) {
                getHolder().createErrorAnnotation(range, message);
            }
            else {
                getHolder().createWarningAnnotation(range, message);
            }
        }
    }

    @Nonnull
    @RequiredReadAction
    private static IntentionAction createIntention(
        @Nonnull PsiElement node,
        @Nonnull LocalizeValue message,
        @Nonnull LocalQuickFix localQuickFix
    ) {
        return createIntention(node, node.getTextRange(), message, localQuickFix);
    }

    @Nonnull
    @RequiredReadAction
    private static IntentionAction createIntention(
        @Nonnull PsiElement node,
        @Nullable TextRange range,
        @Nonnull LocalizeValue message,
        @Nonnull LocalQuickFix localQuickFix
    ) {
        LocalQuickFix[] quickFixes = {localQuickFix};
        ProblemDescriptorImpl descr =
            new ProblemDescriptorImpl(node, node, message, quickFixes, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, true, range, true);

        return QuickFixWrapper.wrap(descr, 0);
    }
}
