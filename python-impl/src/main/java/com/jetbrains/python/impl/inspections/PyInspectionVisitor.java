// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.impl.inspections.PyInspectionMessages.ProblemMessage;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyTypedElement;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.access.RequiredReadAction;
import consulo.document.util.TextRange;
import consulo.language.editor.impl.inspection.ProblemDescriptorImpl;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemBuilder;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.editor.intention.HintAction;
import consulo.language.psi.FileContextUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiErrorElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.util.dataholder.Key;
import consulo.util.lang.ObjectUtil;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

public abstract class PyInspectionVisitor extends PyElementVisitor {
    public static final String POPULATE_TYPE_EVAL_CONTEXT_ON_CREATION_PROPERTY = "python.populate.type.eval.context.on.creation";

    public static final Key<TypeEvalContext> INSPECTION_TYPE_EVAL_CONTEXT = Key.create("PyInspectionTypeEvalContext");

    private final @Nullable ProblemsHolder holder;

    protected final TypeEvalContext myTypeEvalContext;

    public PyInspectionVisitor(@Nullable ProblemsHolder holder, TypeEvalContext typeEvalContext) {
        this.holder = holder;
        myTypeEvalContext = typeEvalContext;
    }

    /**
     * @deprecated use {@link PyInspectionVisitor#PyInspectionVisitor(ProblemsHolder, TypeEvalContext)} instead
     */
    @Deprecated
    public PyInspectionVisitor(@Nullable ProblemsHolder holder, LocalInspectionToolSession session) {
        this(holder, getContext(session));
    }

    protected @Nullable ProblemsHolder getHolder() {
        return holder;
    }

    protected PyResolveContext getResolveContext() {
        return PyResolveContext.defaultContext(myTypeEvalContext);
    }

    public boolean isOnTheFly() {
        return holder != null && holder.isOnTheFly();
    }

    @RequiredReadAction
    protected void registerProblem(@Nullable PsiElement element, String message) {
        if (!canRegisterProblem(element)) {
            return;
        }
        if (holder != null) {
            holder.registerProblem(element, message);
        }
    }

    @RequiredReadAction
    protected void registerProblem(@Nullable PsiElement element, String message, LocalQuickFix... quickFixes) {
        if (!canRegisterProblem(element)) {
            return;
        }
        if (holder != null) {
            holder.registerProblem(element, message, quickFixes);
        }
    }

    @RequiredReadAction
    public void registerProblem(@Nullable PsiElement element, String message, ProblemHighlightType type) {
        if (!canRegisterProblem(element)) {
            return;
        }
        if (holder != null) {
            holder.registerProblem(holder.getManager().newProblemDescriptor(LocalizeValue.of(message))
                .range(element)
                .highlightType(type)
                .onTheFly(holder.isOnTheFly())
                .create());
        }
    }

