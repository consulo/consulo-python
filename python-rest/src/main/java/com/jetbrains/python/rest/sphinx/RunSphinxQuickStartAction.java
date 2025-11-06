/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.rest.sphinx;

import com.jetbrains.python.rest.RestPythonUtil;
import consulo.annotation.component.ActionImpl;
import consulo.annotation.component.ActionParentRef;
import consulo.annotation.component.ActionRef;
import consulo.application.Application;
import consulo.application.dumb.DumbAware;
import consulo.localize.LocalizeValue;
import consulo.module.Module;
import consulo.module.ModuleManager;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.Presentation;
import jakarta.annotation.Nonnull;

/**
 * @author catherine
 */
@ActionImpl(id = "RunSphinxQuickStartAction", parents = @ActionParentRef(@ActionRef(id = "ToolsMenu")))
public class RunSphinxQuickStartAction extends AnAction implements DumbAware {
    public RunSphinxQuickStartAction() {
        super(LocalizeValue.localizeTODO("Sphinx quickstart"), LocalizeValue.localizeTODO("Allows to run sphinx quick-start action"));
    }

    @Override
    public void update(@Nonnull AnActionEvent event) {
        super.update(event);
        RestPythonUtil.updateSphinxQuickStartRequiredAction(event);
    }

    @Override
    @RequiredUIAccess
    public void actionPerformed(@Nonnull AnActionEvent e) {
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
