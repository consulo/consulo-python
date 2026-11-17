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
package com.jetbrains.python.impl.debugger;

import com.jetbrains.python.console.pydev.ConsoleCommunicationListener;
import com.jetbrains.python.debugger.PyDebugValue;
import com.jetbrains.python.debugger.ValuesPolicy;
import com.jetbrains.python.impl.PythonHelper;
import com.jetbrains.python.impl.actions.PyExecuteInConsole;
import com.jetbrains.python.impl.console.PyConsoleOptions;
import com.jetbrains.python.impl.console.PydevConsoleRunnerFactory;
import com.jetbrains.python.impl.console.PythonConsoleView;
import com.jetbrains.python.impl.console.PythonDebugConsoleCommunication;
import com.jetbrains.python.impl.console.PythonDebugLanguageConsoleView;
import com.jetbrains.python.impl.debugger.settings.PyDebuggerSettings;
import com.jetbrains.python.impl.run.AbstractPythonRunConfiguration;
import com.jetbrains.python.impl.run.CommandLinePatcher;
import com.jetbrains.python.impl.run.DebugAwareConfiguration;
import com.jetbrains.python.impl.run.EnvironmentController;
import com.jetbrains.python.impl.run.PlainEnvironmentController;
import com.jetbrains.python.impl.run.PythonCommandLineState;
import com.jetbrains.python.impl.sdk.PySdkUtil;
import com.jetbrains.python.impl.sdk.PythonEnvUtil;
import com.jetbrains.python.impl.sdk.PythonSdkType;
import com.jetbrains.python.impl.sdk.flavors.PythonSdkFlavor;
import com.jetbrains.python.psi.LanguageLevel;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.Application;
import consulo.application.util.registry.Registry;
import consulo.container.boot.ContainerPathManager;
import consulo.content.base.BinariesOrderRootType;
import consulo.content.bundle.Sdk;
import consulo.document.FileDocumentManager;
import consulo.execution.ExecutionResult;
import consulo.execution.configuration.RunProfile;
import consulo.execution.configuration.RunProfileState;
import consulo.execution.configuration.WrappingRunConfiguration;
import consulo.execution.debug.DefaultDebugExecutor;
import consulo.execution.debug.XDebugProcess;
import consulo.execution.debug.XDebugSession;
import consulo.execution.debug.XDebuggerManager;
import consulo.execution.executor.Executor;
import consulo.execution.runner.ExecutionEnvironment;
import consulo.execution.runner.GenericProgramRunner;
import consulo.execution.ui.ExecutionConsole;
import consulo.execution.ui.RunContentDescriptor;
import consulo.execution.ui.console.ConsoleView;
import consulo.execution.ui.layout.RunnerLayoutUi;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.module.content.ModuleRootManager;
import consulo.platform.ForwardedPort;
import consulo.platform.Platform;
import consulo.process.ExecutionException;
import consulo.process.ProcessHandler;
import consulo.process.cmd.GeneralCommandLine;
import consulo.process.cmd.ParametersList;
import consulo.process.cmd.ParamsGroup;
import consulo.process.event.ProcessEvent;
import consulo.process.event.ProcessListener;
import consulo.project.Project;
import consulo.ui.ex.content.Content;
import consulo.util.dataholder.Key;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Consulo keeps its own {@link GenericProgramRunner} based launch: the session is created from the
 * {@link GeneralCommandLine} patchers of JB's legacy implementation ({@link #createCommandLinePatchers}),
 * JB's Targets API based launch (PythonDebuggerClientModeTargetedCommandLineBuilder) is not ported.
 */
@ExtensionImpl
public class PyDebugRunner extends GenericProgramRunner {
    private static final Logger LOG = Logger.getInstance(PyDebugRunner.class);

    public static final String PY_DEBUG_RUNNER = "PyDebugRunner";