    /**
     * Registers a {@link ProblemMessage}: the {@link ProblemMessage#description()}
     * is the plain-text description shown in the Problems view, and the
     * {@link ProblemMessage#tooltip()} is the HTML tooltip shown on editor hover.
     * Keeping the description plain-text means batch results and golden tests still see the original
     * message; the tooltip just adds {@code <code>} blocks around names.
     */
    @RequiredReadAction
    protected void registerProblem(@Nullable PsiElement element, ProblemMessage message) {
        registerProblem(element, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
    }

    @RequiredReadAction
    public void registerProblem(@Nullable PsiElement element, ProblemMessage message, ProblemHighlightType type) {
        registerProblemMessage(element, message, type, null);
    }

    @RequiredReadAction
    protected void registerProblem(@Nullable PsiElement element,
                                   ProblemMessage message,
                                   ProblemHighlightType type,
                                   LocalQuickFix... fixes) {
        registerProblemMessage(element, message, type, null, fixes);
    }

    @RequiredReadAction
    protected void registerProblem(@Nullable PsiElement element,
                                   ProblemMessage message,
                                   ProblemHighlightType type,
                                   @Nullable LocalQuickFix[] fixes,
                                   @Nullable TextRange rangeInElement) {
        if (element instanceof PsiErrorElement) {
            return;
        }
        registerProblemMessage(element, message, type, rangeInElement, fixes);
    }

    /**
     * The most full-blown version.
     *
     * @see ProblemDescriptor
     */
    @RequiredReadAction
    protected void registerProblem(PsiElement psiElement,
                                   String descriptionTemplate,
                                   ProblemHighlightType highlightType,
                                   @Nullable HintAction hintAction,
                                   LocalQuickFix... fixes) {
        registerProblem(psiElement, descriptionTemplate, highlightType, hintAction, null, fixes);
    }

    /**
     * The most full-blown version.
     *
     * @see ProblemDescriptor
     */
    @RequiredReadAction
    protected void registerProblem(PsiElement psiElement,
                                   String descriptionTemplate,
                                   ProblemHighlightType highlightType,
                                   @Nullable HintAction hintAction,
                                   @Nullable TextRange rangeInElement,
                                   LocalQuickFix... fixes) {
        if (psiElement instanceof PsiErrorElement) {
            return;
        }
        if (holder != null) {
            holder.registerProblem(new ProblemDescriptorImpl(psiElement,
                                                             psiElement,
                                                             LocalizeValue.of(descriptionTemplate),
                                                             fixes,
                                                             highlightType,
                                                             false,
                                                             rangeInElement,
                                                             hintAction,
                                                             holder.isOnTheFly()));
        }
    }

    @RequiredReadAction
    private void registerProblemMessage(@Nullable PsiElement element,
                                        ProblemMessage message,
                                        ProblemHighlightType type,
                                        @Nullable TextRange rangeInElement,
                                        @Nullable LocalQuickFix... fixes) {
        if (holder == null || element == null || !canRegisterProblem(element)) {
            return;
        }
        // TODO consulo: ProblemBuilder.tooltip(String) missing, message.tooltip() is not shown
        ProblemBuilder builder = holder.newProblem(LocalizeValue.of(message.description())).highlightType(type);
        builder.range(element, rangeInElement);
        for (LocalQuickFix fix : fixes) {
            if (fix != null) {
                builder.withFix(fix);
            }
        }
        builder.create();
    }

    /**
     * Registers a problem whose one-line {@code message} is the plain-text description, but whose editor hover shows a
     * richer {@code tooltip} (HTML). Keeping the description and tooltip separate means batch results and golden tests
     * still see only the plain message. Any quick {@code fixes} are attached (nulls are ignored).
     */
    @RequiredReadAction
    protected void registerProblemWithTooltip(@Nullable PsiElement element,
                                              String message,
                                              ProblemHighlightType type,
                                              String tooltip,
                                              @Nullable LocalQuickFix... fixes) {
        if (holder == null || element == null || !canRegisterProblem(element)) {
            return;
        }
        // TODO consulo: ProblemBuilder.tooltip(String) missing, tooltip is not shown
        ProblemBuilder builder = holder.newProblem(LocalizeValue.of(message)).range(element).highlightType(type);
        for (LocalQuickFix fix : fixes) {
            if (fix != null) {
                builder.withFix(fix);
            }
        }
        builder.create();
    }

    /**
     * Registers {@code message} as the flat description and, <b>only</b> in on-the-fly mode, attaches the hover tooltip
     * produced by {@code tooltip}. The supplier is invoked lazily and exclusively for on-the-fly highlighting, so any
     * expensive work behind it (e.g. re-running a type match to build a breakdown) is skipped in batch mode; when
     * it yields {@code null} — or in batch mode — only the plain {@code message} is registered. This folds the
     * {@code if (isOnTheFly) registerProblemWithTooltip(...) else registerProblem(...)} branch into a single call.
     */
    @RequiredReadAction
    public void registerProblemWithTooltip(@Nullable PsiElement element,
                                           String message,
                                           ProblemHighlightType type,
                                           LocalQuickFix[] fixes,
                                           Supplier<@Nullable String> tooltip) {
        if (!canRegisterProblem(element)) {
            return;
        }
        String onTheFlyTooltip = holder != null && holder.isOnTheFly() ? tooltip.get() : null;
        if (onTheFlyTooltip != null) {
            registerProblemWithTooltip(element, message, type, onTheFlyTooltip, fixes);
        }
        else if (fixes.length == 0) {
            registerProblem(element, message, type);
        }
        else {
            registerProblem(element, message, type, (HintAction)null, fixes);
        }
    }

    /**
     * {@link ProblemMessage} variant of the supplier-based {@link #registerProblemWithTooltip}: on-the-fly the {@code tooltip} supplier
     * (when it yields non-null) replaces the message's own tooltip; otherwise the message is registered with its
     * plain {@link ProblemMessage#description()} and {@link ProblemMessage#tooltip()}.
     */
    @RequiredReadAction
    public void registerProblemWithTooltip(@Nullable PsiElement element,
                                           ProblemMessage message,
                                           ProblemHighlightType type,
                                           LocalQuickFix[] fixes,
                                           Supplier<@Nullable String> tooltip) {
        if (!canRegisterProblem(element)) {
            return;
        }
        String onTheFlyTooltip = holder != null && holder.isOnTheFly() ? tooltip.get() : null;
        if (onTheFlyTooltip != null) {
            registerProblemWithTooltip(element, message.description(), type, onTheFlyTooltip, fixes);
        }
        else {
            registerProblem(element, message, type, fixes);
        }
    }

    public static TypeEvalContext getContext(LocalInspectionToolSession session) {
        TypeEvalContext context;
        synchronized (INSPECTION_TYPE_EVAL_CONTEXT) {
            context = session.getUserData(INSPECTION_TYPE_EVAL_CONTEXT);
            if (context == null) {
                PsiFile sessionFile = session.getFile();
                PsiFile contextFile = FileContextUtil.getContextFile(sessionFile);
                PsiFile file = ObjectUtil.chooseNotNull(contextFile, sessionFile);
                context = TypeEvalContext.codeAnalysis(file.getProject(), file);
                if (Boolean.getBoolean(POPULATE_TYPE_EVAL_CONTEXT_ON_CREATION_PROPERTY)) {
                    populateContextCache(context, file);
                }
                session.putUserData(INSPECTION_TYPE_EVAL_CONTEXT, context);
            }
        }
        return context;
    }

    private static void populateContextCache(TypeEvalContext context, PsiFile file) {
        PsiTreeUtil.processElements(file, element -> {
            if (element instanceof PyTypedElement typedElement) {
                context.getType(typedElement);
            }
            return true;
        });
    }

    private static boolean canRegisterProblem(@Nullable PsiElement element) {
        if (element == null) {
            return false;
        }

        if (element.getTextLength() > 0) {
            return true;
        }

        return element instanceof PyFile;
    }
}
