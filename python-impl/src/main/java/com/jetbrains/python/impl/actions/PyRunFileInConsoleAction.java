// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.actions;

import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.impl.run.PythonRunConfiguration;
import com.jetbrains.python.impl.run.PythonRunConfigurationProducer;
import com.jetbrains.python.psi.PyFile;
import consulo.annotation.component.ActionImpl;
import consulo.annotation.component.ActionParentRef;
import consulo.annotation.component.ActionRef;
import consulo.application.dumb.DumbAware;
import consulo.execution.ExecutionManager;
import consulo.execution.RunManager;
import consulo.execution.RunnerAndConfigurationSettings;
import consulo.execution.action.ConfigurationContext;
import consulo.execution.action.ConfigurationFromContext;
import consulo.execution.action.RunConfigurationProducer;
import consulo.execution.executor.DefaultRunExecutor;
import consulo.execution.runner.ExecutionEnvironmentBuilder;
import consulo.language.psi.PsiFile;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.ActionUpdateThread;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.AnActionWithSyncUpdate;
import consulo.ui.ex.action.IdeActions;
import consulo.ui.ex.action.Presentation;

import java.util.HashMap;

@ActionImpl(id = "PyRunFileInConsole", parents = @ActionParentRef(@ActionRef(id = IdeActions.GROUP_EDITOR_POPUP)))
public class PyRunFileInConsoleAction extends AnAction implements DumbAware, AnActionWithSyncUpdate {
  private static final HashMap<PythonRunConfiguration, Boolean> waitingForExecution = new HashMap<>();

  public PyRunFileInConsoleAction() {
    super(PyLocalize.actonRunFileInPythonConsoleTitle(),
          PyLocalize.actionRunFileInPythonConsoleDescription(), PythonFileType.INSTANCE.getIcon());
  }

  @Override
  public ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(AnActionEvent e) {
    final Project project = e.getData(Project.KEY);
    final PsiFile psiFile = e.getData(PsiFile.KEY);
    final Presentation presentation = e.getPresentation();
    presentation.setEnabledAndVisible(project != null && psiFile instanceof PyFile);
  }

  @Override
  @RequiredUIAccess
  public void actionPerformed(AnActionEvent e) {
    final PsiFile file = e.getData(PsiFile.KEY);
    if (file == null) return;
    final Project project = e.getData(Project.KEY);
    if (project == null) return;
    final ConfigurationContext context = ConfigurationContext.getFromContext(e.getDataContext());
    PythonRunConfigurationProducer configProducer = RunConfigurationProducer.getInstance(PythonRunConfigurationProducer.class);
    RunnerAndConfigurationSettings settings = configProducer.findExistingConfiguration(context);
    RunManager runManager = RunManager.getInstance(project);
    if (settings == null) {
      final ConfigurationFromContext fromContext = configProducer.createConfigurationFromContext(context);
      if (fromContext == null) return;
      settings = fromContext.getConfigurationSettings();
      // consulo: RunManagerEx.setTemporaryConfiguration is internal API
      settings.setTemporary(true);
      runManager.addConfiguration(settings, false);
    }
    final PythonRunConfiguration configuration = (PythonRunConfiguration)settings.getConfiguration();
    runManager.setSelectedConfiguration(settings);
    ExecutionEnvironmentBuilder builder = ExecutionEnvironmentBuilder.createOrNull(DefaultRunExecutor.getRunExecutorInstance(), settings);
    if (builder != null) {
      boolean oldValueShowCommandline = configuration.showCommandLineAfterwards();
      configuration.setShowCommandLineAfterwards(true);
      synchronized (this) {
        waitingForExecution.put(configuration, oldValueShowCommandline);
      }

      ExecutionManager.getInstance(project).restartRunProfile(builder.build());
    }
  }

  /*
    Restore the option which was changed for the action execution
   */
  public static synchronized void configExecuted(PythonRunConfiguration configuration) {
    if (waitingForExecution.containsKey(configuration)) {
      Boolean oldValue = waitingForExecution.remove(configuration);
      configuration.setShowCommandLineAfterwards(oldValue);
    }
  }
}
