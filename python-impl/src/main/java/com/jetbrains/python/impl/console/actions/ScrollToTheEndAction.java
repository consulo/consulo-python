// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.console.actions;

import consulo.codeEditor.Editor;
import consulo.codeEditor.util.EditorUtil;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.platform.base.localize.ActionLocalize;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;

public class ScrollToTheEndAction extends AnAction {
    private final Editor editor;

    public ScrollToTheEndAction(Editor editor) {
        super(
            ActionLocalize.actionEditorconsolescrolltotheendText(),
            ActionLocalize.actionEditorconsolescrolltotheendText(),
            PlatformIconGroup.runconfigurationsScroll_down()
        );
        this.editor = editor;
    }

    @Override
    @RequiredUIAccess
    public void actionPerformed(AnActionEvent e) {
        EditorUtil.scrollToTheEnd(editor);
    }
}
