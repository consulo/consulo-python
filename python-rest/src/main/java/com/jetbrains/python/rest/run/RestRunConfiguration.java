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

package com.jetbrains.python.rest.run;

import com.jetbrains.python.impl.run.AbstractPythonRunConfiguration;
import consulo.execution.configuration.ConfigurationFactory;
import consulo.execution.configuration.LegacyRunConfigurationConverter;
import consulo.execution.configuration.RunConfigurationOptions;
import consulo.project.Project;
import consulo.util.lang.StringUtil;

/**
 * User : catherine
 */
public abstract class RestRunConfiguration extends AbstractPythonRunConfiguration {
  public RestRunConfiguration(Project project,
                              ConfigurationFactory factory) {
    super(project, factory);
  }

  @Override
  protected Class<? extends RunConfigurationOptions> getOptionsClass() {
    return RestRunConfigurationOptions.class;
  }

  @Override
  public RestRunConfigurationOptions getOptions() {
    return (RestRunConfigurationOptions) super.getOptions();
  }

  @Override
  protected void convertLegacyState(LegacyRunConfigurationConverter converter) {
    super.convertLegacyState(converter);

    RestRunConfigurationOptions options = new RestRunConfigurationOptions();
    convertLegacyCommonState(converter, options);

    RestRunConfigurationLegacyState old = converter.readXml(RestRunConfigurationLegacyState.class);
    options.setInputFile(old.docutils_input_file);
    options.setOutputFile(old.docutils_output_file);
    options.setParams(old.docutils_params);
    options.setTask(old.docutils_task);
    options.setOpenInBrowser(old.docutils_open_in_browser);
    converter.setOptions(options);
  }

  public String getInputFile() {
    return StringUtil.notNullize(getOptions().getInputFile());
  }

  public void setInputFile(String inputFile) {
    getOptions().setInputFile(inputFile);
  }

  public String getOutputFile() {
    return StringUtil.notNullize(getOptions().getOutputFile());
  }

  public void setOutputFile(String file) {
    getOptions().setOutputFile(file);
  }

  public void setParams(String params) {
    getOptions().setParams(params);
  }

  public String getParams() {
    return StringUtil.notNullize(getOptions().getParams());
  }

  public String getTask() {
    return StringUtil.notNullize(getOptions().getTask());
  }

  public void setTask(Object task) {
    getOptions().setTask(task != null ? task.toString() : "rst2html");
  }

  public void setOpenInBrowser(boolean open) {
    getOptions().setOpenInBrowser(open);
  }

  public boolean openInBrowser() {
    return getOptions().isOpenInBrowser();
  }

  @Override
  public boolean canRunWithCoverage() {
    return false;
  }
}
