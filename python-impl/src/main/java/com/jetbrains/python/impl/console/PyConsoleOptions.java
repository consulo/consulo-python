/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.python.impl.console;

import com.google.common.collect.Maps;
import com.jetbrains.python.impl.run.AbstractPyCommonOptionsForm;
import com.jetbrains.python.run.AbstractPythonRunConfigurationParams;
import com.jetbrains.python.run.PythonRunParams;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.component.persist.PersistentStateComponent;
import consulo.component.persist.State;
import consulo.component.persist.Storage;
import consulo.component.persist.StoragePathMacros;
import consulo.ide.ServiceManager;
import consulo.ide.impl.idea.util.PathMappingSettings;
import consulo.module.Module;
import consulo.module.ModuleManager;
import consulo.project.Project;
import consulo.util.lang.ComparatorUtil;
import consulo.util.xml.serializer.annotation.*;
import jakarta.inject.Singleton;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Map;

/**
 * @author traff
 */
@State(
  name = "PyConsoleOptionsProvider",
  storages = {
    @Storage(file = StoragePathMacros.WORKSPACE_FILE)
  })
@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
@Singleton
public class PyConsoleOptions implements PersistentStateComponent<PyConsoleOptions.State> {
  private State myState = new State();

  public PyConsoleSettings getPythonConsoleSettings() {
    return myState.myPythonConsoleState;
  }

  public boolean isShowDebugConsoleByDefault() {
    return myState.myShowDebugConsoleByDefault;
  }

  public void setShowDebugConsoleByDefault(boolean showDebugConsoleByDefault) {
    myState.myShowDebugConsoleByDefault = showDebugConsoleByDefault;
  }

  public boolean isIpythonEnabled() {
    return myState.myIpythonEnabled;
  }

  public void setIpythonEnabled(boolean enabled) {
    myState.myIpythonEnabled = enabled;
  }

  public static PyConsoleOptions getInstance(Project project) {
    return ServiceManager.getService(project, PyConsoleOptions.class);
  }

  @Override
  public State getState() {
    return myState;
  }

  @Override
  public void loadState(State state) {
    myState.myShowDebugConsoleByDefault = state.myShowDebugConsoleByDefault;
    myState.myPythonConsoleState = state.myPythonConsoleState;
    myState.myIpythonEnabled = state.myIpythonEnabled;
  }

  public static class State {
    public PyConsoleSettings myPythonConsoleState = new PyConsoleSettings();

    public boolean myShowDebugConsoleByDefault = false;
    public boolean myIpythonEnabled = true;
  }

  @Tag("console-settings")
  public static class PyConsoleSettings implements PythonRunParams {
    public String myCustomStartScript = PydevConsoleRunnerImpl.CONSOLE_START_COMMAND;
    public String mySdkHome = null;
    public String myInterpreterOptions = "";
    public boolean myUseModuleSdk;
    public String myModuleName = null;
    public Map<String, String> myEnvs = Maps.newHashMap();
    public boolean myPassParentEnvs = true;
    public String myWorkingDirectory = "";
    public boolean myAddContentRoots = true;
    public boolean myAddSourceRoots;
    @Nonnull
    private PathMappingSettings myMappings = new PathMappingSettings();

    public PyConsoleSettings() {
    }

    public PyConsoleSettings(String myCustomStartScript) {
      this.myCustomStartScript = myCustomStartScript;
    }

    public void apply(AbstractPythonRunConfigurationParams form) {
      mySdkHome = form.getSdkHome();
      myInterpreterOptions = form.getInterpreterOptions();
      myEnvs = form.getEnvs();
      myPassParentEnvs = form.isPassParentEnvs();
      myUseModuleSdk = form.isUseModuleSdk();
      myModuleName = form.getModule() == null ? null : form.getModule().getName();
      myWorkingDirectory = form.getWorkingDirectory();

      myAddContentRoots = form.shouldAddContentRoots();
      myAddSourceRoots = form.shouldAddSourceRoots();
      myMappings = form.getMappingSettings() == null ? new PathMappingSettings() : form.getMappingSettings();
    }

