// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

import com.jetbrains.python.impl.debugger.PyDebuggerOptionsProvider;
import com.jetbrains.python.impl.run.EnvironmentController;
import com.jetbrains.python.impl.run.PlainEnvironmentController;
import com.jetbrains.python.impl.run.PythonCommandLineState;
import com.jetbrains.python.impl.run.PythonRunConfiguration;
import com.jetbrains.python.impl.sdk.PythonEnvUtil;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.Application;
import consulo.application.ReadAction;
import consulo.application.util.concurrent.AppExecutorUtil;
import consulo.content.bundle.Sdk;
import consulo.language.util.ModuleUtilCore;
import consulo.module.Module;
import consulo.module.content.ModuleRootManager;
import consulo.module.content.ProjectRootManager;
import consulo.project.Project;
import consulo.python.buildout.module.extension.BuildoutModuleExtension;
import consulo.util.lang.Pair;
import consulo.util.lang.SystemProperties;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

@ExtensionImpl(id = "pydev")
public class PydevConsoleRunnerFactory extends PyConsoleRunnerFactoryAsync {
    protected abstract static class ConsoleParameters {
        private final Project project;
        private final @Nullable Sdk sdk;
        private final @Nullable String workingDir;
        private final Map<String, String> envs;
        private final PyConsoleType consoleType;
        private final PyConsoleOptions.PyConsoleSettings settingsProvider;
        private @Nullable Module module;

        protected ConsoleParameters(
            Project project,
            @Nullable Sdk sdk,
            @Nullable String workingDir,
            Map<String, String> envs,
            PyConsoleType consoleType,
            PyConsoleOptions.PyConsoleSettings settingsProvider
        ) {
            this.project = project;
            this.sdk = sdk;
            this.workingDir = workingDir;
            this.envs = envs;
            this.consoleType = consoleType;
            this.settingsProvider = settingsProvider;
        }

        public Project getProject() {
            return project;
        }

        public @Nullable Sdk getSdk() {
            return sdk;
        }

        public @Nullable String getWorkingDir() {
            return workingDir;
        }

        public Map<String, String> getEnvs() {
            return envs;
        }

        public PyConsoleType getConsoleType() {
            return consoleType;
        }

        public PyConsoleOptions.PyConsoleSettings getSettingsProvider() {
            return settingsProvider;
        }

        public @Nullable Module getModule() {
            return module;
        }

        public void setModule(@Nullable Module module) {
            this.module = module;
        }
    }

    protected static class ConstantConsoleParameters extends ConsoleParameters {
        private final String[] setupFragment;

        public ConstantConsoleParameters(
            Project project,
            @Nullable Sdk sdk,
            @Nullable String workingDir,
            Map<String, String> envs,
            PyConsoleType consoleType,
            PyConsoleOptions.PyConsoleSettings settingsProvider,
            String[] setupFragment
        ) {
            super(project, sdk, workingDir, envs, consoleType, settingsProvider);
            this.setupFragment = setupFragment;
        }

        public String[] getSetupFragment() {
            return setupFragment;
        }
    }

    // TODO consulo: TargetedConsoleParameters (Target API) is not ported, ConstantConsoleParameters is always used

    /**
     * Consulo-specific: JB builds TargetedConsoleParameters from a PythonInterpreter. Consulo resolves the Sdk, and
     * builds ConstantConsoleParameters with the plain setup script (plus the buildout path statement of the module).
     */
    protected CompletableFuture<ConsoleParameters> createConsoleParametersAsync(Project project, @Nullable Module contextModule) {
        // Both read the module's content and source roots, and this runs on a background thread rather than on the
        // EDT, which held read access on its own.
        return CompletableFuture.supplyAsync(
            () -> ReadAction.compute(() -> createConsoleParameters(project, contextModule)),
            AppExecutorUtil.getAppExecutorService()
        );
    }

    private ConsoleParameters createConsoleParameters(Project project, @Nullable Module contextModule) {
        Pair<Sdk, Module> sdkAndModule = PydevConsoleRunnerUtil.findPythonSdkAndModule(project, contextModule);
        Sdk sdk = sdkAndModule.first;
        Module module = sdkAndModule.second;
        PyConsoleOptions.PyConsoleSettings settingsProvider = PyConsoleOptions.getInstance(project).getPythonConsoleSettings();
        Map<String, String> envs = new HashMap<>(settingsProvider.getEnvs());
        putIPythonEnvFlag(project, envs);

        String workingDir = getWorkingDir(project, module, settingsProvider);
        String[] setupFragment = createSetupFragment(module, workingDir, settingsProvider);

        ConstantConsoleParameters parameters =
            new ConstantConsoleParameters(project, sdk, workingDir, envs, PyConsoleType.PYTHON, settingsProvider, setupFragment);
        parameters.setModule(module);
        return parameters;
    }

