// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.psi.PyFile;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.document.util.TextRange;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Registers {@link PyTypeCheckerInspection} problems tagged with a granular {@link PyTypeCheckerSuppressionCode}.
 * <p>
 * Unlike the plain {@code PyInspectionVisitor.registerProblem} helpers, every problem reported here:
 * <ul>
 *  <li>is skipped when the element is covered by a {@code # noinspection <code>}, {@code # type: ignore[<code>]}, or
 *    {@code # pycharm: ignore[<code>]} comment for its own code ({@link PySuppressionUtil}, {@link PyIgnoreCommentUtil}); the
 *    broad {@code # noinspection PyTypeChecker} / bare {@code # type: ignore} is still handled afterwards by the platform
 *    via {@link PyInspectionsSuppressor} / the type-ignore suppressor;</li>
 *  <li>carries a {@link PyTypeCheckerSuppressableProblemGroup} so Alt-Enter offers the per-code suppress actions.</li>
 * </ul>
 */
final class PyTypeCheckerProblemReporter {
    private PyTypeCheckerProblemReporter() {
    }

    /**
     * Reports a message that has a distinct plain description and HTML tooltip.
     */
    @RequiredReadAction
    static boolean report(ProblemsHolder holder,
                          PyTypeCheckerSuppressionCode code,
                          @Nullable PsiElement element,
                          PyInspectionMessages.ProblemMessage message) {
        return report(holder, code, element, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
    }

    /**
     * Reports a message that has a distinct plain description and HTML tooltip.
     */
    @RequiredReadAction
    static boolean report(ProblemsHolder holder,
                          PyTypeCheckerSuppressionCode code,
                          @Nullable PsiElement element,
                          PyInspectionMessages.ProblemMessage message,
                          ProblemHighlightType type,
                          LocalQuickFix... fixes) {
        if (element == null || !canRegister(element) || isSuppressed(element, code)) {
            return false;
        }
        // TODO consulo: ProblemDescriptorBuilder.tooltip(String) missing, message.tooltip() is not shown
        ProblemDescriptor descriptor = holder.getManager().newProblemDescriptor(LocalizeValue.of(message.description()))
            .range(element, (TextRange) null)
            .highlightType(type)
            .onTheFly(holder.isOnTheFly())
            .withFixes(fixes)
            .create();
        descriptor.setProblemGroup(new PyTypeCheckerSuppressableProblemGroup(code));
        holder.registerProblem(descriptor);
        return true;
    }

    /**
     * Reports a plain-text message (tooltip is derived from the description, as in {@code ProblemsHolder.registerProblem}).
     */
    @RequiredReadAction
    static boolean report(ProblemsHolder holder,
                          PyTypeCheckerSuppressionCode code,
                          @Nullable PsiElement element,
                          String message) {
        return report(holder, code, element, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
    }

    /**
     * Reports a plain-text message (tooltip is derived from the description, as in {@code ProblemsHolder.registerProblem}).
     */
    @RequiredReadAction
    static boolean report(ProblemsHolder holder,
                          PyTypeCheckerSuppressionCode code,
                          @Nullable PsiElement element,
                          String message,
                          ProblemHighlightType type,
                          LocalQuickFix... fixes) {
        if (element == null || !canRegister(element) || isSuppressed(element, code)) {
            return false;
        }
        ProblemDescriptor descriptor = holder.getManager().newProblemDescriptor(LocalizeValue.of(message))
            .range(element)
            .highlightType(type)
            .onTheFly(holder.isOnTheFly())
            .withFixes(fixes)
            .create();
        descriptor.setProblemGroup(new PyTypeCheckerSuppressableProblemGroup(code));
        holder.registerProblem(descriptor);
        return true;
    }

    /**
     * Like {@link #report} for a {@link PyInspectionMessages.ProblemMessage}, but the on-the-fly hover tooltip is produced lazily by
     * {@code tooltip} (e.g. a re-computed type-mismatch breakdown). The supplier is invoked only on-the-fly; in batch mode, or
     * when it yields {@code null}, the message's own {@link PyInspectionMessages.ProblemMessage#tooltip()} is used. The plain
     * {@link PyInspectionMessages.ProblemMessage#description()} always stays the one-line description.
     */
    @RequiredReadAction
    static boolean reportWithTooltip(ProblemsHolder holder,
                                     PyTypeCheckerSuppressionCode code,
                                     @Nullable PsiElement element,
                                     PyInspectionMessages.ProblemMessage message,
                                     ProblemHighlightType type,
                                     LocalQuickFix[] fixes,
                                     Supplier<@Nullable String> tooltip) {
        if (element == null || !canRegister(element) || isSuppressed(element, code)) {
            return false;
        }
        String onTheFlyTooltip = holder.isOnTheFly() ? tooltip.get() : null;
        // TODO consulo: ProblemDescriptorBuilder.tooltip(String) missing, onTheFlyTooltip / message.tooltip() is not shown
        ProblemDescriptor descriptor = holder.getManager().newProblemDescriptor(LocalizeValue.of(message.description()))
            .range(element, (TextRange) null)
            .highlightType(type)
            .onTheFly(holder.isOnTheFly())
            .withFixes(fixes)
            .create();
        descriptor.setProblemGroup(new PyTypeCheckerSuppressableProblemGroup(code));
        holder.registerProblem(descriptor);
        return true;
    }

    /**
     * {@link #reportWithTooltip} without quick fixes.
     */
    @RequiredReadAction
    static boolean reportWithTooltip(ProblemsHolder holder,
                                     PyTypeCheckerSuppressionCode code,
                                     @Nullable PsiElement element,
                                     PyInspectionMessages.ProblemMessage message,
                                     ProblemHighlightType type,
                                     Supplier<@Nullable String> tooltip) {
        return reportWithTooltip(holder, code, element, message, type, LocalQuickFix.EMPTY_ARRAY, tooltip);
    }

    /**
     * Plain-text variant of the supplier-based {@link #reportWithTooltip}; the lazy {@code tooltip} replaces the derived one on-the-fly.
     */
    @RequiredReadAction
    static boolean reportWithTooltip(ProblemsHolder holder,
                                     PyTypeCheckerSuppressionCode code,
                                     @Nullable PsiElement element,
                                     String message,
                                     ProblemHighlightType type,
                                     Supplier<@Nullable String> tooltip) {
        if (element == null || !canRegister(element) || isSuppressed(element, code)) {
            return false;
        }
        String onTheFlyTooltip = holder.isOnTheFly() ? tooltip.get() : null;
        // TODO consulo: ProblemDescriptorBuilder.tooltip(String) missing, onTheFlyTooltip is not shown
        ProblemDescriptor descriptor = holder.getManager().newProblemDescriptor(LocalizeValue.of(message))
            .range(element)
            .highlightType(type)
            .onTheFly(holder.isOnTheFly())
            .create();
        descriptor.setProblemGroup(new PyTypeCheckerSuppressableProblemGroup(code));
        holder.registerProblem(descriptor);
        return true;
    }

    // A granular code is silenced by `# noinspection <code>` (scanned by PySuppressionUtil over the enclosing
    // statement/function/class) or by an explicit `# type: ignore[<code>]` / `# pycharm: ignore[<code>]`
    // trailing/file-level comment (PyIgnoreCommentUtil).
    private static boolean isSuppressed(PsiElement element, PyTypeCheckerSuppressionCode code) {
        return PySuppressionUtil.isSuppressed(element, code.getId()) || PyIgnoreCommentUtil.isExplicitlyIgnored(element, code.getId());
    }

    // Mirrors PyInspectionVisitor.canRegisterProblem.
    @RequiredReadAction
    private static boolean canRegister(PsiElement element) {
        return element.getTextLength() > 0 || element instanceof PyFile;
    }
}
