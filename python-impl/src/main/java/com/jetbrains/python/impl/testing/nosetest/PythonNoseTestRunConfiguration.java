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

package com.jetbrains.python.impl.testing.nosetest;

import com.jetbrains.python.impl.PyBundle;
import com.jetbrains.python.impl.sdk.PythonSdkType;
import com.jetbrains.python.impl.testing.AbstractPythonTestRunConfiguration;
import com.jetbrains.python.impl.testing.VFSTestFrameworkListener;
import consulo.content.bundle.Sdk;
import consulo.execution.RuntimeConfigurationException;
import consulo.execution.RuntimeConfigurationWarning;
import consulo.execution.configuration.ConfigurationFactory;
import consulo.execution.configuration.LegacyRunConfigurationConverter;
import consulo.execution.configuration.RunConfigurationOptions;
import consulo.execution.configuration.RunProfileState;
import consulo.execution.executor.Executor;
import consulo.execution.runner.ExecutionEnvironment;
import consulo.process.ExecutionException;
import consulo.project.Project;
import consulo.util.lang.StringUtil;


/**
 * User: catherine
 */
public class PythonNoseTestRunConfiguration extends AbstractPythonTestRunConfiguration
                                          implements PythonNoseTestRunConfigurationParams {
  protected String myTitle = "Nosetest";
  protected String myPluralTitle = "Nosetests";

  public PythonNoseTestRunConfiguration(Project project,
                                        ConfigurationFactory configurationFactory) {
    super(project, configurationFactory);
  }

  @Override
  protected Class<? extends RunConfigurationOptions> getOptionsClass() {
    return PythonNoseTestRunConfigurationOptions.class;
  }

  @Override
  public PythonNoseTestRunConfigurationOptions getOptions() {
    return (PythonNoseTestRunConfigurationOptions) super.getOptions();
  }

  @Override
  protected void convertLegacyState(LegacyRunConfigurationConverter converter) {
    super.convertLegacyState(converter);

    PythonNoseTestRunConfigurationOptions options = new PythonNoseTestRunConfigurationOptions();
    convertLegacyTestState(converter, options);

    PythonNoseTestRunConfigurationLegacyState old = converter.readXml(PythonNoseTestRunConfigurationLegacyState.class);
    options.setParams(old.PARAMS);
    options.setUseParam(old.USE_PARAM);
    converter.setOptions(options);
  }

  @Override
  protected boolean isPatternSupported() {
    return false;
  }




  @Override
  protected String getTitle() {
    return myTitle;
  }

  @Override
  protected String getPluralTitle() {
    return myPluralTitle;
  }

  public RunProfileState getState(Executor executor, ExecutionEnvironment env) throws ExecutionException {
    return new PythonNoseTestCommandLineState(this, env);
  }

  public static void copyParams(PythonNoseTestRunConfigurationParams source, PythonNoseTestRunConfigurationParams target) {
    AbstractPythonTestRunConfiguration.copyParams(source.getTestRunConfigurationParams(), target.getTestRunConfigurationParams());
    target.setParams(source.getParams());
    target.useParam(source.useParam());
  }

  public String getParams() {
    return StringUtil.notNullize(getOptions().getParams());
  }

  public void setParams(String pattern) {
    getOptions().setParams(pattern);
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException
  {
    super.checkConfiguration();
    Sdk sdkPath = PythonSdkType.findSdkByPath(getInterpreterPath());
    if (sdkPath != null && !VFSTestFrameworkListener.getInstance().isNoseTestInstalled(sdkPath))
      throw new RuntimeConfigurationWarning(PyBundle.message("runcfg.testing.no.test.framework", "nosetest"));
  }

  public boolean useParam() {
    return getOptions().isUseParam();
  }

  public void useParam(boolean useParam) {
    getOptions().setUseParam(useParam);
  }
}