    public static final String CLIENT_PARAM = "--client";
    public static final String PORT_PARAM = "--port";
    public static final String FILE_PARAM = "--file";
    public static final String MODULE_PARAM = "--module";
    public static final String IDE_PROJECT_ROOTS = "IDE_PROJECT_ROOTS";
    public static final String LIBRARY_ROOTS = "LIBRARY_ROOTS";
    public static final String GEVENT_SUPPORT = "GEVENT_SUPPORT";
    public static final String PYDEVD_FILTERS = "PYDEVD_FILTERS";
    public static final String PYDEVD_FILTER_LIBRARIES = "PYDEVD_FILTER_LIBRARIES";
    public static final String PYDEVD_USE_CYTHON = "PYDEVD_USE_CYTHON";
    public static final String PYCHARM_DEBUG = "PYCHARM_DEBUG";
    public static final String USE_LOW_IMPACT_MONITORING = "USE_LOW_IMPACT_MONITORING";
    public static final String HALT_VARIABLE_RESOLVE_THREADS_ON_STEP_RESUME = "HALT_VARIABLE_RESOLVE_THREADS_ON_STEP_RESUME";

    public static final String CYTHON_EXTENSIONS_DIR =
        Path.of(ContainerPathManager.get().getSystemPath(), "cythonExtensions").toString();

    /**
     * A hack for disabling the debugging tracing in the unit-test mode.
     */
    public static final Key<Boolean> FORCE_DISABLE_DEBUGGER_TRACING = Key.create("FORCE_DISABLE_DEBUGGER_TRACING");

    private @Nullable PyDebugProcess myPyDebugProcess;

    @Override
    public String getRunnerId() {
        return PY_DEBUG_RUNNER;
    }

    @Override
    public boolean canRun(String executorId, RunProfile profile) {
        if (!DefaultDebugExecutor.EXECUTOR_ID.equals(executorId)) {
            // If not debug at all
            return false;
        }
        // Any python configuration is debuggable unless it explicitly declares itself as DebugAwareConfiguration and denies it
        // with canRunUnderDebug == false
        if (profile instanceof WrappingRunConfiguration<?> wrappingRunConfiguration) {
            // If the configuration is wrapper -- unwrap it and check
            return isDebuggable(wrappingRunConfiguration.getPeer());
        }
        return isDebuggable(profile);
    }

    @Override
    protected @Nullable RunContentDescriptor doExecute(RunProfileState state, ExecutionEnvironment environment) throws ExecutionException {
        XDebugSession session = createSession(state, environment);
        initSession(session, state, environment.getExecutor());
        return session.getRunContentDescriptor();
    }

    // Consulo-specific
    protected void initSession(XDebugSession session, RunProfileState state, Executor executor) {
    }

    protected XDebugSession createSession(RunProfileState state, ExecutionEnvironment environment) throws ExecutionException {
        FileDocumentManager.getInstance().saveAllDocuments();

        PythonCommandLineState pyState = (PythonCommandLineState)state;

        Sdk sdk = pyState.getSdk();
        PyDebugSessionFactory sessionCreator = PyDebugSessionFactory.findExtension(sdk);
        if (sessionCreator != null) {
            return sessionCreator.createSession(pyState, environment);
        }

        ServerSocket serverSocket = PythonCommandLineState.createServerSocket();
        int serverLocalPort = serverSocket.getLocalPort();

        // Consulo-specific: a process of another machine connects to the port of the IDE published on that machine
        Platform platform = sdk == null ? Platform.current() : sdk.getPlatform();
        if (!platform.equals(Platform.current())) {
            // the debuggers of the subprocesses would connect to ports of the IDE which are not published there
            pyState.setMultiprocessDebug(false);
        }
        ForwardedPort publishedPort;
        try {
            publishedPort = platform.net().publish(serverLocalPort);
        }
        catch (IOException e) {
            closeServerSocket(serverSocket, e);
            throw new ExecutionException("Cannot publish the port of the debugger on " + platform.getName(), e);
        }

        RunProfile profile = environment.getRunProfile();
        ExecutionResult result;
        try {
            result = pyState.execute(environment.getExecutor(),
                                     createCommandLinePatchers(environment.getProject(), pyState, profile, publishedPort.port()));
        }
        catch (ExecutionException | RuntimeException e) {
            publishedPort.close();
            closeServerSocket(serverSocket, e);
            throw e;
        }
        // Consulo-specific: the published port lives as long as the debugged process
        result.getProcessHandler().addProcessListener(new ProcessListener() {
            @Override
            public void processTerminated(ProcessEvent event) {
                publishedPort.close();
            }
        });

        return XDebuggerManager.getInstance(environment.getProject()).startSession(environment, session -> {
            PyDebugProcess process = createDebugProcess(session, serverSocket, result, pyState);
            // Consulo-specific: the paths of the debugged process name the files of its machine
            if (!platform.equals(Platform.current())) {
                process.setPositionConverter(new PyLocalPositionConverter(platform));
            }
            myPyDebugProcess = process;
            createConsoleCommunication(environment.getProject(), result, process, session);
            return process;
        });
    }

