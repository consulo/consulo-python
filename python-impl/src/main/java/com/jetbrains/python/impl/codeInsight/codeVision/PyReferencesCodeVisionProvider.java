// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.codeVision;

import com.jetbrains.python.ast.impl.PyUtilCore;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyImportStatementBase;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.document.util.TextRange;
import consulo.language.editor.codeVision.CodeVisionEntry;
import consulo.language.editor.codeVision.CodeVisionRelativeOrdering;
import consulo.language.editor.impl.codeVision.ReferencesCodeVisionProvider;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiNamedElement;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.search.PsiSearchHelper;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;
import consulo.python.impl.localize.PyCodeInsightLocalize;
import consulo.usage.UsageInfo;
import consulo.util.lang.Pair;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@ExtensionImpl
public class PyReferencesCodeVisionProvider extends ReferencesCodeVisionProvider {
    public static final String ID = "python.references";

    /**
     * Test-only override for both budgets below, so a test expecting an exact count can let the search finish.
     */
    public static @Nullable Duration pyUsagesSearchBudgetTestOverride = null;

    // One widespread-name search; its partial count is rendered as "N+"
    private static final Duration SINGLE_SEARCH_BUDGET = Duration.ofMillis(100);

    /**
     * Shared by all widespread-name searches of one pass, so the per-search budget
     * is not multiplied by the number of declarations
     */
    private static final Duration PASS_BUDGET = Duration.ofMillis(300);

    // TODO consulo: AdvancedSettings missing, JB registers advancedSetting "python.code.vision.usages.limit" with default 500
    private static final int PYTHON_CODE_VISION_USAGES_LIMIT_DEFAULT = 500;

    /**
     * `computeForEditor` walks the file on one thread, so no synchronization is needed.
     */
    private final ThreadLocal<PassBudget> myPassBudget = new ThreadLocal<>();

    private static class PassBudget {
        private Duration myRemaining;

        PassBudget(Duration remaining) {
            myRemaining = remaining;
        }

        Duration nextSlice(Duration singleSearchBudget) {
            return myRemaining.compareTo(singleSearchBudget) <= 0 ? myRemaining : singleSearchBudget;
        }

        void charge(Duration spent) {
            myRemaining = myRemaining.minus(spent);
        }
    }

    @Override
    public List<Pair<TextRange, CodeVisionEntry>> computeForEditor(Editor editor, PsiFile file) {
        myPassBudget.set(new PassBudget(pyUsagesSearchBudgetTestOverride != null ? pyUsagesSearchBudgetTestOverride : PASS_BUDGET));
        try {
            return super.computeForEditor(editor, file);
        }
        finally {
            myPassBudget.remove();
        }
    }

    @Override
    public boolean acceptsFile(PsiFile file) {
        return file instanceof PyFile;
    }

    @Override
    public boolean acceptsElement(PsiElement element) {
        if (!element.getManager().isInProject(element)) {
            return false;
        }

        if (element instanceof PyClass && PyUtil.isTopLevel(element)) {
            return true;
        }

        if (element instanceof PyFunction function) {
            if (!PyUtil.isTopLevel(function)) {
                PyClass containingClass = function.getContainingClass();
                if (containingClass == null || !PyUtil.isTopLevel(containingClass)) {
                    return false;
                }
            }
            String elementName = function.getName();
            if (elementName == null) {
                return false;
            }
            return !PyUtilCore.isSpecialName(elementName);
        }

        return false;
    }

