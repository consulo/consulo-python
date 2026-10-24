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

package com.jetbrains.python.impl.testing.doctest;

import com.jetbrains.python.impl.testing.AbstractPythonTestRunConfiguration;
import com.jetbrains.python.impl.testing.PythonTestRunConfigurationOptions;
import consulo.execution.configuration.ConfigurationFactory;
import consulo.execution.configuration.LegacyRunConfigurationConverter;
import consulo.execution.configuration.RunConfigurationOptions;
import consulo.execution.configuration.RunProfileState;
import consulo.execution.executor.Executor;
import consulo.execution.runner.ExecutionEnvironment;
import consulo.process.ExecutionException;
import consulo.project.Project;


/**
 * User: catherine
 */
public class PythonDocTestRunConfiguration extends AbstractPythonTestRunConfiguration
  implements PythonDocTestRunConfigurationParams {
  protected String myPluralTitle = "Doctests";
  protected String myTitle = "Doctest";

  public PythonDocTestRunConfiguration(Project project,
                                       ConfigurationFactory configurationFactory) {
    super(project, configurationFactory);
  }

  @Override
  protected Class<? extends RunConfigurationOptions> getOptionsClass() {
    return PythonTestRunConfigurationOptions.class;
  }

  @Override
  public PythonTestRunConfigurationOptions getOptions() {
    return (PythonTestRunConfigurationOptions) super.getOptions();
  }

  @Override
  protected void convertLegacyState(LegacyRunConfigurationConverter converter) {
    super.convertLegacyState(converter);

    PythonTestRunConfigurationOptions options = new PythonTestRunConfigurationOptions();
    convertLegacyTestState(converter, options);
    converter.setOptions(options);
  }


  @Override
  public RunProfileState getState(Executor executor, ExecutionEnvironment env) throws ExecutionException {
    return new PythonDocTestCommandLineState(this, env);
  }



  @Override
  protected String getTitle() {
    return myTitle;
  }

  @Override
  protected String getPluralTitle() {
    return myPluralTitle;
  }

  public static void copyParams(PythonDocTestRunConfigurationParams source, PythonDocTestRunConfigurationParams target) {
    copyParams(source.getTestRunConfigurationParams(), target.getTestRunConfigurationParams());
  }
}