    @Override
    public CompletableFuture<PydevConsoleRunner> createConsoleRunnerAsync(Project project, @Nullable Module contextModule) {
        Module module = Application.get().getExtensionPoint(PyConsoleCustomizer.class).getExtensionList().stream()
            .map(it -> it.guessConsoleModule(project, contextModule))
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);
        return createConsoleParametersAsync(project, module != null ? module : contextModule).thenApply(consoleParameters -> {
            ConstantConsoleParameters parameters = (ConstantConsoleParameters) consoleParameters;
            return new PydevConsoleRunnerImpl(
                project,
                parameters.getSdk(),
                parameters.getConsoleType(),
                parameters.getWorkingDir(),
                parameters.getEnvs(),
                parameters.getSettingsProvider(),
                parameters.getSetupFragment()
            );
        });
    }

    @Override
    public CompletableFuture<PydevConsoleRunner> createConsoleRunnerWithFileAsync(Project project, PythonRunConfiguration config) {
        return createConsoleParametersAsync(project, config.getModule()).thenApply(consoleParameters -> {
            ConstantConsoleParameters parameters = (ConstantConsoleParameters) consoleParameters;
            Sdk sdk = config.getSdk() != null ? config.getSdk() : parameters.getSdk();
            Map<String, String> consoleEnvs = new HashMap<>();
            consoleEnvs.putAll(parameters.getEnvs());
            consoleEnvs.putAll(config.getEnvs());
            return new PydevConsoleWithFileRunnerImpl(
                project,
                sdk,
                parameters.getConsoleType(),
                config.getName(),
                config.getWorkingDirectory() != null ? config.getWorkingDirectory() : parameters.getWorkingDir(),
                consoleEnvs,
                parameters.getSettingsProvider(),
                config,
                parameters.getSetupFragment()
            );
        });
    }

    public static void putIPythonEnvFlag(Project project, Map<String, String> envs) {
        putIPythonEnvFlag(project, new PlainEnvironmentController(envs));
    }

    public static void putIPythonEnvFlag(Project project, EnvironmentController environmentController) {
        putIPythonEnvFlag(environmentController, PyConsoleOptions.getInstance(project).isIpythonEnabled());
    }

    /**
     * The Debug Console reads the same variable but keeps its own setting, so the debugger passes the value
     * of {@link PyDebuggerOptionsProvider#isDebugConsoleIpythonEnabled} instead.
     */
    public static void putDebugConsoleIPythonEnvFlag(Project project, EnvironmentController environmentController) {
        putIPythonEnvFlag(environmentController, PyDebuggerOptionsProvider.getInstance(project).isDebugConsoleIpythonEnabled());
    }

    private static void putIPythonEnvFlag(EnvironmentController environmentController, boolean enabled) {
        environmentController.putFixedValue(PythonEnvUtil.IPYTHONENABLE, enabled ? "True" : "False");
    }

    public static String getWorkingDir(Project project, @Nullable Module module, PyConsoleOptions.PyConsoleSettings settingsProvider) {
        return getWorkingDirFromSettings(project, module, settingsProvider);
    }

    private static String getWorkingDirFromSettings(
        Project project,
        @Nullable Module module,
        PyConsoleOptions.PyConsoleSettings settingsProvider
    ) {
        String workingDirectoryInSettings = settingsProvider.getWorkingDirectory();
        if (workingDirectoryInSettings != null && !workingDirectoryInSettings.isEmpty()) {
            return workingDirectoryInSettings;
        }
        if (module != null && ModuleRootManager.getInstance(module).getContentRoots().length > 0) {
            return ModuleRootManager.getInstance(module).getContentRoots()[0].getPath();
        }
        VirtualFile[] projectRoots = ProjectRootManager.getInstance(project).getContentRoots();
        for (VirtualFile root : projectRoots) {
            if (root.isInLocalFileSystem()) {
                // we can't start Python Console in remote folder without additional connection configurations
                return root.getPath();
            }
        }
        return SystemProperties.getUserHome();
    }

    private static String makeStartWithEmptyLine(String line) {
        if (line.startsWith("\n") || line.isBlank()) {
            return line;
        }
        return "\n" + line;
    }

    /**
     * Consulo-specific: the String counterpart of JB {@code createSetupScriptFunction}, with the buildout path
     * statement kept from the previous Consulo factory.
     */
    private static String[] createSetupFragment(
        @Nullable Module module,
        @Nullable String workingDir,
        PyConsoleOptions.PyConsoleSettings settingsProvider
    ) {
        String customStartScript = makeStartWithEmptyLine(settingsProvider.getCustomStartScript());
        Collection<String> pythonPath = PythonCommandLineState.collectPythonPath(
            module,
            settingsProvider.shouldAddContentRoots(),
            settingsProvider.shouldAddSourceRoots()
        );
        String selfPathAppend = PydevConsoleRunnerUtil.constructPyPathAndWorkingDirCommand(pythonPath, workingDir, customStartScript);

        BuildoutModuleExtension facet = null;
        if (module != null) {
            facet = ModuleUtilCore.getExtension(module, BuildoutModuleExtension.class);
        }

        if (facet != null) {
            List<String> path = facet.getAdditionalPythonPath();
            String prependStatement = facet.getPathPrependStatement(path);
            return new String[]{prependStatement, selfPathAppend};
        }
        return new String[]{selfPathAppend};
    }
}
