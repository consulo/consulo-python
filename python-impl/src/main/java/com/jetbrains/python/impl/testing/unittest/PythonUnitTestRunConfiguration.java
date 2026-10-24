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

package com.jetbrains.python.impl.testing.unittest;

import com.jetbrains.python.impl.testing.AbstractPythonTestRunConfiguration;
import consulo.execution.configuration.ConfigurationFactory;
import consulo.execution.configuration.LegacyRunConfigurationConverter;
import consulo.execution.configuration.RunConfigurationOptions;
import consulo.execution.configuration.RunProfileState;
import consulo.execution.configuration.fragment.RunConfigurationFragment;
import consulo.execution.executor.Executor;
import consulo.execution.runner.ExecutionEnvironment;
import consulo.process.ExecutionException;
import consulo.project.Project;
import consulo.util.lang.StringUtil;

import java.util.ArrayList;
import java.util.List;


/**
 * @author Leonid Shalupov
 */
public class PythonUnitTestRunConfiguration extends
                                            AbstractPythonTestRunConfiguration
                                              implements PythonUnitTestRunConfigurationParams {
  protected String myTitle = "Unittest";
  protected String myPluralTitle = "Unittests";


  public PythonUnitTestRunConfiguration(Project project,
                                        ConfigurationFactory configurationFactory) {
    super(project, configurationFactory);
  }

  @Override
  protected Class<? extends RunConfigurationOptions> getOptionsClass() {
    return PythonUnitTestRunConfigurationOptions.class;
  }

  @Override
  public PythonUnitTestRunConfigurationOptions getOptions() {
    return (PythonUnitTestRunConfigurationOptions) super.getOptions();
  }

  @Override
  protected void convertLegacyState(LegacyRunConfigurationConverter converter) {
    super.convertLegacyState(converter);

    PythonUnitTestRunConfigurationOptions options = new PythonUnitTestRunConfigurationOptions();
    convertLegacyTestState(converter, options);

    PythonUnitTestRunConfigurationLegacyState old = converter.readXml(PythonUnitTestRunConfigurationLegacyState.class);
    options.setPureUnittest(old.PUREUNITTEST);
    options.setParams(old.PARAMS);
    options.setUseParam(old.USE_PARAM);
    converter.setOptions(options);
  }

  @Override
  public List<RunConfigurationFragment<?>> createFragments() {
    List<RunConfigurationFragment<?>> fragments = new ArrayList<>(super.createFragments());
    fragments.add(new PythonPureUnittestFragment());
    return fragments;
  }


  @Override
  public RunProfileState getState(Executor executor, ExecutionEnvironment env) throws ExecutionException {
    return new PythonUnitTestCommandLineState(this, env);
  }



  @Override
  protected String getTitle() {
    return myTitle;
  }

  @Override
  protected String getPluralTitle() {
    return myPluralTitle;
  }

  public static void copyParams(PythonUnitTestRunConfigurationParams source, PythonUnitTestRunConfigurationParams target) {
    copyParams(source.getTestRunConfigurationParams(), target.getTestRunConfigurationParams());
    target.setPureUnittest(source.isPureUnittest());
    target.setParams(source.getParams());
    target.useParam(source.useParam());
  }

  @Override
  public boolean isPureUnittest() {
    return getOptions().isPureUnittest();
  }

  public void setPureUnittest(boolean isPureUnittest) {
    getOptions().setPureUnittest(isPureUnittest);
  }

  public String getParams() {
    return StringUtil.notNullize(getOptions().getParams());
  }

  public void setParams(String pattern) {
    getOptions().setParams(pattern);
  }

  public boolean useParam() {
    return getOptions().isUseParam();
  }

  public void useParam(boolean useParam) {
    getOptions().setUseParam(useParam);
  }
}
