// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.rest.run.docutils;

import com.jetbrains.python.impl.run.PythonAddContentRootsFragment;
import com.jetbrains.python.impl.run.PythonAddSourceRootsFragment;
import com.jetbrains.python.impl.run.PythonInterpreterFragment;
import com.jetbrains.python.impl.run.PythonInterpreterOptionsFragment;
import com.jetbrains.python.rest.run.RestOpenInBrowserFragment;
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
public class DocutilsRunConfiguration extends RestRunConfiguration {

  public DocutilsRunConfiguration(Project project,
                                  ConfigurationFactory factory) {
    super(project, factory);
  }

  @Override
  public List<RunConfigurationFragment<?>> createFragments() {
    return List.of(
      new RestTaskFragment(DocutilsTasksModel.getTasks(), DocutilsTasksModel.DEFAULT_TASK, false),
      new RestOpenInBrowserFragment(),
      new PythonInterpreterFragment<>(),
      new PythonInterpreterOptionsFragment<>(),
      new PythonAddContentRootsFragment<>(),
      new PythonAddSourceRootsFragment<>()
    );
  }

  @Override
  public RunProfileState getState(Executor executor, ExecutionEnvironment env) throws ExecutionException {
    return new DocutilsCommandLineState(this, env);
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException
  {
    super.checkConfiguration();
    if (StringUtil.isEmptyOrSpaces(getInputFile())) {
      throw new RuntimeConfigurationError(PythonRestLocalize.pythonRestSpecifyInputFileName());
    }
  }

  @Override
  public String suggestedName() {
    return PythonRestLocalize.pythonRestDocutilsRunCfgDefaultName(getName()).get();
  }
}
