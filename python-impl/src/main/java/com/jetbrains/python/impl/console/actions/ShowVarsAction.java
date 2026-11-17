// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.impl.console.actions;

import com.jetbrains.python.impl.console.PydevConsoleCommunication;
import com.jetbrains.python.impl.console.PythonConsoleView;
import consulo.application.dumb.DumbAware;
import consulo.execution.debug.icon.ExecutionDebugIconGroup;
import consulo.python.impl.localize.PyLocalize;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.ToggleAction;

/**
 * Created by Yuli Fiterman on 9/18/2016.
 */
public class ShowVarsAction extends ToggleAction implements DumbAware {
    private final PythonConsoleView consoleView;
    private final PydevConsoleCommunication consoleComm;

    public ShowVarsAction(PythonConsoleView consoleView, PydevConsoleCommunication consoleComm) {
        super(
            PyLocalize.consoleShowVariablesTitle(),
            PyLocalize.consoleShowVariablesDescription(),
            ExecutionDebugIconGroup.nodeWatch()
        );
        this.consoleView = consoleView;
        this.consoleComm = consoleComm;
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
        return consoleView.isShowVars();
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
        consoleView.setShowVars(state);

        if (state) {
            consoleView.showVariables(consoleComm);
        }
        else {
            consoleView.restoreWindow();
        }
    }
}
