// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.console;

import com.jetbrains.python.impl.actions.PyExecuteInConsole;
import consulo.annotation.component.ActionImpl;
import consulo.annotation.component.ActionParentRef;
import consulo.annotation.component.ActionRef;
import consulo.application.dumb.DumbAware;
import consulo.module.Module;
import consulo.project.Project;
import consulo.python.impl.icon.PythonImplIconGroup;
import consulo.python.impl.localize.PyLocalize;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.AnActionWithSyncUpdate;
import consulo.ui.ex.action.IdeActions;

@ActionImpl(
    id = "com.jetbrains.python.console.RunPythonOrDebugConsoleAction",
    parents = @ActionParentRef(@ActionRef(id = IdeActions.TOOLS_MENU))
)
public class RunPythonOrDebugConsoleAction extends AnAction implements DumbAware, AnActionWithSyncUpdate {

    public RunPythonOrDebugConsoleAction() {
        super(
            PyLocalize.actionComJetbrainsPythonConsoleRunpythonordebugconsoleactionText(),
            PyLocalize.actionComJetbrainsPythonConsoleRunpythonordebugconsoleactionDescription()
        );
        getTemplatePresentation().setIcon(PythonImplIconGroup.pythonPythonconsole());
    }

    @Override
    public void update(AnActionEvent e) {
        e.getPresentation().setVisible(true);
        e.getPresentation().setEnabled(false);
        Project project = e.getData(Project.KEY);
        if (project != null) {
            boolean hasInterpreter = PythonConsoleStarter.hasPythonConsoleInterpreter(project, e.getData(Module.KEY));
            PythonConsoleStarter.setConsoleInterpreterState(
                e.getPresentation(), hasInterpreter,
                PyLocalize.pythonConsoleNoInterpreterProject(), getTemplatePresentation().getDescription());
        }
    }

    @Override
    @RequiredUIAccess
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getData(Project.KEY);
        if (project == null) {
            return;
        }
        PyExecuteInConsole.executeCodeInConsole(project, (String) null, null, true, true, true, null);
    }
}
