// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.InspectionToolState;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.dataholder.Key;

/**
 * Dummy inspection for configuring the PEP8 checker. The checking itself is performed by
 * Pep8ExternalAnnotator.
 */
// TODO consulo: ExternalAnnotatorBatchInspection missing
@ExtensionImpl
public final class PyPep8Inspection extends PyInspection {
    public static final String INSPECTION_SHORT_NAME = "PyPep8Inspection";
    public static final Key<PyPep8Inspection> KEY = Key.create(INSPECTION_SHORT_NAME);

    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspPep8CodingStyleViolation();
    }

    @Override
    public LocalizeValue getGroupDisplayName() {
        return PyLocalize.inspGroupPythonCodeStyleIssues();
    }

    @Override
    public HighlightDisplayLevel getDefaultLevel() {
        return HighlightDisplayLevel.DO_NOT_SHOW;
    }

    /**
     * JB {@code ignoredErrors} option lives in {@link PyPep8InspectionState}.
     */
    @Override
    public InspectionToolState<?> createStateProvider() {
        return new PyPep8InspectionState();
    }

    @Override
    public String getShortName() {
        return INSPECTION_SHORT_NAME;
    }
}
