// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.debugger;

import consulo.ui.ex.action.ActionUpdateThread;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.Presentation;
import consulo.ui.ex.action.ToggleAction;
import consulo.project.Project;
import consulo.execution.debug.XDebugProcess;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import com.jetbrains.python.impl.debugger.settings.PyDebuggerSettings;

import java.util.function.Consumer;

public final class WatchReturnValuesAction extends ToggleAction {
    private volatile boolean myWatchesReturnValues;
    private final XDebugProcess myProcess;
    private final LocalizeValue myText;
    private final Consumer<Boolean> myToggleCallback;

    public WatchReturnValuesAction(XDebugProcess frameAccessor, Consumer<Boolean> toggleCallback) {
        super(LocalizeValue.empty(), PyLocalize.debuggerWatchReturnValuesDescription(), null);
        myWatchesReturnValues = PyDebuggerSettings.getInstance().isWatchReturnValues();
        myProcess = frameAccessor;
        myText = PyLocalize.debuggerWatchShowReturnValues();
        myToggleCallback = toggleCallback;
    }

    @Override
    public void update(AnActionEvent e) {
        super.update(e);
        Presentation presentation = e.getPresentation();
        presentation.setEnabled(true);
        presentation.setText(myText);
    }

    @Override
    public ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
        return myWatchesReturnValues;
    }

    @Override
    public void setSelected(AnActionEvent e, boolean watch) {
        myWatchesReturnValues = watch;
        PyDebuggerSettings.getInstance().setWatchReturnValues(watch);
        Project project = e.getData(Project.KEY);
        if (project != null) {
            if (myToggleCallback != null) {
                myToggleCallback.accept(myWatchesReturnValues);
            }
            myProcess.getSession().rebuildViews();
        }
    }
}