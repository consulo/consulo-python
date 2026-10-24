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

package com.jetbrains.python.impl.testing.pytest;

import com.jetbrains.python.impl.PyBundle;
import com.jetbrains.python.impl.run.PythonAddContentRootsFragment;
import com.jetbrains.python.impl.run.PythonAddSourceRootsFragment;
import com.jetbrains.python.impl.run.PythonInterpreterFragment;
import com.jetbrains.python.impl.run.PythonInterpreterOptionsFragment;
import com.jetbrains.python.impl.sdk.PythonSdkType;
import com.jetbrains.python.impl.testing.AbstractPythonTestRunConfiguration;
import com.jetbrains.python.impl.testing.PythonTestParamsFragment;
import com.jetbrains.python.impl.testing.VFSTestFrameworkListener;
import consulo.content.bundle.Sdk;
import consulo.execution.RuntimeConfigurationException;
import consulo.execution.RuntimeConfigurationWarning;
import consulo.execution.configuration.ConfigurationFactory;
import consulo.execution.configuration.LegacyRunConfigurationConverter;
import consulo.execution.configuration.RunConfigurationOptions;
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
 * @author yole
 */
public class PyTestRunConfiguration extends AbstractPythonTestRunConfiguration implements PyTestRunConfigurationParams {

  protected String myTitle = "py.test";
  protected String myPluralTitle = "py.tests";



  public PyTestRunConfiguration(Project project, ConfigurationFactory factory) {
    super(project, factory);
  }

  @Override
  protected Class<? extends RunConfigurationOptions> getOptionsClass() {
    return PyTestRunConfigurationOptions.class;
  }

  @Override
  public PyTestRunConfigurationOptions getOptions() {
    return (PyTestRunConfigurationOptions) super.getOptions();
  }

  @Override
  protected void convertLegacyState(LegacyRunConfigurationConverter converter) {
    super.convertLegacyState(converter);

    PyTestRunConfigurationOptions options = new PyTestRunConfigurationOptions();
    convertLegacyTestState(converter, options);

    PyTestRunConfigurationLegacyState old = converter.readXml(PyTestRunConfigurationLegacyState.class);
    options.setTestToRun(old.testToRun);
    options.setKeywords(old.keywords);
    options.setUseKeyword(old.USE_KEYWORD);
    options.setParams(old.params);
    options.setUseParam(old.USE_PARAM);
    converter.setOptions(options);
  }

  @Override
  public List<RunConfigurationFragment<?>> createFragments() {
    return List.of(
      new PyTestTargetFragment(),
      new PyTestKeywordsFragment(),
      new PythonTestParamsFragment<PyTestRunConfiguration>(),
      new PythonInterpreterFragment<PyTestRunConfiguration>(),
      new PythonInterpreterOptionsFragment<PyTestRunConfiguration>(),
      new PythonAddContentRootsFragment<PyTestRunConfiguration>(),
      new PythonAddSourceRootsFragment<PyTestRunConfiguration>()
    );
  }


  public RunProfileState getState(Executor executor, ExecutionEnvironment env) throws ExecutionException {
    return new PyTestCommandLineState(this, env);
  }

  public String getTestToRun() {
    return StringUtil.notNullize(getOptions().getTestToRun());
  }

  public void setTestToRun(String testToRun) {
    getOptions().setTestToRun(testToRun);
  }

  public String getKeywords() {
    if (useKeyword()) {
      return StringUtil.notNullize(getOptions().getKeywords());
    }
    return "";
  }

  public void setKeywords(String keywords) {
    getOptions().setKeywords(keywords);
  }

  public void setParams(String params) {
    getOptions().setParams(params);
  }

  public String getParams() {
    if (useParam()) {
      return StringUtil.notNullize(getOptions().getParams());
    }
    return "";
  }

  public boolean useParam() {
    return getOptions().isUseParam();
  }

  public void useParam(boolean useParam) {
    getOptions().setUseParam(useParam);
  }

  public boolean useKeyword() {
    return getOptions().isUseKeyword();
  }

  public void useKeyword(boolean useKeyword) {
    getOptions().setUseKeyword(useKeyword);
  }




  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    if (StringUtil.isEmptyOrSpaces(getTestToRun())) {
      throw new RuntimeConfigurationError("Please specify target folder or script");
    }
    Sdk sdkPath = PythonSdkType.findSdkByPath(getInterpreterPath());
    if (sdkPath != null && !VFSTestFrameworkListener.getInstance().isPyTestInstalled(sdkPath))
      throw new RuntimeConfigurationWarning(PyBundle.message("runcfg.testing.no.test.framework", "py.test"));
  }

  @Override
  public String suggestedName() {
    return "py.test in " + getName();
  }

  @Override
  protected String getTitle() {
    return myTitle;
  }

  @Override
  protected String getPluralTitle() {
    return myPluralTitle;
  }
}