    public boolean isModified(AbstractPyCommonOptionsForm form) {
      return !ComparatorUtil.equalsNullable(mySdkHome, form.getSdkHome()) ||
        !myInterpreterOptions.equals(form.getInterpreterOptions()) ||
        !myEnvs.equals(form.getEnvs()) ||
        myPassParentEnvs != form.isPassParentEnvs() ||
        myUseModuleSdk != form.isUseModuleSdk() ||
        myAddContentRoots != form.shouldAddContentRoots() ||
        myAddSourceRoots != form.shouldAddSourceRoots() || !ComparatorUtil.equalsNullable(myModuleName,
                                                                                          form.getModule() == null ? null : form.getModule()
                                                                                                                                .getName()) ||
        !myWorkingDirectory.equals(form.getWorkingDirectory()) || !myMappings.equals(form.getMappingSettings());
    }

    public void reset(Project project, AbstractPythonRunConfigurationParams form) {
      form.setEnvs(myEnvs);
      form.setPassParentEnvs(myPassParentEnvs);
      form.setInterpreterOptions(myInterpreterOptions);
      form.setSdkHome(mySdkHome);
      form.setUseModuleSdk(myUseModuleSdk);
      form.setAddContentRoots(myAddContentRoots);
      form.setAddSourceRoots(myAddSourceRoots);

      boolean moduleWasAutoselected = false;
      if (form.isUseModuleSdk() != myUseModuleSdk) {
        myUseModuleSdk = form.isUseModuleSdk();
        moduleWasAutoselected = true;
      }

      if (myModuleName != null) {
        form.setModule(ModuleManager.getInstance(project).findModuleByName(myModuleName));
      }

      if (moduleWasAutoselected && form.getModule() != null) {
        myModuleName = form.getModule().getName();
      }

      form.setWorkingDirectory(myWorkingDirectory);

      form.setMappingSettings(myMappings);
    }

    @Attribute("custom-start-script")
    public String getCustomStartScript() {
      return myCustomStartScript;
    }

    @Attribute("sdk-home")
    public String getSdkHome() {
      return mySdkHome;
    }

    @Attribute("module-name")
    public String getModuleName() {
      return myModuleName;
    }

    @Attribute("working-directory")
    public String getWorkingDirectory() {
      return myWorkingDirectory;
    }

    @Attribute("is-module-sdk")
    public boolean isUseModuleSdk() {
      return myUseModuleSdk;
    }

    @Tag("envs")
    @Property(surroundWithTag = false)
    @MapAnnotation(surroundWithTag = false, surroundKeyWithTag = false, keyAttributeName = "key",
      entryTagName = "env", valueAttributeName = "value", surroundValueWithTag = false)
    public Map<String, String> getEnvs() {
      return myEnvs;
    }

    @Attribute("add-content-roots")
    public boolean shouldAddContentRoots() {
      return myAddContentRoots;
    }

    @Attribute("add-source-roots")
    public boolean shouldAddSourceRoots() {
      return myAddSourceRoots;
    }

    @Attribute("interpreter-options")
    public String getInterpreterOptions() {
      return myInterpreterOptions;
    }

    @AbstractCollection(surroundWithTag = false)
    public PathMappingSettings getMappings() {
      return myMappings;
    }

    public void setCustomStartScript(String customStartScript) {
      myCustomStartScript = customStartScript;
    }

    public void setSdkHome(String sdkHome) {
      mySdkHome = sdkHome;
    }

    @Override
    public void setModule(Module module) {
      setModuleName(module.getName());
    }

    public void setInterpreterOptions(String interpreterOptions) {
      myInterpreterOptions = interpreterOptions;
    }

    public void setUseModuleSdk(boolean useModuleSdk) {
      myUseModuleSdk = useModuleSdk;
    }

    @Override
    @Attribute("is-pass-parent-envs")
    public boolean isPassParentEnvs() {
      return myPassParentEnvs;
    }

    @Override
    public void setPassParentEnvs(boolean passParentEnvs) {
      myPassParentEnvs = passParentEnvs;
    }

    public void setModuleName(String moduleName) {
      myModuleName = moduleName;
    }

    public void setEnvs(Map<String, String> envs) {
      myEnvs = envs;
    }

    @Nullable
    @Override
    public PathMappingSettings getMappingSettings() {
      return getMappings();
    }

    @Override
    public void setMappingSettings(@Nullable PathMappingSettings mappingSettings) {

    }

    public void setWorkingDirectory(String workingDirectory) {
      myWorkingDirectory = workingDirectory;
    }

    public void setAddContentRoots(boolean addContentRoots) {
      myAddContentRoots = addContentRoots;
    }

    public void setAddSourceRoots(boolean addSourceRoots) {
      myAddSourceRoots = addSourceRoots;
    }

    public void setMappings(@Nullable PathMappingSettings mappings) {
      myMappings = mappings != null ? mappings : new PathMappingSettings();
    }
  }
}

