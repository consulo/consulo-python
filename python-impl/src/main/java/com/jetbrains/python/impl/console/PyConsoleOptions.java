// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
/*
 * Copyright 2013-2026 consulo.io
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

import com.jetbrains.python.impl.console.actions.CommandQueueForPythonConsoleService;
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
import consulo.module.Module;
import consulo.module.ModuleManager;
import consulo.project.Project;
import consulo.util.lang.Comparing;
import consulo.util.xml.serializer.annotation.Attribute;
import consulo.util.xml.serializer.annotation.MapAnnotation;
import consulo.util.xml.serializer.annotation.Property;
import consulo.util.xml.serializer.annotation.Tag;
import consulo.util.xml.serializer.annotation.Transient;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@State(
    name = "PyConsoleOptionsProvider",
    storages = @Storage(file = StoragePathMacros.WORKSPACE_FILE)
)
@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
@Singleton
public class PyConsoleOptions implements PersistentStateComponent<PyConsoleOptions.State> {
    private final Project myProject;
    private final State myState = new State();

    @Inject
    public PyConsoleOptions(Project project) {
        myProject = project;
    }

    public PyConsoleSettings getPythonConsoleSettings() {
        return myState.myPythonConsoleState;
    }

    public boolean isShowDebugConsoleByDefault() {
        return myState.myShowDebugConsoleByDefault;
    }

    public void setShowDebugConsoleByDefault(boolean value) {
        myState.myShowDebugConsoleByDefault = value;
    }

    public boolean isShowVariableByDefault() {
        return myState.myShowVariablesByDefault;
    }

    public void setShowVariablesByDefault(boolean value) {
        myState.myShowVariablesByDefault = value;
    }

    public boolean isIpythonEnabled() {
        return myState.myIpythonEnabled;
    }

    public void setIpythonEnabled(boolean value) {
        myState.myIpythonEnabled = value;
    }

    public boolean isUseExistingConsole() {
        return myState.myUseExistingConsole;
    }

    public void setUseExistingConsole(boolean value) {
        myState.myUseExistingConsole = value;
    }

    public boolean isCommandQueueEnabled() {
        return myState.myCommandQueueEnabled;
    }

    public void setCommandQueueEnabled(boolean value) {
        myState.myCommandQueueEnabled = value;
        if (!value) {
            CommandQueueForPythonConsoleService.getInstance(myProject).disableCommandQueue();
        }
    }

    public PyConsoleOptionsConfigurable.CodeCompletionOption getCodeCompletionOption() {
        return myState.myCodeCompletionOption;
    }

    public void setCodeCompletionOption(PyConsoleOptionsConfigurable.CodeCompletionOption value) {
        myState.myCodeCompletionOption = value;
    }

    public boolean isRuntimeCodeCompletion() {
        return myState.myCodeCompletionOption == PyConsoleOptionsConfigurable.CodeCompletionOption.RUNTIME;
    }

    @Override
    public State getState() {
        return myState;
    }

    @Override
    public void loadState(State state) {
        myState.myShowDebugConsoleByDefault = state.myShowDebugConsoleByDefault;
        myState.myShowVariablesByDefault = state.myShowVariablesByDefault;
        myState.myPythonConsoleState = state.myPythonConsoleState;
        myState.myIpythonEnabled = state.myIpythonEnabled;
        myState.myUseExistingConsole = state.myUseExistingConsole;
        myState.myCommandQueueEnabled = state.myCommandQueueEnabled;
        myState.myCodeCompletionOption = state.myCodeCompletionOption;
    }

    public static PyConsoleOptions getInstance(Project project) {
        return project.getInstance(PyConsoleOptions.class);
    }

    public static class State {
        public PyConsoleSettings myPythonConsoleState = new PyConsoleSettings();

        public boolean myShowDebugConsoleByDefault = true;

        public boolean myShowVariablesByDefault = true;

        public boolean myIpythonEnabled = true;

        public boolean myUseExistingConsole = false;

        public boolean myCommandQueueEnabled = false;

        public PyConsoleOptionsConfigurable.CodeCompletionOption myCodeCompletionOption =
            PyConsoleOptionsConfigurable.CodeCompletionOption.STATIC;
    }

    @Tag("console-settings")
    public static class PyConsoleSettings implements PythonRunParams {
        public String myCustomStartScript = PydevConsoleRunnerImpl.CONSOLE_START_COMMAND;

        /**
         * Consulo: JB keeps a transient {@code PythonInterpreter} here (a console runs with the interpreter of its Python project).
         * Consulo keeps its own SDK design: the console settings store the home of the selected SDK.
         */
        public @Nullable String mySdkHome = null;

        public String myInterpreterOptions = "";

        public boolean myUseModuleSdk = false;

        public @Nullable String myModuleName = null;

        public Map<String, String> myEnvs = new HashMap<>();

        public List<String> myEnvFiles = List.of();

        public boolean myPassParentEnvs = true;

        public String myWorkingDirectory = "";

        public boolean myAddContentRoots = true;

        public boolean myAddSourceRoots = true;

        // TODO consulo: PathMappingSettings missing (JB myMappings)
        private boolean myUseSoftWraps = false;
        private boolean myDebugJustMyCode = false;

        public PyConsoleSettings() {
        }

        public PyConsoleSettings(String myCustomStartScript) {
            this.myCustomStartScript = myCustomStartScript;
        }

        public void apply(AbstractPythonRunConfigurationParams form) {
            mySdkHome = form.getSdkHome();
            myInterpreterOptions = form.getInterpreterOptions();
            myEnvs = form.getEnvs();
            // TODO consulo: AbstractPythonRunConfigurationParams.getEnvFilePaths() missing
            myPassParentEnvs = form.isPassParentEnvs();
            myUseModuleSdk = form.isUseModuleSdk();
            Module module = form.getModule();
            myModuleName = module != null ? module.getName() : null;
            myWorkingDirectory = form.getWorkingDirectory();

            myAddContentRoots = form.shouldAddContentRoots();
            myAddSourceRoots = form.shouldAddSourceRoots();
        }

        public boolean isModified(AbstractPyCommonOptionsForm form) {
            Module module = form.getModule();
            return !Comparing.equal(mySdkHome, form.getSdkHome()) ||
                !myInterpreterOptions.equals(form.getInterpreterOptions()) ||
                !myEnvs.equals(form.getEnvs()) ||
                myPassParentEnvs != form.isPassParentEnvs() ||
                myUseModuleSdk != form.isUseModuleSdk() ||
                myAddContentRoots != form.shouldAddContentRoots() ||
                myAddSourceRoots != form.shouldAddSourceRoots() ||
                !Comparing.equal(myModuleName, module != null ? module.getName() : null) ||
                !myWorkingDirectory.equals(form.getWorkingDirectory());
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

            Module module = form.getModule();
            if (moduleWasAutoselected && module != null) {
                myModuleName = module.getName();
            }

            form.setWorkingDirectory(myWorkingDirectory);
        }

        @SuppressWarnings("unused")
        @Attribute("custom-start-script")
        public String getCustomStartScript() {
            return myCustomStartScript;
        }

        @Attribute("sdk-home")
        @Override
        public @Nullable String getSdkHome() {
            return mySdkHome;
        }

        @Attribute("module-name")
        @Override
        public @Nullable String getModuleName() {
            return myModuleName;
        }

        @Attribute("working-directory")
        @Override
        public String getWorkingDirectory() {
            return myWorkingDirectory;
        }

        @Attribute("is-module-sdk")
        @Override
        public boolean isUseModuleSdk() {
            return myUseModuleSdk;
        }

        @Tag("envs")
        @Property(surroundWithTag = false)
        @MapAnnotation(surroundWithTag = false, surroundKeyWithTag = false, keyAttributeName = "key",
            entryTagName = "env", valueAttributeName = "value", surroundValueWithTag = false)
        @Override
        public Map<String, String> getEnvs() {
            return myEnvs;
        }

        @Attribute("add-content-roots")
        @Override
        public boolean shouldAddContentRoots() {
            return myAddContentRoots;
        }

        @Attribute("add-source-roots")
        @Override
        public boolean shouldAddSourceRoots() {
            return myAddSourceRoots;
        }

        @Attribute("interpreter-options")
        @Override
        public String getInterpreterOptions() {
            return myInterpreterOptions;
        }

        @SuppressWarnings("unused")
        public void setCustomStartScript(String customStartScript) {
            myCustomStartScript = customStartScript;
        }

        @Override
        public void setSdkHome(@Nullable String sdkHome) {
            mySdkHome = sdkHome;
        }

        @Override
        public void setModule(Module module) {
            setModuleName(module.getName());
        }

        @Override
        public void setInterpreterOptions(String interpreterOptions) {
            myInterpreterOptions = interpreterOptions;
        }

        @Override
        public void setUseModuleSdk(boolean useModuleSdk) {
            myUseModuleSdk = useModuleSdk;
        }

        @Attribute("is-pass-parent-envs")
        @Override
        public boolean isPassParentEnvs() {
            return myPassParentEnvs;
        }

        @Override
        public void setPassParentEnvs(boolean passParentEnvs) {
            myPassParentEnvs = passParentEnvs;
        }

        public void setModuleName(@Nullable String moduleName) {
            myModuleName = moduleName;
        }

        @Override
        public void setEnvs(Map<String, String> envs) {
            myEnvs = envs;
        }

        @Override
        public void setWorkingDirectory(String workingDirectory) {
            myWorkingDirectory = workingDirectory;
        }

        @Override
        public void setAddContentRoots(boolean addContentRoots) {
            myAddContentRoots = addContentRoots;
        }

        @Override
        public void setAddSourceRoots(boolean addSourceRoots) {
            myAddSourceRoots = addSourceRoots;
        }

        public boolean shouldDebugJustMyCode() {
            return myDebugJustMyCode;
        }

        public void setDebugJustMyCode(boolean debugJustMyCode) {
            myDebugJustMyCode = debugJustMyCode;
        }

        public boolean isUseSoftWraps() {
            return myUseSoftWraps;
        }

        public void setUseSoftWraps(boolean value) {
            myUseSoftWraps = value;
        }

        @Transient
        public List<String> getEnvFilePaths() {
            return myEnvFiles;
        }

        public void setEnvFilePaths(List<String> value) {
            myEnvFiles = value;
        }
    }
}
