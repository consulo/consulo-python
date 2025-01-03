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
import consulo.execution.configuration.RunConfiguration;
import consulo.execution.configuration.RunProfileState;
import consulo.execution.configuration.ui.SettingsEditor;
import consulo.execution.executor.Executor;
import consulo.execution.runner.ExecutionEnvironment;
import consulo.process.ExecutionException;
import consulo.project.Project;
import consulo.project.macro.ProjectPathMacroManager;
import consulo.util.xml.serializer.InvalidDataException;
import consulo.util.xml.serializer.JDOMExternalizerUtil;
import consulo.util.xml.serializer.WriteExternalException;
import org.jdom.Element;

import jakarta.annotation.Nonnull;

/**
 * @author Leonid Shalupov
 */
public class PythonUnitTestRunConfiguration extends
                                            AbstractPythonTestRunConfiguration
                                              implements PythonUnitTestRunConfigurationParams {
  private boolean myIsPureUnittest = true;
  protected String myTitle = "Unittest";
  protected String myPluralTitle = "Unittests";

  private String myParams = "";
  private boolean useParam = false;

  public PythonUnitTestRunConfiguration(Project project,
                                        ConfigurationFactory configurationFactory) {
    super(project, configurationFactory);
  }

  @Override
  protected SettingsEditor<? extends RunConfiguration> createConfigurationEditor() {
    return new PythonUnitTestRunConfigurationEditor(getProject(), this);
  }

  @Override
  public RunProfileState getState(@Nonnull final Executor executor, @Nonnull final ExecutionEnvironment env) throws ExecutionException {
    return new PythonUnitTestCommandLineState(this, env);
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    ProjectPathMacroManager.getInstance(getProject()).expandPaths(element);
    super.readExternal(element);
    myIsPureUnittest = Boolean.parseBoolean(JDOMExternalizerUtil.readField(element, "PUREUNITTEST"));
    myParams = JDOMExternalizerUtil.readField(element, "PARAMS");
    useParam = Boolean.parseBoolean(JDOMExternalizerUtil.readField(element, "USE_PARAM"));
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    super.writeExternal(element);
    JDOMExternalizerUtil.writeField(element, "PUREUNITTEST", String.valueOf(myIsPureUnittest));
    JDOMExternalizerUtil.writeField(element, "PARAMS", myParams);
    JDOMExternalizerUtil.writeField(element, "USE_PARAM", String.valueOf(useParam));
	  ProjectPathMacroManager.getInstance(getProject()).collapsePathsRecursively(element);
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
    return myIsPureUnittest;
  }

  public void setPureUnittest(boolean isPureUnittest) {
    myIsPureUnittest = isPureUnittest;
  }

  public String getParams() {
    return myParams;
  }

  public void setParams(String pattern) {
    myParams = pattern;
  }

  public boolean useParam() {
    return useParam;
  }

  public void useParam(boolean useParam) {
    this.useParam = useParam;
  }
}
