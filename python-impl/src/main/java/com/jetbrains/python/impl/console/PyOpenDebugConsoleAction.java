/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.impl.console;

import com.jetbrains.python.impl.PythonIcons;
import consulo.application.dumb.DumbAware;
import consulo.application.ui.wm.ApplicationIdeFocusManager;
import consulo.dataContext.DataContext;
import consulo.execution.ExecutionHelper;
import consulo.execution.ui.RunContentDescriptor;
import consulo.language.editor.CommonDataKeys;
import consulo.process.ProcessHandler;
import consulo.project.Project;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.function.Consumer;

/**
 * @author traff
 */
public class PyOpenDebugConsoleAction extends AnAction implements DumbAware {

  public PyOpenDebugConsoleAction() {
    super();
    getTemplatePresentation().setIcon(PythonIcons.Python.Debug.CommandLine);
  }

  @Override
  public void update(final AnActionEvent e) {
    e.getPresentation().setVisible(false);
    e.getPresentation().setEnabled(true);
    final Project project = e.getData(CommonDataKeys.PROJECT);
    if (project != null) {
      e.getPresentation().setVisible(getConsoles(project).size() > 0);
    }
  }

  @Override
  public void actionPerformed(final AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    if (project != null) {
      selectRunningProcess(e.getDataContext(), project, view -> {
        view.enableConsole(false);
        ApplicationIdeFocusManager.getInstance().getInstanceForProject(project).requestFocus(view.getPydevConsoleView().getComponent(), true);
      });
    }
  }


  private static void selectRunningProcess(@Nonnull DataContext dataContext,
                                           @Nonnull Project project,
                                           final Consumer<PythonDebugLanguageConsoleView> consumer) {
    Collection<RunContentDescriptor> consoles = getConsoles(project);

    ExecutionHelper.selectContentDescriptor(dataContext, project, consoles, "Select running python process", descriptor -> {
      if (descriptor != null && descriptor.getExecutionConsole() instanceof PythonDebugLanguageConsoleView) {
        consumer.accept((PythonDebugLanguageConsoleView)descriptor.getExecutionConsole());
      }
    });
  }

  private static Collection<RunContentDescriptor> getConsoles(Project project) {
    return ExecutionHelper.findRunningConsole(project,
                                              dom -> dom.getExecutionConsole() instanceof PythonDebugLanguageConsoleView && isAlive(dom));
  }

  private static boolean isAlive(RunContentDescriptor dom) {
    ProcessHandler processHandler = dom.getProcessHandler();
    return processHandler != null && !processHandler.isProcessTerminated();
  }
}
