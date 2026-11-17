// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.actions;

import consulo.codeEditor.action.EditorAction;
import consulo.python.impl.localize.PyCodeInsightLocalize;

/**
 * Ported from JB {@code PyTypeAnnotationsFoldingActions.kt}.
 */
public class PyExpandTypeAnnotationsAction extends EditorAction {
    public PyExpandTypeAnnotationsAction() {
        super(
            PyCodeInsightLocalize.actionExpandpythontypeannotationsText(),
            PyCodeInsightLocalize.actionExpandpythontypeannotationsDescription(),
            new PyTypeAnnotationsFoldingHandler(true)
        );
    }
}
