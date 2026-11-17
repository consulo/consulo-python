// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.unusedLocal;

import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.InspectionToolState;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;

/**
 * Reports unused function parameters. Unused local variables and other local symbols are reported separately by
 * {@link PyUnusedLocalVariableInspection}; both inspections share {@link PyUnusedLocalInspectionVisitor} for the analysis.
 */
@ExtensionImpl
public class PyUnusedParameterInspection extends PyUnusedSymbolInspection {
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameUnusedParameter();
    }

    @Override
    public LocalizeValue getGroupDisplayName() {
        return PyLocalize.inspGroupPythonUnusedSymbols();
    }

    @Override
    public InspectionToolState<?> createStateProvider() {
        return new PyUnusedParameterInspectionState();
    }

    @Override
    protected PyUnusedLocalInspectionVisitor.ReportTarget getReportTarget() {
        return PyUnusedLocalInspectionVisitor.ReportTarget.PARAMETERS;
    }

    @Override
    protected boolean shouldIgnoreLambdaParameters(Object state) {
        return ((PyUnusedParameterInspectionState) state).ignoreLambdaParameters;
    }
}
