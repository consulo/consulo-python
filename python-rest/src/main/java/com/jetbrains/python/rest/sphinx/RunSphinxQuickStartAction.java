// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.rest.sphinx;

import com.jetbrains.python.rest.RestPythonUtil;
import consulo.annotation.component.ActionImpl;
import consulo.annotation.component.ActionParentRef;
import consulo.annotation.component.ActionRef;
import consulo.application.Application;
import consulo.application.dumb.DumbAware;
import consulo.module.Module;
import consulo.module.ModuleManager;
import consulo.project.Project;
import consulo.python.rest.localize.PythonRestLocalize;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.*;
import consulo.ui.ex.action.coroutine.ActionSafeReadLock;
import consulo.util.concurrent.coroutine.Coroutine;

/**
 * user : catherine
 */
@ActionImpl(id = "RunSphinxQuickStartAction", parents = @ActionParentRef(@ActionRef(id = "ToolsMenu")))
public class RunSphinxQuickStartAction extends AnAction implements DumbAware, AnActionWithAsyncUpdate {
    public RunSphinxQuickStartAction() {
        super(
            PythonRestLocalize.actionComIntellijRestructuredtextPythonRunsphinxquickstartactionText(),
            PythonRestLocalize.actionComIntellijRestructuredtextPythonRunsphinxquickstartactionDescription()
        );
    }

    @Override
    public Coroutine<?, ?> updateAsync(AnActionEvent e) {
        return ActionSafeReadLock.run(e, presentation -> {
            // nothing to decide without the lock: only the python sdk of the lazily computed module can disable the action
            presentation.setEnabled(true);
        }, presentation -> {
            RestPythonUtil.updateSphinxQuickStartRequiredAction(e);
        }).toCoroutine();
    }

    @Override
    @RequiredUIAccess
    public void actionPerformed(AnActionEvent e) {
        Presentation presentation = RestPythonUtil.updateSphinxQuickStartRequiredAction(e);
        assert presentation.isEnabled() && presentation.isVisible() : "Sphinx requirements for action are not satisfied";

        Project project = e.getData(Project.KEY);

        if (project == null) {
            return;
        }

        Module module = e.getData(Module.KEY);
        if (module == null) {
            Module[] modules = ModuleManager.getInstance(project).getModules();
            module = modules.length == 0 ? null : modules[0];
        }

        if (module == null) {
            return;
        }
        SphinxBaseCommand action = new SphinxBaseCommand();
        Module finalModule = module;
        Application application = project.getApplication();
        application.invokeLater(() -> action.execute(finalModule), application.getNoneModalityState());
    }
}
