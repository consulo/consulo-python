// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.impl.inspections.quickfix.PySuppressWithTypeIgnoreFix;
import consulo.language.editor.annotation.SuppressableProblemGroup;
import consulo.language.editor.inspection.SuppressIntentionActionFromFix;
import consulo.language.editor.inspection.SuppressQuickFix;
import consulo.language.editor.intention.SuppressIntentionAction;
import consulo.language.psi.PsiElement;
import consulo.util.collection.ArrayUtil;
import org.jspecify.annotations.Nullable;

/**
 * Provides the Alt-Enter "Suppress for this statement / for function 'f' / for class 'C'" actions for a single
 * {@link PyTypeCheckerInspection} problem tagged with {@code code}. The {@code # noinspection} actions reuse
 * {@link PyInspectionsSuppressor#createSuppressActions} for identical, declaration-naming wording, but insert a
 * {@code # noinspection <code.id>} comment (e.g. {@code bad-return}), which {@link PySuppressionUtil} then recognizes. A
 * "Suppress with {@code # type: ignore}" action ({@link PySuppressWithTypeIgnoreFix}) using the same granular code is
 * offered alongside them.
 * <p>
 * {@link #getProblemName} returns {@code null} on purpose: a non-null name would re-key the highlight's
 * {@code HighlightDisplayKey} to the code (for which no inspection tool is registered), and the highlight would be
 * dropped. The problem must stay keyed to the {@code PyTypeChecker} inspection for severity/enablement.
 */
final class PyTypeCheckerSuppressableProblemGroup implements SuppressableProblemGroup {
    private final PyTypeCheckerSuppressionCode code;

    PyTypeCheckerSuppressableProblemGroup(PyTypeCheckerSuppressionCode code) {
        this.code = code;
    }

    /**
     * The granular code id (e.g. {@code unsupported-operator}); read off a descriptor by {@code PyIgnoreCodeComputer}.
     */
    public String getCodeId() {
        return code.getId();
    }

    @Override
    public @Nullable String getProblemName() {
        return null;
    }

    @Override
    public SuppressIntentionAction[] getSuppressActions(@Nullable PsiElement element) {
        SuppressQuickFix[] fixes = ArrayUtil.append(PyInspectionsSuppressor.createSuppressActions(code.getId(), element, true),
                                                    PySuppressWithTypeIgnoreFix.forCode(code.getId()));
        return SuppressIntentionActionFromFix.convertBatchToSuppressIntentionActions(fixes);
    }
}
