// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.actions;

import com.jetbrains.python.impl.PythonFoldingBuilder;
import consulo.codeEditor.Caret;
import consulo.codeEditor.Editor;
import consulo.codeEditor.FoldRegion;
import consulo.dataContext.DataContext;
import consulo.ide.impl.idea.codeInsight.folding.impl.actions.BaseFoldingHandler;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Ported from JB {@code PyTypeAnnotationsFoldingActions.kt}.
 */
public class PyTypeAnnotationsFoldingHandler extends BaseFoldingHandler {
    private final boolean myExpand;

    public PyTypeAnnotationsFoldingHandler(boolean expand) {
        myExpand = expand;
    }

    public boolean getExpand() {
        return myExpand;
    }

    @Override
    protected void doExecute(Editor editor, @Nullable Caret caret, DataContext dataContext) {
        List<FoldRegion> pythonTypeRegions = getFoldRegionsForSelection(editor, caret)
            .stream()
            .filter(it -> it.getGroup() != null
                && it.getGroup().toString().equals(PythonFoldingBuilder.PYTHON_TYPE_ANNOTATION_GROUP_NAME))
            .toList();
        editor.getFoldingModel().runBatchFoldingOperation(() -> {
            for (FoldRegion region : pythonTypeRegions) {
                region.setExpanded(myExpand);
            }
        });
    }
}
