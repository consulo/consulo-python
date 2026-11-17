// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.unusedLocal;

import com.jetbrains.python.impl.inspections.PyInspection;
import com.jetbrains.python.impl.inspections.PyInspectionVisitor;
import consulo.application.ReadAction;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.psi.PsiElementVisitor;
import consulo.util.dataholder.Key;

/**
 * Base class for the unused-parameter and unused-function inspections. Both report a single kind of unused symbol via the shared
 * {@link PyUnusedLocalInspectionVisitor}; the actual control-flow analysis is shared across all unused-symbol inspections running on the
 * same session (see {@link PyUnusedLocalInspectionVisitor.SharedAnalysis}). Unused local variables and other local symbols are reported
 * separately by {@link PyUnusedLocalVariableInspection}.
 */
public abstract class PyUnusedSymbolInspection extends PyInspection {
    private final Key<PyUnusedLocalInspectionVisitor> visitorKey = Key.create(getClass().getSimpleName() + ".Visitor");

    /**
     * The single element kind this inspection reports.
     */
    protected abstract PyUnusedLocalInspectionVisitor.ReportTarget getReportTarget();

    /**
     * Whether lambda parameters should be ignored; only meaningful when {@link #getReportTarget()} is `PARAMETERS`.
     */
    protected boolean shouldIgnoreLambdaParameters(Object state) {
        return true;
    }

    @Override
    public HighlightDisplayLevel getDefaultLevel() {
        return HighlightDisplayLevel.WEAK_WARNING;
    }

    @Override
    public final PsiElementVisitor buildVisitor(ProblemsHolder holder,
                                                boolean isOnTheFly,
                                                LocalInspectionToolSession session,
                                                Object state) {
        PyUnusedLocalInspectionVisitor visitor = new PyUnusedLocalInspectionVisitor(holder, false, shouldIgnoreLambdaParameters(state), false, false,
                                                                                    getReportTarget(),
                                                                                    PyUnusedLocalInspectionVisitor.getSharedAnalysis(session),
                                                                                    PyInspectionVisitor.getContext(session));
        // buildVisitor() will be called on injected files in the same session - don't overwrite if we already have one
        if (session.getUserData(visitorKey) == null) {
            session.putUserData(visitorKey, visitor);
        }
        return visitor;
    }

    @Override
    public final void inspectionFinished(LocalInspectionToolSession session, ProblemsHolder holder, Object state) {
        PyUnusedLocalInspectionVisitor visitor = session.getUserData(visitorKey);
        if (visitor == null) {
            return;
        }
        ReadAction.run(visitor::registerProblems);
        session.putUserData(visitorKey, null);
    }
}
