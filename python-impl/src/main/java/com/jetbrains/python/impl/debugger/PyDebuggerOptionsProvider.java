// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.impl.debugger;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.component.persist.PersistentStateComponent;
import consulo.component.persist.State;
import consulo.component.persist.Storage;
import consulo.component.persist.StoragePathMacros;
import consulo.project.Project;
import consulo.util.lang.StringUtil;
import consulo.execution.debug.XDebuggerManager;
import consulo.python.impl.localize.PyLocalize;
import com.jetbrains.python.impl.console.PyConsoleOptions;
import com.jetbrains.python.impl.console.PythonDebugLanguageConsoleView;
import com.jetbrains.python.impl.run.AbstractPythonRunConfiguration;

import consulo.util.collection.ContainerUtil;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@State(
  name = "PyDebuggerOptionsProvider",
  storages = @Storage(StoragePathMacros.WORKSPACE_FILE)
)
@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
@Singleton
public final class PyDebuggerOptionsProvider implements PersistentStateComponent<PyDebuggerOptionsProvider.State> {
    private State myState = new State();
    private final Project myProject;

    @Inject
    public PyDebuggerOptionsProvider(Project project) {
        myProject = project;
    }

    public static PyDebuggerOptionsProvider getInstance(Project project) {
        return project.getInstance(PyDebuggerOptionsProvider.class);
    }

    /**
     * IPython in the Debug Console.
     * <p>
     * Falls back to the Python Console value while the Debug Console keeps none of its own, so a project
     * configured before the two consoles were split behaves as it did. See PY-91913.
     */
    public boolean isDebugConsoleIpythonEnabled() {
        Boolean own = myState.myDebugConsoleIpythonEnabled;
        return own != null ? own : PyConsoleOptions.getInstance(myProject).isIpythonEnabled();
    }

    public void setDebugConsoleIpythonEnabled(boolean enabled) {
        myState.myDebugConsoleIpythonEnabled = enabled;
    }

    /**
     * The command queue in the Debug Console.
     * <p>
     * Falls back to the Python Console value, see {@link #isDebugConsoleIpythonEnabled}.
     */
    public boolean isDebugConsoleCommandQueueEnabled() {
        Boolean own = myState.myDebugConsoleCommandQueueEnabled;
        return own != null ? own : PyConsoleOptions.getInstance(myProject).isCommandQueueEnabled();
    }

    /**
     * Deliberately does not call {@code CommandQueueForPythonConsoleService.disableCommandQueue}, unlike
     * {@link PyConsoleOptions#setCommandQueueEnabled}. That method clears every queue in the project and disables
     * every console panel, so it would switch the Python Console queue off together with this one. Commands already
     * queued for the Debug Console drain as usual, and nothing new is queued once the setting is off.
     */
    public void setDebugConsoleCommandQueueEnabled(boolean enabled) {
        myState.myDebugConsoleCommandQueueEnabled = enabled;
    }

    /** The script the Debug Console runs when it starts. */
    public String getDebugConsoleStartScript() {
        return myState.myDebugConsoleStartScript;
    }

    public void setDebugConsoleStartScript(String script) {
        myState.myDebugConsoleStartScript = script;
    }

    @Override
    public State getState() {
        return myState;
    }

    @Override
    public void loadState(State state) {
        myState = state;
    }

    public static class State {
        public boolean myAttachToSubprocess = true;
        public boolean mySaveCallSignatures = false;
        public boolean mySupportGeventDebugging = false;
        public boolean myDropIntoDebuggerOnFailedTests = false;
        public boolean mySupportQtDebugging = true;
        public String myPyQtBackend = "auto";
        public String myAttachProcessFilter = "python";
        public int myEvaluationResponseTimeout = 60_000;

        /**
         * Stays {@code null} until the user sets it, and {@link #isDebugConsoleIpythonEnabled} then reads the
         * Python Console value instead.
         */
        public Boolean myDebugConsoleIpythonEnabled = null;

        public Boolean myDebugConsoleCommandQueueEnabled = null;

        public String myDebugConsoleStartScript = PythonDebugLanguageConsoleView.DEBUG_CONSOLE_START_COMMAND;
    }


    public boolean isAttachToSubprocess() {
        return myState.myAttachToSubprocess;
    }

    public void setAttachToSubprocess(boolean attachToSubprocess) {
        myState.myAttachToSubprocess = attachToSubprocess;
    }

    public boolean isSaveCallSignatures() {
        return myState.mySaveCallSignatures;
    }

    public void setSaveCallSignatures(boolean saveCallSignatures) {
        myState.mySaveCallSignatures = saveCallSignatures;
    }

    public boolean isSupportGeventDebugging() {
        return myState.mySupportGeventDebugging;
    }

    public void setSupportGeventDebugging(boolean supportGeventDebugging) {
        myState.mySupportGeventDebugging = supportGeventDebugging;
    }

    public boolean isDropIntoDebuggerOnFailedTest() {
        return myState.myDropIntoDebuggerOnFailedTests;
    }

    public void setDropIntoDebuggerOnFailedTest(boolean dropIntoDebuggerOnFailedTest) {
        myState.myDropIntoDebuggerOnFailedTests = dropIntoDebuggerOnFailedTest;
    }

    public boolean isSupportQtDebugging() {
        return myState.mySupportQtDebugging;
    }

    public void setSupportQtDebugging(boolean supportQtDebugging) {
        myState.mySupportQtDebugging = supportQtDebugging;
    }

    public String getPyQtBackend() {
        if (StringUtil.toLowerCase(PyLocalize.pythonDebuggerQtBackendAuto().get()).equals(myState.myPyQtBackend)) {
            return "auto";
        }
        return myState.myPyQtBackend;
    }

    public void setPyQtBackend(String backend) {
        myState.myPyQtBackend = backend;
    }

    public String getAttachProcessFilter() {
        return myState.myAttachProcessFilter;
    }

    public void setAttachProcessFilter(String filter) {
        myState.myAttachProcessFilter = filter;
    }

    public int getEvaluationResponseTimeout() {
        return myState.myEvaluationResponseTimeout;
    }

    public void setEvaluationResponseTimeout(int timeout) {
        myState.myEvaluationResponseTimeout = timeout;
    }

    // DAP debugger backend switching (getSelectedBackend, setSelectedBackend, switchBackendWithRestart) is not ported

    public static boolean hasActivePythonSessions(Project project) {
        return ContainerUtil.exists(
          XDebuggerManager.getInstance(project).getDebugSessions(),
          session -> session.getRunProfile() instanceof AbstractPythonRunConfiguration
        );
    }
}
