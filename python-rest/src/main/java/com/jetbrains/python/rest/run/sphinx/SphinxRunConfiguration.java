// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.rest.run.sphinx;

import com.jetbrains.python.impl.run.PythonAddContentRootsFragment;
import com.jetbrains.python.impl.run.PythonAddSourceRootsFragment;
import com.jetbrains.python.impl.run.PythonInterpreterFragment;
import com.jetbrains.python.impl.run.PythonInterpreterOptionsFragment;
import com.jetbrains.python.rest.run.RestRunConfiguration;
import com.jetbrains.python.rest.run.RestTaskFragment;
import consulo.execution.RuntimeConfigurationException;
import consulo.execution.configuration.ConfigurationFactory;
import consulo.execution.configuration.RunProfileState;
import consulo.execution.configuration.RuntimeConfigurationError;
import consulo.execution.configuration.fragment.RunConfigurationFragment;
import consulo.execution.executor.Executor;
import consulo.execution.runner.ExecutionEnvironment;
import consulo.process.ExecutionException;
import consulo.project.Project;
import consulo.python.rest.localize.PythonRestLocalize;
import consulo.util.lang.StringUtil;

import java.util.List;


/**
 * User : catherine
 */
public class SphinxRunConfiguration extends RestRunConfiguration {
  public SphinxRunConfiguration(Project project,
                                ConfigurationFactory factory) {
    super(project, factory);
  }

  // TODO consulo: JB adds the "pdf" task when rst2pdf is installed in the interpreter (PythonPackageManager#hasInstalledPackageSnapshot)
  @Override
  public List<RunConfigurationFragment<?>> createFragments() {
    return List.of(
      new RestTaskFragment(SphinxTasksModel.getTasks(), SphinxTasksModel.DEFAULT_TASK, true),
      new PythonInterpreterFragment<>(),
      new PythonInterpreterOptionsFragment<>(),
      new PythonAddContentRootsFragment<>(),
      new PythonAddSourceRootsFragment<>()
    );
  }

  @Override
  public RunProfileState getState(Executor executor, ExecutionEnvironment env) throws ExecutionException {
    return new SphinxCommandLineState(this, env);
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException
  {
    super.checkConfiguration();
    if (StringUtil.isEmptyOrSpaces(getInputFile())) {
      throw new RuntimeConfigurationError(PythonRestLocalize.pythonRestSpecifyInputDirectoryName());
    }
    if (StringUtil.isEmptyOrSpaces(getOutputFile())) {
      throw new RuntimeConfigurationError(PythonRestLocalize.pythonRestSpecifyOutputDirectoryName());
    }
  }

  @Override
  public String suggestedName() {
    return PythonRestLocalize.pythonRestSphinxRunCfgDefaultName(getName()).get();
  }
}
