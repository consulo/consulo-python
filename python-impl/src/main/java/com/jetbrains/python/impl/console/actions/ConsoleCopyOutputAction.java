// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.console.actions;

import com.jetbrains.python.impl.console.PythonConsoleView;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.python.impl.localize.PyLocalize;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.awt.CopyPasteManager;

import java.awt.datatransfer.StringSelection;

public class ConsoleCopyOutputAction extends AnAction {
    private final PythonConsoleView consoleView;

    public ConsoleCopyOutputAction(PythonConsoleView consoleView) {
        super(
            PyLocalize.pydevConsoleRunnerCopyConsoleOutputText(),
            PyLocalize.pydevConsoleRunnerCopyConsoleOutputDescription(),
            PlatformIconGroup.actionsCopy()
        );
        this.consoleView = consoleView;
    }

    @Override
    @RequiredUIAccess
    public void actionPerformed(AnActionEvent e) {
        String content = consoleView.getHistoryViewer().getDocument().getCharsSequence().toString();
        CopyPasteManager.getInstance().setContents(new StringSelection(content));
    }
}
