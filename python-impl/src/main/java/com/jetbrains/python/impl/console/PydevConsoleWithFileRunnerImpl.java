// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.console;

import com.jetbrains.python.impl.run.PythonRunConfiguration;
import consulo.content.bundle.Sdk;
import consulo.module.Module;
import consulo.project.Project;
import org.jspecify.annotations.Nullable;

import java.util.Map;

class PydevConsoleWithFileRunnerImpl extends PydevConsoleRunnerImpl {
    private final PythonRunConfiguration myConfig;

    PydevConsoleWithFileRunnerImpl(
        Project project,
        @Nullable Sdk sdk,
        PyConsoleType consoleType,
        String title,
        @Nullable String workingDir,
        Map<String, String> environmentVariables,
        PyConsoleOptions.PyConsoleSettings settingsProvider,
        PythonRunConfiguration config,
        String... statementsToExecute
    ) {
        super(project, sdk, consoleType, title, workingDir, environmentVariables, settingsProvider, statementsToExecute);
        myConfig = config;
    }

    // TODO consulo: the Target API constructor (Function<TargetEnvironment, String> workingDir, startScriptFun) is not ported

    @Override
    protected PythonConsoleRunParams createConsoleRunParams(
        @Nullable String workingDir,
        Sdk sdk,
        Map<String, String> environmentVariables
    ) {
        return new PythonConsoleWithFileRunParams(myConsoleSettings, workingDir, sdk, environmentVariables);
    }

    @Override
    protected final @Nullable Module getModule() {
        return myConfig.getModule();
    }

    public class PythonConsoleWithFileRunParams extends PythonConsoleRunParams {

        public PythonConsoleWithFileRunParams(
            PyConsoleOptions.PyConsoleSettings consoleSettings,
            @Nullable String workingDir,
            Sdk sdk,
            Map<String, String> envs
        ) {
            super(consoleSettings, workingDir, sdk, envs);
        }

        @Override
        public boolean isUseModuleSdk() {
            return myConfig.isUseModuleSdk();
        }

        @Override
        public String getModuleName() {
            return myConfig.getModuleName();
        }

        @Override
        public String getInterpreterOptions() {
            return myConfig.getInterpreterOptions();
        }

        @Override
        public boolean shouldAddContentRoots() {
            return myConfig.shouldAddContentRoots();
        }

        @Override
        public boolean shouldAddSourceRoots() {
            return myConfig.shouldAddSourceRoots();
        }
    }
}
