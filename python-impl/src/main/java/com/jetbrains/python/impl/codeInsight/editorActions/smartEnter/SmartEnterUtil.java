// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.editorActions.smartEnter;

import consulo.ui.ex.action.IdeActions;
import consulo.codeEditor.Editor;
import consulo.codeEditor.action.EditorActionHandler;
import consulo.codeEditor.action.EditorActionManager;
import consulo.codeEditor.util.EditorUtil;

public final class SmartEnterUtil {
    private static EditorActionHandler getEnterHandler() {
        return EditorActionManager.getInstance().getActionHandler(IdeActions.ACTION_EDITOR_START_NEW_LINE);
    }

    public static void plainEnter(final Editor editor) {
        getEnterHandler().execute(editor, editor.getCaretModel().getCurrentCaret(), EditorUtil.getEditorDataContext(editor));
    }

    private SmartEnterUtil() {
    }
}