    protected PyDebugProcess createDebugProcess(XDebugSession session,
                                                ServerSocket serverSocket,
                                                ExecutionResult result,
                                                PythonCommandLineState pyState) {
        return new PyDebugProcess(session, serverSocket, result.getExecutionConsole(), result.getProcessHandler(),
                                  pyState.isMultiprocessDebug());
    }

    public static <T extends XDebugProcess & PyDebugProcessWithConsole> @Nullable PythonDebugConsoleCommunication<T> createConsoleCommunication(
        Project project,
        ExecutionResult result,
        T debugProcess,
        XDebugSession session
    ) {
        ExecutionConsole console = result.getExecutionConsole();
        if (console instanceof PythonDebugLanguageConsoleView debugLanguageConsoleView) {
            ProcessHandler processHandler = result.getProcessHandler();
            return initDebugConsole(project, debugProcess, debugLanguageConsoleView, processHandler, session);
        }
        return null;
    }

    public static <T extends XDebugProcess & PyDebugProcessWithConsole> PythonDebugConsoleCommunication<T> initDebugConsole(
        Project project,
        T debugProcess,
        PythonDebugLanguageConsoleView console,
        ProcessHandler processHandler,
        XDebugSession session
    ) {
        PythonConsoleView pythonConsoleView = console.getPydevConsoleView();
        PythonDebugConsoleCommunication<T> debugConsoleCommunication =
            new PythonDebugConsoleCommunication<>(project, debugProcess, pythonConsoleView);

        pythonConsoleView.setConsoleCommunication(debugConsoleCommunication);

        PydevDebugConsoleExecuteActionHandler consoleExecuteActionHandler =
            new PydevDebugConsoleExecuteActionHandler(console, processHandler, debugConsoleCommunication);

        PythonDebugConsoleCommunication<T> pythonDebugConsoleCommunication =
            initDebugConsole(pythonConsoleView, consoleExecuteActionHandler, debugProcess, processHandler, debugConsoleCommunication, session);

        // Readiness is unconditional: the Debug Console has to work whichever console is on screen. It can only be
        // marked ready here, because it needs the execute action handler that was attached just above.
        // "Always show Debug Console" then picks the visible one, and nothing else.
        console.initDebugConsole();
        if (console.isEnabled() && PyConsoleOptions.getInstance(project).isShowDebugConsoleByDefault()) {
            console.enableConsole(false);
        }

        return pythonDebugConsoleCommunication;
    }

    protected static <T extends XDebugProcess & PyDebugProcessWithConsole> PythonDebugConsoleCommunication<T> initDebugConsole(
        PythonConsoleView pythonConsoleView,
        PydevDebugConsoleExecuteActionHandler consoleExecuteActionHandler,
        T debugProcess,
        ProcessHandler processHandler,
        PythonDebugConsoleCommunication<T> debugConsoleCommunication,
        XDebugSession session
    ) {
        pythonConsoleView.setExecutionHandler(consoleExecuteActionHandler);

        debugProcess.getSession().addSessionListener(consoleExecuteActionHandler);
        // TODO consulo: LanguageConsoleBuilder cannot wrap an existing console, JB registers the execute action of the console with
        //  new LanguageConsoleBuilder(pythonConsoleView).processHandler(processHandler).initActions(consoleExecuteActionHandler, "py", true)

        debugConsoleCommunication.addCommunicationListener(new ConsoleCommunicationListener() {
            @Override
            public void commandExecuted(boolean more) {
                session.rebuildViews();
            }

            @Override
            public void inputRequested() {
                Application.get().invokeLater(() -> {
                    ConsoleView debugConsoleView = session.getConsoleView();
                    if (debugConsoleView instanceof PythonDebugLanguageConsoleView pythonDebugLanguageConsoleView) {
                        RunnerLayoutUi sessionUi = session.getUI();
                        if (sessionUi != null) {
                            // In debug mode, selectConsoleTab only uses "Console" tab name, descriptor not needed
                            Content consoleContent = sessionUi.getContentManager().findContent("Console");
                            if (consoleContent != null) {
                                sessionUi.getContentManager().setSelectedContent(consoleContent);
                            }
                        }

                        if (pythonConsoleView.isVisible()) {
                            PyExecuteInConsole.requestFocus(true, null, pythonConsoleView, true);
                        }
                        else {
                            ConsoleView primaryConsoleView = pythonDebugLanguageConsoleView.getPrimaryConsoleView();
                            // TODO consulo: ConsoleViewImpl is internal, JB focuses the editor of the primary ConsoleViewImpl:
                            //  requestFocus(false, primaryConsoleView.editor, null, true)
                        }
                    }
                });
            }
        });

        return debugConsoleCommunication;
    }

