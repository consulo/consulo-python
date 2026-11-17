// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.unusedLocal;

import consulo.annotation.component.ExtensionImpl;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;

/**
 * Reports unused local functions. Unused local variables and other local symbols are reported separately by
 * {@link PyUnusedLocalVariableInspection}; both inspections share {@link PyUnusedLocalInspectionVisitor} for the analysis.
 */
@ExtensionImpl
public class PyUnusedFunctionInspection extends PyUnusedSymbolInspection {
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameUnusedFunction();
    }

    @Override
    public LocalizeValue getGroupDisplayName() {
        return PyLocalize.inspGroupPythonUnusedSymbols();
    }

    @Override
    protected PyUnusedLocalInspectionVisitor.ReportTarget getReportTarget() {
        return PyUnusedLocalInspectionVisitor.ReportTarget.FUNCTIONS;
    }
}
