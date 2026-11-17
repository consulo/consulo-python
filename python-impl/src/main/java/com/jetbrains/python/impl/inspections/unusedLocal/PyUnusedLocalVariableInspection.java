// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.unusedLocal;

import com.jetbrains.python.impl.inspections.PyInspection;
import com.jetbrains.python.impl.inspections.PyInspectionVisitor;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.ReadAction;
import consulo.language.editor.inspection.InspectionToolState;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.psi.PsiElementVisitor;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.dataholder.Key;

@ExtensionImpl
public final class PyUnusedLocalVariableInspection extends PyInspection {
    private static final Key<PyUnusedLocalInspectionVisitor> KEY = Key.create("PyUnusedLocal.Visitor");

    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameUnused();
    }

    @Override
    public LocalizeValue getGroupDisplayName() {
        return PyLocalize.inspGroupPythonUnusedSymbols();
    }

    @Override
    public HighlightDisplayLevel getDefaultLevel() {
        return HighlightDisplayLevel.WEAK_WARNING;
    }

    /**
     * JB registration: suppressId="PyUnusedLocal"
     */
    @Override
    public String getID() {
        return "PyUnusedLocal";
    }

    @Override
    public InspectionToolState<?> createStateProvider() {
        return new PyUnusedLocalVariableInspectionState();
    }

    @Override
    public PsiElementVisitor buildVisitor(ProblemsHolder holder,
                                          boolean isOnTheFly,
                                          LocalInspectionToolSession session,
                                          Object state) {
        PyUnusedLocalVariableInspectionState inspectionState = (PyUnusedLocalVariableInspectionState) state;
        PyUnusedLocalInspectionVisitor visitor = new PyUnusedLocalInspectionVisitor(holder,
                                                                                    inspectionState.ignoreTupleUnpacking,
                                                                                    true,
                                                                                    inspectionState.ignoreLoopIterationVariables,
                                                                                    inspectionState.ignoreVariablesStartingWithUnderscore,
                                                                                    PyUnusedLocalInspectionVisitor.ReportTarget.LOCALS,
                                                                                    PyUnusedLocalInspectionVisitor.getSharedAnalysis(session),
                                                                                    PyInspectionVisitor.getContext(session));
        // buildVisitor() will be called on injected files in the same session - don't overwrite if we already have one
        PyUnusedLocalInspectionVisitor existingVisitor = session.getUserData(KEY);
        if (existingVisitor == null) {
            session.putUserData(KEY, visitor);
        }
        return visitor;
    }

    @Override
    public void inspectionFinished(LocalInspectionToolSession session, ProblemsHolder holder, Object state) {
        PyUnusedLocalInspectionVisitor visitor = session.getUserData(KEY);
        if (visitor != null) {
            ReadAction.run(() -> visitor.registerProblems());
            session.putUserData(KEY, null);
        }
    }
}