    public static void configureDebugEnvironment(Project project, Map<String, String> environment, @Nullable RunProfile runProfile) {
        configureDebugEnvironment(project, new PlainEnvironmentController(environment), runProfile, true);
    }

    private static void configureDebugEnvironment(Project project,
                                                  EnvironmentController environmentController,
                                                  @Nullable RunProfile runProfile,
                                                  boolean addCythonExtensionsToPythonPath) {
        if (PyDebuggerOptionsProvider.getInstance(project).isSupportGeventDebugging()) {
            environmentController.putFixedValue(GEVENT_SUPPORT, "True");
        }

        PyDebuggerSettings debuggerSettings = PyDebuggerSettings.getInstance();
        if (debuggerSettings.isSteppingFiltersEnabled()) {
            environmentController.putFixedValue(PYDEVD_FILTERS, debuggerSettings.getSteppingFiltersForProject(project));
        }
        if (debuggerSettings.isLibrariesFilterEnabled()) {
            environmentController.putFixedValue(PYDEVD_FILTER_LIBRARIES, "True");
        }
        if (debuggerSettings.getValuesPolicy() != ValuesPolicy.SYNC) {
            environmentController.putFixedValue(PyDebugValue.POLICY_ENV_VARS.get(debuggerSettings.getValuesPolicy()), "True");
        }

        PydevConsoleRunnerFactory.putDebugConsoleIPythonEnvFlag(project, environmentController);

        if (addCythonExtensionsToPythonPath) {
            environmentController.appendTargetPathToPathsValue(PythonEnvUtil.PYTHONPATH, CYTHON_EXTENSIONS_DIR);
        }

        PyDebugAsyncioCustomizer.getInstance().enableAsyncioMode(environmentController);

        AbstractPythonRunConfiguration<?> runConfiguration =
            runProfile instanceof AbstractPythonRunConfiguration<?> configuration ? configuration : null;
        Module module = runConfiguration != null ? runConfiguration.getModule() : null;

        if (module != null) {
            addProjectRootsToEnv(module, environmentController);
        }

        if (runConfiguration != null) {
            // Consulo: the interpreter of a run configuration is its SDK
            Sdk interpreter = runConfiguration.getSdk();
            if (interpreter != null) {
                LanguageLevel langLevel = PythonSdkType.getLanguageLevelForSdk(interpreter);
                // PY-28457 Disable Cython extensions in Python 3.4 and Python 3.5 because of a crash in generated C code
                if (langLevel == LanguageLevel.PYTHON34 || langLevel == LanguageLevel.PYTHON35) {
                    environmentController.putFixedValue(PYDEVD_USE_CYTHON, "NO");
                }
            }

            addSdkRootsToEnv(environmentController, runConfiguration);
            environmentController.appendTargetPathToPathsValue(PythonEnvUtil.PYTHONPATH, runConfiguration.getWorkingDirectorySafe());
        }

        applyRegistryFlags(environmentController);
    }

    private static boolean isForceDisableDebuggerTracing() {
        return Boolean.TRUE.equals(Application.get().getUserData(FORCE_DISABLE_DEBUGGER_TRACING));
    }

