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
    if (StringUtil.isEmptyOrSpaces(getInputFile()))
      throw new RuntimeConfigurationError("Please specify input file name.");
  }

  @Override
  public String suggestedName() {
    return "docutils task in " + getName();
  }
}