    @Override
    public @Nullable CodeVisionInfo getVisionInfo(PsiElement element, PsiFile file) {
        if (!(element instanceof PsiNamedElement namedElement)) {
            return null;
        }
        String elementName = namedElement.getName();
        if (elementName == null) {
            return null;
        }

        Project project = element.getProject();
        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
        // A non-positive advanced-setting value means "no cap"; guard against it so the hint never
        // degrades to "no usages" when the setting is misconfigured or unavailable.
        int configuredLimit = PYTHON_CODE_VISION_USAGES_LIMIT_DEFAULT;
        int maxUsagesToCount = configuredLimit > 0 ? configuredLimit : Integer.MAX_VALUE;

        boolean isWidespreadName = PsiSearchHelper.getInstance(project)
            .isCheapEnoughToSearch(elementName, scope, file, null) == PsiSearchHelper.SearchCostResult.TOO_MANY_OCCURRENCES;

        AtomicInteger regularUsages = new AtomicInteger();
        AtomicInteger dynamicUsages = new AtomicInteger();
        AtomicBoolean isTruncated = new AtomicBoolean();
        Runnable search = () -> countUsages(namedElement, scope, maxUsagesToCount, regularUsages, dynamicUsages, isTruncated);

        if (!isWidespreadName) {
            search.run();
        }
        else {
            Duration singleSearchBudget = pyUsagesSearchBudgetTestOverride != null ? pyUsagesSearchBudgetTestOverride : SINGLE_SEARCH_BUDGET;
            PassBudget budget = myPassBudget.get();
            Duration slice = budget != null ? budget.nextSlice(singleSearchBudget) : singleSearchBudget;
            if (slice.compareTo(Duration.ZERO) <= 0) {
                return null;
            }

            long startedAt = System.nanoTime();
            // The deadline has to be enforced by cancellation rather than checked from the result processor: the
            // processor is not called while the search walks files that contain no match, which is exactly the
            // case this budget exists for. `ReferencesSearch` calls `checkCanceled` often enough for the
            // indicator `withTimeout` cancels to stop it. The counters are updated in place, so a timed-out
            // search still contributes what it managed to find.
            // TODO consulo: ProgressIndicatorUtils.withTimeout(long, Supplier) is not exported to plugins (consulo.application.internal);
            //  JB: val completed = ProgressIndicatorUtils.withTimeout(slice.inWholeMilliseconds) { search(); true } != null
            search.run();
            boolean completed = true;
            if (budget != null) {
                budget.charge(Duration.ofNanos(System.nanoTime() - startedAt));
            }
            if (!completed) {
                isTruncated.set(true);
            }
        }

        int result = regularUsages.get();
        int dynamicResult = dynamicUsages.get();
        if (result == 0 && dynamicResult == 0) {
            return null;
        }
        boolean hasMoreUsages = isTruncated.get();
        if (dynamicResult == 0 || hasMoreUsages) {
            return new CodeVisionInfo(
                PyCodeInsightLocalize.inlayHintsUsagesText(Math.min(result, maxUsagesToCount), hasMoreUsages ? 1 : 0).get(),
                result,
                !hasMoreUsages
            );
        }
        return new CodeVisionInfo(PyCodeInsightLocalize.inlayHintsUsagesWithDynamicText(result, dynamicResult).get(), result);
    }

    private static void countUsages(
        PsiNamedElement element,
        GlobalSearchScope scope,
        int maxUsagesToCount,
        AtomicInteger regularUsages,
        AtomicInteger dynamicUsages,
        AtomicBoolean isTruncated
    ) {
        ReferencesSearch.search(new ReferencesSearch.SearchParameters(element, scope, false))
            .allowParallelProcessing()
            .forEach(it -> {
                if (it == null) {
                    return true;
                }
                else if (element.getReference() == it) {
                    return true;
                }
                else if (PsiTreeUtil.getParentOfType(it.getElement(), PyImportStatementBase.class) != null) {
                    return true; // imports are not usages
                }
                else {
                    if (new UsageInfo(it).isDynamicUsage()) {
                        dynamicUsages.incrementAndGet();
                    }
                    if (regularUsages.incrementAndGet() > maxUsagesToCount) {
                        isTruncated.set(true);
                        return false;
                    }
                    else {
                        return true;
                    }
                }
            });
    }

    @Override
    public @Nullable String getHint(PsiElement element, PsiFile file) {
        CodeVisionInfo info = getVisionInfo(element, file);
        return info != null ? info.text() : null;
    }

    @Override
    public void logClickToFUS(PsiElement element, String hint) {
        // TODO consulo: FUS statistics (CounterUsagesCollector) missing - JB calls PyCodeVisionUsageCollector.logClickToFUS(element)
    }

    @Override
    public List<CodeVisionRelativeOrdering> getRelativeOrderings() {
        return List.of();
    }

    @Override
    public String getId() {
        return ID;
    }
}