    /**
     * The part of the legacy implementation based on {@link GeneralCommandLine}.
     */
    public static void disableBuiltinBreakpoint(@Nullable Sdk sdk, Map<String, String> env) {
        if (sdk != null && languageLevelOf(sdk) == LanguageLevel.PYTHON37) {
            env.put("PYTHONBREAKPOINT", "0");
        }
    }

    private static void addProjectRootsToEnv(Module module, EnvironmentController environment) {
        ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
        List<String> contentRoots = new ArrayList<>();
        for (VirtualFile contentRoot : moduleRootManager.getContentRoots()) {
            // Consulo-specific: the path of the root on the machine of the debugged process
            contentRoots.add(PySdkUtil.toTargetPath(contentRoot));
        }
        for (VirtualFile contentRoot : getDependenciesContentRoots(module)) {
            contentRoots.add(PySdkUtil.toTargetPath(contentRoot));
        }
        environment.putTargetPathsValue(IDE_PROJECT_ROOTS, contentRoots);
    }

    private static List<VirtualFile> getDependenciesContentRoots(Module module) {
        ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
        List<VirtualFile> roots = new ArrayList<>();
        for (Module dependency : moduleRootManager.getDependencies()) {
            roots.addAll(List.of(ModuleRootManager.getInstance(dependency).getContentRoots()));
        }
        return roots;
    }

    private static void addSdkRootsToEnv(EnvironmentController environmentController, AbstractPythonRunConfiguration<?> runConfiguration) {
        Sdk interpreter = runConfiguration.getSdk();
        if (interpreter != null) {
            // Consulo: the class roots of the interpreter are the binaries roots of its SDK, named by their paths on its machine
            List<String> roots = new ArrayList<>();
            for (VirtualFile root : interpreter.getRootProvider().getFiles(BinariesOrderRootType.ID)) {
                roots.add(PySdkUtil.toTargetPath(root));
            }
            // Assume that libraries are located on the target machine
            environmentController.putFixedValue(LIBRARY_ROOTS, StringUtil.join(roots, File.pathSeparator));
        }
    }

    private static void applyRegistryFlags(EnvironmentController environmentController) {
        if (Registry.is("python.debug.low.impact.monitoring.api", true)) {
            environmentController.putFixedValue(USE_LOW_IMPACT_MONITORING, "True");
        }

        if (!Registry.is("python.debug.enable.cython.speedups", true)) {
            environmentController.putFixedValue(PYDEVD_USE_CYTHON, "NO");
        }

        if (Registry.is("python.debug.enable.diagnostic.prints", false)) {
            environmentController.putFixedValue(PYCHARM_DEBUG, "True");
        }

        if (Registry.is("python.debug.halt.variable.resolve.threads.on.step.resume", true)) {
            environmentController.putFixedValue(HALT_VARIABLE_RESOLVE_THREADS_ON_STEP_RESUME, "True");
        }
    }

    /**
     * The flag that enables multiprocess debugging in pydevd. {@code --multiproc} selects the older dispatcher
     * protocol, which {@link PyDebugProcess#createMultiprocessDebugger} still implements on the IDE side behind
     * the same registry key; the two must agree on which protocol pydevd speaks.
     */
    private static String getMultiprocessDebugParameter() {
        return Registry.is("python.debugger.use.dispatcher", false) ? "--multiproc" : "--multiprocess";
    }

    private static void closeServerSocket(ServerSocket serverSocket, Throwable cause) {
        try {
            serverSocket.close();
        }
        catch (IOException e) {
            cause.addSuppressed(e);
        }
    }

    private static boolean isDebuggable(RunProfile profile) {
        if (profile instanceof DebugAwareConfiguration debugAwareConfiguration) {
            // if configuration knows whether debug is allowed
            return debugAwareConfiguration.canRunUnderDebug();
        }
        // Any python configuration is debuggable
        return profile instanceof AbstractPythonRunConfiguration<?>;
    }

    public @Nullable PyDebugProcess getProcess() {
        return myPyDebugProcess;
    }

    public void resetProcess() {
        myPyDebugProcess = null;
    }

