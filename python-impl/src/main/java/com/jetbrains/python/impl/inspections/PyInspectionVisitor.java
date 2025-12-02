/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.impl.inspections;

import consulo.annotation.access.RequiredReadAction;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import consulo.language.editor.intention.HintAction;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.ide.impl.idea.codeInspection.ex.ProblemDescriptorImpl;
import consulo.util.dataholder.Key;
import consulo.document.util.TextRange;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiErrorElement;
import consulo.language.psi.PsiFile;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.TypeEvalContext;

/**
 * @author dcheryasov
 */
public abstract class PyInspectionVisitor extends PyElementVisitor {
    @Nullable
    private final ProblemsHolder myHolder;
    @Nonnull
    private final LocalInspectionToolSession mySession;
    protected final TypeEvalContext myTypeEvalContext;

    public static final Key<TypeEvalContext> INSPECTION_TYPE_EVAL_CONTEXT = Key.create("PyInspectionTypeEvalContext");

    public PyInspectionVisitor(@Nullable ProblemsHolder holder, @Nonnull LocalInspectionToolSession session) {
        myHolder = holder;
        mySession = session;
        TypeEvalContext context;
        synchronized (INSPECTION_TYPE_EVAL_CONTEXT) {
            context = session.getUserData(INSPECTION_TYPE_EVAL_CONTEXT);
            if (context == null) {
                PsiFile file = session.getFile();
                context = TypeEvalContext.codeAnalysis(file.getProject(), file);
                session.putUserData(INSPECTION_TYPE_EVAL_CONTEXT, context);
            }
        }
        myTypeEvalContext = context;
    }

    protected PyResolveContext getResolveContext() {
        return PyResolveContext.noImplicits().withTypeEvalContext(myTypeEvalContext);
    }

    @Nullable
    protected ProblemsHolder getHolder() {
        return myHolder;
    }

    @Nonnull
    public LocalInspectionToolSession getSession() {
        return mySession;
    }

    @RequiredReadAction
    protected final void registerProblem(PsiElement element, String message) {
        if (element == null || element.getTextLength() == 0) {
            return;
        }
        if (myHolder != null) {
            myHolder.newProblem(LocalizeValue.of(message))
                .range(element)
                .create();
        }
    }

    @RequiredReadAction
    protected final void registerProblem(
        @Nullable PsiElement element,
        @Nonnull String message,
        @Nonnull LocalQuickFix... quickFixes
    ) {
        if (element == null || element.getTextLength() == 0) {
            return;
        }
        if (myHolder != null) {
            myHolder.newProblem(LocalizeValue.of(message))
                .range(element)
                .withFixes(quickFixes)
                .create();
        }
    }

    @RequiredReadAction
    protected final void registerProblem(PsiElement element, String message, ProblemHighlightType type) {
        if (element == null || element.getTextLength() == 0) {
            return;
        }
        if (myHolder != null) {
            myHolder.registerProblem(myHolder.getManager().newProblemDescriptor(LocalizeValue.of(message))
                .range(element)
                .highlightType(type)
                .onTheFly(myHolder.isOnTheFly())
                .create());
        }
    }

    /**
     * The most full-blown version.
     *
     * @see ProblemDescriptor
     */
    @RequiredReadAction
    protected final void registerProblem(
        @Nonnull PsiElement psiElement,
        @Nonnull String descriptionTemplate,
        ProblemHighlightType highlightType,
        @Nullable HintAction hintAction,
        LocalQuickFix... fixes
    ) {
        registerProblem(psiElement, descriptionTemplate, highlightType, hintAction, null, fixes);
    }

    /**
     * The most full-blown version.
     *
     * @see ProblemDescriptor
     */
    @RequiredReadAction
    protected final void registerProblem(
        @Nonnull PsiElement psiElement,
        @Nonnull String descriptionTemplate,
        ProblemHighlightType highlightType,
        @Nullable HintAction hintAction,
        @Nullable TextRange rangeInElement,
        LocalQuickFix... fixes
    ) {
        if (myHolder != null && !(psiElement instanceof PsiErrorElement)) {
            myHolder.registerProblem(new ProblemDescriptorImpl(
                psiElement,
                psiElement,
                LocalizeValue.of(descriptionTemplate),
                fixes,
                highlightType,
                false,
                rangeInElement,
                hintAction,
                myHolder.isOnTheFly()
            ));
        }
    }
}