    /**
     * The part of the legacy implementation based on {@link GeneralCommandLine}.
     */
    public CommandLinePatcher[] createCommandLinePatchers(Project project,
                                                          PythonCommandLineState state,
                                                          RunProfile profile,
                                                          int serverLocalPort) {
        CommandLinePatcher debugServerPatcher = commandLine -> {
            // script name is the last parameter; all other params are for python interpreter; insert just before the name
            ParametersList parametersList = commandLine.getParametersList();

            ParamsGroup debugParams = parametersList.getParamsGroup(PythonCommandLineState.GROUP_DEBUGGER);
            assert debugParams != null;

            // was patchExeParams(parametersList): strip '-m' from the module params group, remembering whether it was a module run
            // Consulo: PythonCommandLineState has no module group, the '-m' of a module run is in the exe options group
            int moduleParamsIndex =
                parametersList.getParamsGroups().indexOf(parametersList.getParamsGroup(PythonCommandLineState.GROUP_EXE_OPTIONS));
            ParamsGroup oldModuleParams = parametersList.removeParamsGroup(moduleParamsIndex);
            boolean isModule = false;
            if (oldModuleParams != null) {
                ParamsGroup newModuleParams = new ParamsGroup(PythonCommandLineState.GROUP_EXE_OPTIONS);
                for (String param : oldModuleParams.getParameters()) {
                    if (!param.equals("-m")) {
                        newModuleParams.addParameter(param);
                    }
                    else {
                        isModule = true;
                    }
                }
                parametersList.addParamsGroupAt(moduleParamsIndex, newModuleParams);
            }

            PythonHelper.DEBUGGER.addToGroup(debugParams, commandLine);

            if (isModule) {
                // add a module flag only after command line parameters
                debugParams.addParameter(MODULE_PARAM);
            }

            if (state.isMultiprocessDebug()) {
                debugParams.addParameter(getMultiprocessDebugParameter());
            }

            // was configureCommonDebugParameters(project, debugParams)
            if (Application.get().isUnitTestMode() && !isForceDisableDebuggerTracing()) {
                debugParams.addParameter("--DEBUG");
            }
            if (PyDebuggerOptionsProvider.getInstance(project).isSaveCallSignatures()) {
                debugParams.addParameter("--save-signatures");
            }
            if (PyDebuggerOptionsProvider.getInstance(project).isSupportQtDebugging()) {
                String pyQtBackend = StringUtil.toLowerCase(PyDebuggerOptionsProvider.getInstance(project).getPyQtBackend());
                debugParams.addParameter("--qt-support=" + pyQtBackend);
            }

            configureDebugEnvironment(project, commandLine.getEnvironment(), profile);

            // was configureDebugConnectionParameters(debugParams, serverLocalPort)
            for (String arg : new String[]{CLIENT_PARAM, "127.0.0.1", PORT_PARAM, String.valueOf(serverLocalPort), FILE_PARAM}) {
                debugParams.addParameter(arg);
            }

            ParamsGroup exeParams = parametersList.getParamsGroup(PythonCommandLineState.GROUP_EXE_OPTIONS);

            PythonSdkFlavor flavor = state.getSdkFlavor();
            if (flavor != null) {
                assert exeParams != null;
                for (String option : flavor.getExtraDebugOptions()) {
                    exeParams.addParameter(option);
                }
            }

            // Consulo-specific: the frozen modules of the standard library have no files - the debugger cannot place their frames
            Sdk sdk = state.getSdk();
            if (sdk != null && PythonSdkType.getLanguageLevelForSdk(sdk).isAtLeast(LanguageLevel.PYTHON311)) {
                assert exeParams != null;
                exeParams.addParameter("-Xfrozen_modules=off");
            }
        };

        // was createRunConfigPatcher(state, profile): state is always a PythonCommandLineState here, so only the profile matters
        List<CommandLinePatcher> patchers = new ArrayList<>();
        patchers.add(debugServerPatcher);
        if (profile instanceof AbstractPythonRunConfiguration<?> runConfigPatcher) {
            patchers.add(runConfigPatcher);
        }
        return patchers.toArray(new CommandLinePatcher[0]);
    }

    private static LanguageLevel languageLevelOf(Sdk sdk) {
        String version = sdk.getVersionString();
        LanguageLevel level = version != null ? LanguageLevel.getLanguageLevelFromVersionStringStaticSafe(version) : null;
        return level != null ? level : LanguageLevel.getDefault();
    }
}
