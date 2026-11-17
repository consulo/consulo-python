// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

import com.jetbrains.python.console.pydev.ConsoleCommunicationListener;
import com.jetbrains.python.debugger.PyDebugValue;
import com.jetbrains.python.debugger.ValuesPolicy;
import com.jetbrains.python.impl.PythonHelper;
import com.jetbrains.python.impl.console.actions.ConsoleCopyOutputAction;
import com.jetbrains.python.impl.console.actions.ScrollToTheEndAction;
import com.jetbrains.python.impl.console.actions.ShowCommandQueueAction;
import com.jetbrains.python.impl.console.actions.ShowVarsAction;
import com.jetbrains.python.impl.debugger.PyDebugRunner;
import com.jetbrains.python.impl.debugger.PyLocalPositionConverter;
import com.jetbrains.python.impl.debugger.PyVariableViewSettings;
import com.jetbrains.python.impl.debugger.settings.PyDebuggerSettings;
import com.jetbrains.python.impl.run.PythonCommandLineState;
import com.jetbrains.python.impl.run.PythonProcessHandler;
import com.jetbrains.python.impl.run.PythonTracebackFilter;
import com.jetbrains.python.impl.sdk.PythonEnvUtil;
import com.jetbrains.python.run.PythonRunParams;
import consulo.annotation.access.RequiredWriteAction;
import consulo.application.Application;
import consulo.application.dumb.DumbAware;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.ProgressManager;
import consulo.application.progress.Task;
import consulo.application.util.registry.Registry;
import consulo.codeEditor.Caret;
import consulo.codeEditor.Editor;
import consulo.codeEditor.action.EditorAction;
import consulo.codeEditor.action.EditorWriteActionHandler;
import consulo.content.bundle.Sdk;
import consulo.dataContext.DataContext;
import consulo.disposer.Disposer;
import consulo.document.FileDocumentManager;
import consulo.execution.debug.XDebugProcess;
import consulo.execution.debug.XDebugProcessStarter;
import consulo.execution.debug.XDebugSession;
import consulo.execution.debug.XDebuggerManager;
import consulo.execution.debug.icon.ExecutionDebugIconGroup;
import consulo.execution.process.ProcessTerminatedListener;
import consulo.execution.ui.RunContentDescriptor;
import consulo.execution.ui.console.ConsoleExecuteAction;
import consulo.execution.ui.console.ConsoleHistoryController;
import consulo.execution.ui.console.language.LanguageConsoleView;
import consulo.execution.util.ConsoleTitleGen;
import consulo.ide.impl.idea.execution.console.ConsoleHistoryControllerImpl;
import consulo.language.file.light.LightVirtualFile;
import consulo.language.psi.PsiFile;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.platform.ForwardedPort;
import consulo.platform.Platform;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.process.ExecutionException;
import consulo.process.KillableProcessHandler;
import consulo.process.NativeProcessHandler;
import consulo.process.ProcessHandler;
import consulo.process.ProcessOutputTypes;
import consulo.process.cmd.GeneralCommandLine;
import consulo.process.cmd.ParamsGroup;
import consulo.process.event.ProcessEvent;
import consulo.process.event.ProcessListener;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.python.psi.icon.PythonPsiIconGroup;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.JBColor;
import consulo.ui.ex.MessageCategory;
import consulo.ui.ex.action.ActionManager;
import consulo.ui.ex.action.ActionToolbar;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.AnActionWithSyncUpdate;
import consulo.ui.ex.action.DefaultActionGroup;
import consulo.ui.ex.action.DumbAwareAction;
import consulo.ui.ex.action.IdeActions;
import consulo.ui.ex.action.AnSeparator;
import consulo.ui.ex.action.ToggleAction;
import consulo.ui.ex.action.util.ActionUtil;
import consulo.ui.ex.awt.JBEmptyBorder;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.awt.SideBorder;
import consulo.ui.ex.awt.UIUtil;
import consulo.ui.ex.content.Content;
import consulo.ui.ex.errorTreeView.NewErrorTreeViewPanel;
import consulo.ui.ex.errorTreeView.NewErrorTreeViewPanelFactory;
import consulo.ui.ex.toolWindow.ToolWindow;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.Lists;
import consulo.util.io.NetUtil;
import consulo.util.io.StreamUtil;
import consulo.util.lang.StringUtil;
import consulo.util.lang.TimeoutUtil;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import static com.jetbrains.python.impl.console.PyConsoleUtil.ASYNCIO_REPL_ENV;
import static com.jetbrains.python.impl.sdk.PythonEnvUtil.PYTHONIOENCODING;
import static consulo.execution.ui.console.language.AbstractConsoleRunnerWithHistory.registerActionShortcuts;

/**
 * @author traff, oleg
 */
public class PydevConsoleRunnerImpl implements PydevConsoleRunner {
    /**
     * The address that IDE uses to listen for incoming connections from Python
     * Console script started in the "client mode".
     */
    private static final String LOCALHOST = "localhost";

    public static final String WORKING_DIR_AND_PYTHON_PATHS = "WORKING_DIR_AND_PYTHON_PATHS";
    public static final String PROJECT_ROOT = "PROJECT_ROOT";
    public static final String CONSOLE_START_COMMAND = "import sys; print('Python %s on %s' % (sys.version, sys.platform))\n" +
        "sys.path.extend([" + WORKING_DIR_AND_PYTHON_PATHS + "])\n";
    public static final String STARTED_BY_RUNNER = "startedByRunner";
    public static final String INLINE_OUTPUT_SUPPORTED = "INLINE_OUTPUT_SUPPORTED";
    private static final String ASYNCIO_REPL_COMMAND = "-m asyncio";

    private static final Long WAIT_BEFORE_FORCED_CLOSE_MILLIS = 2000L;
    private static final Logger LOG = Logger.getInstance(PydevConsoleRunnerImpl.class);
    public static final int PORTS_WAITING_TIMEOUT = 20000;
    /**
     * Everything in console is UTF-8 only, just like regular script execution
     */
    public static final Charset CONSOLE_CHARSET = StandardCharsets.UTF_8;
    private final Project myProject;
    private final String myTitle;
    private final @Nullable String myWorkingDir;
    private @Nullable Sdk mySdk;
    private PydevConsoleCommunication myPydevConsoleCommunication;
    private ProcessHandler myProcessHandler;
    protected PythonConsoleExecuteActionHandler myConsoleExecuteActionHandler;
    private final List<ConsoleListener> myConsoleListeners = Lists.newLockFreeCopyOnWriteList();
    private final PyConsoleType myConsoleType;
    private final Map<String, String> myEnvironmentVariables;
    protected final PyConsoleOptions.PyConsoleSettings myConsoleSettings;
    private final String @Nullable [] myStatementsToExecute;

    /*
     Console title used during initialization, it can be changed with Rename action
     */
    private @Nullable String myConsoleInitTitle = null;
    private PythonConsoleView myConsoleView;

    /**
     * Consulo-specific: the ports leading between the IDE and the machine of a remote interpreter - closed with the console's process.
     */
    private final List<ForwardedPort> myForwardedPorts = new CopyOnWriteArrayList<>();

    // TODO consulo: TargetEnvironment missing - the JB constructor taking `Function<TargetEnvironment, String>` working dir
    // and statements is not ported

    public PydevConsoleRunnerImpl(
        Project project,
        @Nullable Sdk sdk,
        PyConsoleType consoleType,
        String title,
        @Nullable String workingDir,
        Map<String, String> environmentVariables,
        PyConsoleOptions.PyConsoleSettings settingsProvider,
        String... statementsToExecute
    ) {
        myProject = project;
        mySdk = sdk;
        myTitle = title;
        myWorkingDir = workingDir;
        myConsoleType = consoleType;
        myEnvironmentVariables = environmentVariables;
        myConsoleSettings = settingsProvider;
        myStatementsToExecute = statementsToExecute;
    }

    public PydevConsoleRunnerImpl(
        Project project,
        @Nullable Sdk sdk,
        PyConsoleType consoleType,
        @Nullable String workingDir,
        Map<String, String> environmentVariables,
        PyConsoleOptions.PyConsoleSettings settingsProvider,
        String... statementsToExecute
    ) {
        this(project, sdk, consoleType, consoleType.getTitle(), workingDir, environmentVariables, settingsProvider, statementsToExecute);
    }

    public void setConsoleTitle(@Nullable String consoleTitle) {
        myConsoleInitTitle = consoleTitle;
    }

    private List<AnAction> fillRunActionsToolbar(DefaultActionGroup toolbarActions) {
        List<AnAction> actions = new ArrayList<>();
        // Rerun
        actions.add(createRerunAction());
        // Stop
        actions.add(createStopAction());
        // Execute
        // TODO consulo: ConsoleExecuteAction constructor with icon (AllIcons.Debugger.ExecuteCurrentStatement) missing
        actions.add(
            new ConsoleExecuteAction(myConsoleView, myConsoleExecuteActionHandler, myConsoleExecuteActionHandler.getEmptyExecuteAction(),
                myConsoleExecuteActionHandler
            ));
        toolbarActions.addAll(actions);
        // Attach Debugger
        toolbarActions.add(new ConnectDebuggerAction());
        // Show Queue
        toolbarActions.add(new ShowCommandQueueAction(myConsoleView));
        // Scroll to the end
        toolbarActions.add(new ScrollToTheEndAction(myConsoleView.getEditor()));
        // Consulo-specific: new console action (kept from the previous Consulo runner)
        toolbarActions.add(new NewConsoleAction());
        // Separator
        toolbarActions.add(AnSeparator.create());
        // Settings
        DefaultActionGroup settings = DefaultActionGroup.createPopupGroup(() -> PyLocalize.pydevConsoleRunnerSettings().get());
        settings.getTemplatePresentation().setIcon(PlatformIconGroup.generalGearplain());
        settings.add(new PyVariableViewSettings.SimplifiedView(null));
        settings.add(new PyVariableViewSettings.VariablesPolicyGroup());
        settings.add(new PyVariableViewSettings.QuotingPolicyGroup());
        toolbarActions.add(settings);
        // Output actions
        DefaultActionGroup outputActions = DefaultActionGroup.createPopupGroup(() -> PyLocalize.pydevConsoleRunnerOutputActions().get());
        outputActions.getTemplatePresentation().setIcon(PlatformIconGroup.generalInspectionseye());
        outputActions.add(new ShowVarsAction(myConsoleView, myPydevConsoleCommunication)); // Show Variables
        outputActions.add(new SoftWrapAction()); // Use soft wraps
        outputActions.add(AnSeparator.create());
        outputActions.add(ConsoleHistoryController.getController(myConsoleView).getBrowseHistory()); // Console History
        outputActions.add(new ConsoleCopyOutputAction(myConsoleView)); // Copy Console Output
        toolbarActions.add(outputActions);
        // Actions without icons
        actions.add(PyConsoleUtil.createInterruptAction(myConsoleView));
        actions.add(PyConsoleUtil.createTabCompletionAction(myConsoleView));
        actions.add(createSplitLineAction());

        return actions;
    }

    @Override
    @RequiredUIAccess
    public void open() {
        PythonConsoleToolWindow toolWindow = PythonConsoleToolWindow.getInstance(myProject);
        if (toolWindow != null && toolWindow.isInitialized()) {
            toolWindow.getToolWindow().activate(() -> {
            }, true);
        }
        else {
            runSync(true);
        }
    }

    @Override
    public void runSync(boolean requestEditorFocus) {
        ProgressManager.getInstance().run(new Task.Backgroundable(myProject, PyLocalize.connectingToConsoleTitle(), false) {
            @Override
            public void run(ProgressIndicator indicator) {
                try {
                    Sdk sdk = mySdk;
                    if (sdk == null) {
                        throw new ExecutionException(PyLocalize.pydevConsolePythonInterpreterIsNotSelected().get());
                    }
                    initAndRun(sdk);
                    indicator.setText(PyLocalize.connectingToConsoleProgress());
                    connect(getStatementsToExecute());
                    if (requestEditorFocus) {
                        myConsoleView.requestFocus();
                    }
                }
                catch (ExecutionException e) {
                    LOG.warn("Error running console", e);
                    Application.get().invokeLater(() -> showErrorsInConsole(e));
                }
            }
        });
    }

    private String[] getStatementsToExecute() {
        if (myStatementsToExecute == null) {
            return ArrayUtil.EMPTY_STRING_ARRAY;
        }
        else {
            return myStatementsToExecute;
        }
    }

    @Override
    public void run(boolean requestEditorFocus) {
        // TODO consulo: TransactionGuard.submitTransaction(PythonPluginDisposable) missing - documents are saved directly
        Application.get().invokeLater(() -> FileDocumentManager.getInstance().saveAllDocuments());

        Application.get().executeOnPooledThread(
            () -> ProgressManager.getInstance().run(new Task.Backgroundable(myProject, PyLocalize.connectingToConsoleTitle(), false) {
                @Override
                public void run(ProgressIndicator indicator) {
                    indicator.setText(PyLocalize.connectingToConsoleProgress());
                    try {
                        Sdk sdk = mySdk;
                        if (sdk == null) {
                            throw new ExecutionException(PyLocalize.pydevConsolePythonInterpreterIsNotSelected().get());
                        }
                        initAndRun(sdk);
                        connect(getStatementsToExecute());
                        if (requestEditorFocus) {
                            myConsoleView.requestFocus();
                        }
                    }
                    catch (Exception e) {
                        LOG.warn("Error running console", e);
                        UIUtil.invokeAndWaitIfNeeded((Runnable) () -> showErrorsInConsole(e));
                    }
                }
            })
        );
    }

    private void showErrorsInConsole(Exception e) {
        fireConsoleInitializationErrorEvent(e);
        DefaultActionGroup actionGroup = new DefaultActionGroup(createRerunAction());

        ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar("PydevConsoleRunnerErrors", actionGroup, false);

        // Runner creating
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(actionToolbar.getComponent(), BorderLayout.WEST);

        NewErrorTreeViewPanelFactory panelFactory = myProject.getApplication().getInstance(NewErrorTreeViewPanelFactory.class);
        NewErrorTreeViewPanel errorViewPanel = panelFactory.createPanel(myProject, null, false, false, null);
        Disposer.register(myProject, errorViewPanel);

        String[] messages = StringUtil.isNotEmpty(e.getMessage()) ? StringUtil.splitByLines(e.getMessage()) : ArrayUtil.EMPTY_STRING_ARRAY;
        if (messages.length == 0) {
            messages = new String[]{PyLocalize.pydevConsoleRunnerUnknownError().get()};
        }

        errorViewPanel.addMessage(MessageCategory.ERROR, messages, null, -1, -1, null);
        panel.add(errorViewPanel.getComponent(), BorderLayout.CENTER);

        //noinspection DialogTitleCapitalization
        RunContentDescriptor contentDescriptor = new RunContentDescriptor(
            null, myProcessHandler, panel, PyLocalize.pydevConsoleRunnerErrorRunningConsole().get());

        showContentDescriptor(contentDescriptor);
    }

    protected void showContentDescriptor(RunContentDescriptor contentDescriptor) {
        ToolWindow toolwindow = PythonConsoleToolWindow.getToolWindow(myProject);
        toolwindow.getComponent().putClientProperty(STARTED_BY_RUNNER, "true");
        PythonConsoleToolWindow.getInstance(myProject).init(toolwindow, contentDescriptor);
    }

    protected PythonConsoleRunParams createConsoleRunParams(
        @Nullable String workingDir,
        Sdk sdk,
        Map<String, String> environmentVariables
    ) {
        return new PythonConsoleRunParams(myConsoleSettings, workingDir, sdk, environmentVariables);
    }

    /**
     * Consulo-specific: JB creates the console command line with the Targets API
     * ({@code PydevConsoleCli.createPythonConsoleScriptInClientMode()} and {@code PythonCommandLineState.initEnvironment()}).
     * Consulo keeps {@link GeneralCommandLine}: the command line runs the console helper in the client mode on the machine of the
     * interpreter (see {@link PythonCommandLineState#createPythonCommandLine}).
     *
     * @param ideServerHost the host where the IDE being Python Console frontend listens for the connection, as seen from the machine
     *                      of the interpreter
     * @param ideServerPort the port where the IDE being Python Console frontend listens for the connection, as seen from the machine
     *                      of the interpreter
     */
    private GeneralCommandLine createPythonConsoleCommandLine(String ideServerHost, int ideServerPort, PythonConsoleRunParams runParams) {
        GeneralCommandLine cmd = PythonCommandLineState.createPythonCommandLine(myProject, runParams, false);
        if (myWorkingDir != null) {
            cmd.withWorkDirectory(myWorkingDir);
        }

        ParamsGroup group = cmd.getParametersList().getParamsGroup(PythonCommandLineState.GROUP_SCRIPT);
        PythonHelper.CONSOLE.addToGroup(group, cmd);
        PydevConsoleCli.addClientModeOptions(group, ideServerHost, ideServerPort);
        return cmd;
    }

    private PythonConsoleView createConsoleView(Sdk sdk) {
        PythonConsoleView consoleView = new PythonConsoleView(myProject, myTitle, sdk, false);
        myPydevConsoleCommunication.setConsoleFile(consoleView.getVirtualFile());
        consoleView.addMessageFilter(new PythonTracebackFilter(myProject));
        // TODO consulo: PythonImportErrorFilter missing (not ported from JB python/src/com/jetbrains/python/run yet)
        return consoleView;
    }

    /**
     * This method performs several actions at once:
     * <ol>
     *   <li>Creates the server on IDE side and makes it start listening for the
     *   incoming connections.</li>
     *   <li>Starts the Python Console backend process on the machine of the
     *   interpreter in the client mode.</li>
     * </ol>
     * <p>
     * Consulo: JB prepares the target environment of the SDK here ({@code createProcessUsingTargetsAPI}); Consulo runs the
     * {@link GeneralCommandLine} on the machine of the SDK and publishes the IDE server port there (see
     * {@link #ideServerPortOnMachine}).
     */
    private ConsoleProcessCreationResult createProcess(Sdk sdk) throws ExecutionException {
        int ideServerPort;
        try {
            ideServerPort = NetUtil.findAvailableSocketPort();
        }
        catch (IOException e) {
            throw new ExecutionException(e.getMessage(), e);
        }

        PythonConsoleRunParams runParams = createConsoleRunParams(myWorkingDir, sdk, myEnvironmentVariables);

        ForwardedPort ideServerPortOnMachine = ideServerPortOnMachine(sdk, ideServerPort);
        try {
            GeneralCommandLine commandLine = createPythonConsoleCommandLine(
                ideServerPortOnMachine != null ? ideServerPortOnMachine.host() : LOCALHOST,
                ideServerPortOnMachine != null ? ideServerPortOnMachine.port() : ideServerPort,
                runParams
            );
            commandLine.withCharset(CONSOLE_CHARSET);
            commandLine.withEnvironment(PYTHONIOENCODING, CONSOLE_CHARSET.name());
            // TODO consulo: PythonSdkFlavor.getExtraDebugOptions() missing (JB adds them to the interpreter options)

            PydevConsoleCommunicationServer communicationServer = new PydevConsoleCommunicationServer(myProject, LOCALHOST, ideServerPort);
            myPydevConsoleCommunication = communicationServer;
            try {
                communicationServer.serve();
            }
            catch (RuntimeException e) {
                communicationServer.close();
                throw e;
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                communicationServer.close();
                throw new ExecutionException(e.getMessage(), e);
            }

            ProcessHandler processHandler = PythonProcessHandler.createProcessHandler(commandLine);
            // Consulo-specific: the forwarded ports live as long as the console's process
            processHandler.addProcessListener(new ProcessListener() {
                @Override
                public void processTerminated(ProcessEvent event) {
                    closeForwardedPorts();
                }
            });
            if (processHandler instanceof NativeProcessHandler nativeProcessHandler) {
                communicationServer.setPythonConsoleProcess(nativeProcessHandler.getProcess());
            }
            else {
                // Consulo-specific: a process on another machine has no java.lang.Process, its handler tells whether it is alive
                communicationServer.setPythonConsoleProcess(new ProcessHandlerProcess(processHandler));
            }
            String commandLineString = commandLine.getCommandLineString();
            return new GeneralCommandLineProcessHandlerFactory(processHandler, commandLineString, communicationServer);
        }
        catch (ExecutionException | RuntimeException e) {
            closeForwardedPorts();
            throw e;
        }
    }

    /**
     * Consulo-specific: the port the console process connects to the IDE on. On another machine it is a port published there,
     * leading to the IDE's port; on the IDE's machine it is the IDE's port itself ({@code null} is returned).
     */
    private @Nullable ForwardedPort ideServerPortOnMachine(Sdk sdk, int ideServerPort) throws ExecutionException {
        Platform platform = sdk.getPlatform();
        if (platform.equals(Platform.current())) {
            return null;
        }
        try {
            ForwardedPort published = platform.net().publish(ideServerPort);
            myForwardedPorts.add(published);
            return published;
        }
        catch (IOException e) {
            throw new ExecutionException("Cannot publish the console port " + ideServerPort + " on " + platform + ": " + e.getMessage(), e);
        }
    }

    /**
     * Consulo-specific: closes the ports leading between the IDE and the machine of a remote interpreter.
     */
    private void closeForwardedPorts() {
        for (ForwardedPort port : myForwardedPorts) {
            port.close();
        }
        myForwardedPorts.clear();
    }

    /**
     * @return if console is module-specific then provide it.
     */
    protected @Nullable Module getModule() {
        return null;
    }

    public static int getRemotePortFromProcess(Process process) throws ExecutionException {
        Scanner s = new Scanner(process.getInputStream());
        return readInt(s, process);
    }

    private static int readInt(Scanner s, Process process) throws ExecutionException {
        long started = System.currentTimeMillis();

        StringBuilder sb = new StringBuilder();
        boolean flag = false;

        while (System.currentTimeMillis() - started < PORTS_WAITING_TIMEOUT) {
            if (s.hasNextLine()) {
                String line = s.nextLine();
                sb.append(line).append("\n");
                try {
                    int i = Integer.parseInt(line);
                    if (flag) {
                        LOG.warn("Unexpected strings in output:\n" + sb);
                    }
                    return i;
                }
                catch (NumberFormatException ignored) {
                    flag = true;
                    continue;
                }
            }

            TimeoutUtil.sleep(200);

            if (process.exitValue() != 0) {
                String error;
                try {
                    error = PyLocalize.pydevConsoleConsoleProcessTerminatedWithError(
                        StreamUtil.readText(process.getErrorStream(), StandardCharsets.UTF_8),
                        sb.toString()
                    ).get();
                }
                catch (Exception ignored) {
                    error = PyLocalize.pydevConsoleConsoleProcessTerminatedWithExitCode(process.exitValue(), sb.toString()).get();
                }
                throw new ExecutionException(error);
            }
            else {
                break;
            }
        }

        throw new ExecutionException(PyLocalize.pydevConsoleCouldntReadIntegerValueFromStream().get());
    }

    /**
     * Encapsulates the logic of creating {@link ProcessHandler} depending on API used to execute Python Console processes.
     */
    private abstract static class ConsoleProcessCreationResult {
        protected final String myCommandLineString;
        protected final PydevConsoleCommunication myConsoleCommunication;

        private ConsoleProcessCreationResult(String commandLineString, PydevConsoleCommunication consoleCommunication) {
            myCommandLineString = commandLineString;
            myConsoleCommunication = consoleCommunication;
        }

        public abstract ProcessHandler createPythonConsoleProcessHandler(PythonConsoleView consoleView);
    }

    /**
     * Consulo: corresponds to JB {@code TargetProcessHandlerFactory} for a process started from a {@link GeneralCommandLine}.
     */
    private static final class GeneralCommandLineProcessHandlerFactory extends ConsoleProcessCreationResult {
        private final ProcessHandler myProcessHandler;

        private GeneralCommandLineProcessHandlerFactory(
            ProcessHandler handler,
            String commandLineString,
            PydevConsoleCommunication consoleCommunication
        ) {
            super(commandLineString, consoleCommunication);
            myProcessHandler = handler;
        }

        @Override
        public ProcessHandler createPythonConsoleProcessHandler(PythonConsoleView consoleView) {
            PyConsoleProcessHandlers.configureProcessHandlerForPythonConsole(myProcessHandler, consoleView, myConsoleCommunication);
            return myProcessHandler;
        }
    }

    private void initAndRun(Sdk sdk) throws ExecutionException {
        // Create Server process
        ConsoleProcessCreationResult processCreationResult = createProcess(sdk);
        UIUtil.invokeAndWaitIfNeeded((Runnable) () -> {
            // Init console view
            myConsoleView = createConsoleView(sdk);
            myConsoleView.setRunner(this);
            myConsoleView.setBorder(new SideBorder(JBColor.border(), SideBorder.LEFT));
            myPydevConsoleCommunication.setConsoleView(myConsoleView);
            myProcessHandler = processCreationResult.createPythonConsoleProcessHandler(myConsoleView);

            myConsoleExecuteActionHandler = createExecuteActionHandler();

            ProcessTerminatedListener.attach(myProcessHandler);

            PythonConsoleView consoleView = myConsoleView;
            myProcessHandler.addProcessListener(new ProcessListener() {
                @Override
                public void processTerminated(ProcessEvent event) {
                    consoleView.setEditable(false);
                    // PY-53068: When we send to python side command `exit` we don't get notifyFinished,
                    // so we should clear Console Command Queue
                    consoleView.restoreQueueWindow(true);
                }
            });

            // Attach to process
            myConsoleView.attachToProcess(myProcessHandler);
            createContentDescriptorAndActions();

            // Run
            myProcessHandler.startNotify();
        });
    }

    protected void createContentDescriptorAndActions() {
        boolean isHorizontalAndUnitedToolbar = PyExecuteConsoleCustomizer.getInstance().isHorizontalAndUnitedToolbar();
        DefaultActionGroup runToolbarActions = new DefaultActionGroup();
        ActionToolbar runActionsToolbar =
            ActionManager.getInstance().createActionToolbar("PydevConsoleRunner", runToolbarActions, isHorizontalAndUnitedToolbar);
        JPanel actionsPanel = new JPanel(new BorderLayout());
        // Left toolbar panel
        actionsPanel.add(runActionsToolbar.getComponent(), BorderLayout.WEST);

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.add(myConsoleView.getComponent(), BorderLayout.CENTER);
        myConsoleView.setToolbar(runActionsToolbar);
        runActionsToolbar.setTargetComponent(mainPanel);

        DefaultActionGroup outputToolbarActions = new DefaultActionGroup();
        if (!isHorizontalAndUnitedToolbar) {
            ActionToolbar outputActionsToolbar =
                ActionManager.getInstance().createActionToolbar("PydevConsoleRunner", outputToolbarActions, false);
            JComponent outputActionsComponent = outputActionsToolbar.getComponent();
            // Add line between toolbar panels
            int emptyBorderSize = outputActionsComponent.getBorder().getBorderInsets(outputActionsComponent).left;
            outputActionsComponent.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 1, 0, 0, JBColor.border()),
                new JBEmptyBorder(emptyBorderSize)
            ));
            // Right toolbar panel
            actionsPanel.add(outputActionsComponent, BorderLayout.CENTER);
            // Add Toolbar for PythonConsoleView
            myConsoleView.setToolbar(outputActionsToolbar);
            outputActionsToolbar.setTargetComponent(mainPanel);
            mainPanel.add(actionsPanel, BorderLayout.WEST);
        }
        else {
            mainPanel.add(actionsPanel, BorderLayout.PAGE_START);
        }

        if (myConsoleInitTitle == null) {
            ConsoleTitleGen consoleTitleGen = new ConsoleTitleGen(myProject, myTitle) {
                @Override
                protected List<String> getActiveConsoles(String consoleTitle) {
                    PythonConsoleToolWindow toolWindow = PythonConsoleToolWindow.getInstance(myProject);
                    if (toolWindow != null && toolWindow.isInitialized()) {
                        return Arrays.stream(toolWindow.getToolWindow().getContentManager().getContents())
                            .map(Content::getDisplayName)
                            .filter(s -> s.startsWith(myTitle))
                            .collect(Collectors.toList());
                    }
                    else {
                        return super.getActiveConsoles(consoleTitle);
                    }
                }
            };
            myConsoleInitTitle = consoleTitleGen.makeTitle();
        }

        RunContentDescriptor contentDescriptor =
            new RunContentDescriptor(myConsoleView, myProcessHandler, mainPanel, myConsoleInitTitle, null);
        Disposer.register(myProject, contentDescriptor);

        contentDescriptor.setFocusComputable(() -> myConsoleView.getConsoleEditor().getContentComponent());
        contentDescriptor.setAutoFocusContent(true);

        // tool bar actions
        List<AnAction> actions = fillRunActionsToolbar(runToolbarActions);

        registerActionShortcuts(actions, myConsoleView.getConsoleEditor().getComponent());
        registerActionShortcuts(actions, mainPanel);
        getConsoleView().addConsoleFolding(false, false);

        showContentDescriptor(contentDescriptor);
    }

    private void connect(String[] statements2execute) {
        if (handshake()) {
            Application.get().invokeLater(() -> {
                // Propagate console communication to language console
                PythonConsoleView consoleView = myConsoleView;

                consoleView.setConsoleCommunication(myPydevConsoleCommunication);
                consoleView.setSdk(mySdk);
                consoleView.setExecutionHandler(myConsoleExecuteActionHandler);

                enableConsoleExecuteAction();

                if (statements2execute.length == 1 && statements2execute[0].isEmpty()) {
                    statements2execute[0] = "\t";
                }

                for (String statement : statements2execute) {
                    consoleView.executeStatement(statement + "\n", ProcessOutputTypes.SYSTEM);
                }

                setUserTypeRenderers();

                fireConsoleInitializedEvent(consoleView);
                consoleView.initialized();
            });
        }
        else {
            myConsoleView.print(PyLocalize.pydevConsoleCouldntConnectToConsoleProcess().get(), ProcessOutputTypes.STDERR);
            myProcessHandler.destroyProcess();
            myConsoleView.setEditable(false);
        }
    }

    @Override
    public AnAction createRerunAction() {
        return new RestartAction(this, myConsoleInitTitle);
    }

    private void enableConsoleExecuteAction() {
        myConsoleExecuteActionHandler.setEnabled(true);
    }

    private boolean handshake() {
        return myPydevConsoleCommunication.handshake();
    }

    private void setUserTypeRenderers() {
        myPydevConsoleCommunication.setUserTypeRenderersSettings();
    }

    private AnAction createStopAction() {
        // Consulo: a local class, because an action with update() must implement AnActionWithSyncUpdate
        class StopConsoleAction extends DumbAwareAction implements AnActionWithSyncUpdate {
            StopConsoleAction() {
                //noinspection DialogTitleCapitalization
                super(
                    PyLocalize.actionDumbawarePydevconsolerunnerimplTextStopConsole(),
                    PyLocalize.actionDumbawarePydevconsolerunnerimplDescriptionStopPythonConsole(),
                    PlatformIconGroup.actionsSuspend()
                );
            }

            @Override
            public void update(AnActionEvent e) {
                e.getPresentation().setEnabled(!isConsoleProcessTerminated());
            }

            @Override
            @RequiredUIAccess
            public void actionPerformed(AnActionEvent e) {
                stopAndRerunConsole(false, PyLocalize.consoleStoppingConsole(), null);
            }
        }
        return new StopConsoleAction();
    }

    private class SoftWrapAction extends ToggleAction implements DumbAware {
        private boolean isSelected = myConsoleSettings.isUseSoftWraps();

        SoftWrapAction() {
            // Consulo: ActionsBundle texts of "EditorToggleUseSoftWraps" are kept in PyLocalize
            super(
                PyLocalize.actionEditortoggleusesoftwrapsText(),
                PyLocalize.actionEditortoggleusesoftwrapsDescription(),
                PlatformIconGroup.actionsTogglesoftwrap()
            );
            updateEditors();
        }

        @Override
        public boolean isSelected(AnActionEvent e) {
            return isSelected;
        }

        private void updateEditors() {
            myConsoleView.getEditor().getSettings().setUseSoftWraps(isSelected);
            myConsoleView.getConsoleEditor().getSettings().setUseSoftWraps(isSelected);
        }

        @Override
        @RequiredUIAccess
        public void setSelected(AnActionEvent e, boolean state) {
            isSelected = state;
            updateEditors();
            myConsoleSettings.setUseSoftWraps(isSelected);
        }
    }

    protected AnAction createSplitLineAction() {

        class ConsoleSplitLineAction extends EditorAction {

            private static final String CONSOLE_SPLIT_LINE_ACTION_ID = "Console.SplitLine";

            ConsoleSplitLineAction() {
                super(new EditorWriteActionHandler() {

                    private final EditorAction mySplitLineAction =
                        (EditorAction) ActionManager.getInstance().getAction(IdeActions.ACTION_EDITOR_SPLIT);

                    @Override
                    public boolean isEnabledForCaret(Editor editor, Caret caret, DataContext dataContext) {
                        return mySplitLineAction.getHandler().isEnabled(editor, caret, dataContext);
                    }

                    @Override
                    @RequiredWriteAction
                    public void executeWriteAction(Editor editor, @Nullable Caret caret, DataContext dataContext) {
                        mySplitLineAction.getHandler().execute(editor, caret, dataContext);
                        editor.getCaretModel().getCurrentCaret().moveCaretRelatively(0, 1, false, true);
                    }
                });
            }

            public void setup() {
                ActionUtil.mergeFrom(this, CONSOLE_SPLIT_LINE_ACTION_ID);
            }
        }

        ConsoleSplitLineAction action = new ConsoleSplitLineAction();
        action.setup();
        return action;
    }

    private void closeCommunication() {
        if (!myProcessHandler.isProcessTerminated()) {
            myPydevConsoleCommunication.close();
        }
    }

    private boolean isConsoleProcessTerminated() {
        return myProcessHandler.isProcessTerminated();
    }

    protected PythonConsoleExecuteActionHandler createExecuteActionHandler() {
        myConsoleExecuteActionHandler =
            new PydevConsoleExecuteActionHandler(myConsoleView, myProcessHandler, myPydevConsoleCommunication);
        myConsoleExecuteActionHandler.setEnabled(false);
        // TODO consulo: ConsoleHistoryController constructor with the `autoSave` flag missing
        new ConsoleHistoryControllerImpl(PyConsoleRootType.getInstance(), "", myConsoleView).install();
        return myConsoleExecuteActionHandler;
    }

    @Override
    public PydevConsoleCommunication getPydevConsoleCommunication() {
        return myPydevConsoleCommunication;
    }

    static VirtualFile getConsoleFile(PsiFile psiFile) {
        VirtualFile file = psiFile.getViewProvider().getVirtualFile();
        if (file instanceof LightVirtualFile) {
            file = ((LightVirtualFile) file).getOriginalFile();
        }
        return file;
    }

    @Override
    public void addConsoleListener(ConsoleListener consoleListener) {
        myConsoleListeners.add(consoleListener);
    }

    @Override
    public void removeConsoleListener(ConsoleListener consoleListener) {
        myConsoleListeners.remove(consoleListener);
    }

    private void fireConsoleInitializedEvent(LanguageConsoleView consoleView) {
        for (ConsoleListener listener : myConsoleListeners) {
            listener.handleConsoleInitialized(consoleView);
        }
    }

    private void fireConsoleInitializationErrorEvent(Throwable th) {
        for (ConsoleListener listener : myConsoleListeners) {
            if (listener instanceof ConsoleListenerEx l) {
                l.handleInitializationError(th);
            }
        }
    }

    @Override
    public PythonConsoleExecuteActionHandler getConsoleExecuteActionHandler() {
        return myConsoleExecuteActionHandler;
    }

    private static final class RestartAction extends AnAction {
        private final PydevConsoleRunnerImpl myConsoleRunner;
        private final @Nullable String myInitTitle;

        private RestartAction(PydevConsoleRunnerImpl runner, @Nullable String initTitle) {
            ActionUtil.copyFrom(this, IdeActions.ACTION_RERUN);
            getTemplatePresentation().setIcon(PlatformIconGroup.actionsRestart());
            myConsoleRunner = runner;
            myInitTitle = initTitle;
        }

        @Override
        @RequiredUIAccess
        public void actionPerformed(AnActionEvent e) {
            String displayName = myInitTitle;
            Project project = e.getData(Project.KEY);
            if (project != null) {
                String name = getConsoleDisplayName(project);
                if (displayName == null || !displayName.equals(name)) {
                    displayName = name;
                }
            }
            myConsoleRunner.stopAndRerunConsole(true, PyLocalize.consoleRestartingConsole(), displayName);
        }
    }

    private static @Nullable String getConsoleDisplayName(Project project) {
        PythonConsoleToolWindow toolWindow = PythonConsoleToolWindow.getInstance(project);
        ToolWindow window = toolWindow.getToolWindow();
        Content content = window.getContentManager().getSelectedContent();
        if (content == null) {
            return null;
        }
        return content.getDisplayName();
    }

    @Override
    public void reRun(boolean requestEditorFocus, @Nullable String title) {
        setConsoleTitle(title);
        run(requestEditorFocus);
    }

    private void stopAndRerunConsole(Boolean rerun, LocalizeValue message, @Nullable String displayName) {
        new Task.Backgroundable(myProject, message, true) {
            @Override
            public void run(ProgressIndicator indicator) {
                if (myProcessHandler != null) {
                    UIUtil.invokeAndWaitIfNeeded((Runnable) () -> closeCommunication());

                    boolean processStopped = myProcessHandler.waitFor(WAIT_BEFORE_FORCED_CLOSE_MILLIS);
                    if (!processStopped) {
                        tryKillProcess(myProcessHandler);
                    }
                    myProcessHandler.waitFor();
                }

                if (rerun) {
                    Application application = Application.get();
                    application.invokeLater(
                        () -> PydevConsoleRunnerImpl.this.reRun(true, displayName),
                        application.getDefaultModalityState()
                    );
                }
                else {
                    myConsoleListeners.clear();
                }
                if (myConsoleView != null && PyConsoleOptions.getInstance(PydevConsoleRunnerImpl.this.myProject).isCommandQueueEnabled()) {
                    myConsoleView.restoreQueueWindow(true);
                }
            }
        }.queue();
    }

    private static void tryKillProcess(ProcessHandler processHandler) {
        if (processHandler instanceof KillableProcessHandler) {
            if (((KillableProcessHandler) processHandler).canKillProcess()) {
                ((KillableProcessHandler) processHandler).killProcess();
            }
        }
    }

    @Override
    public void setSdk(@Nullable Sdk sdk) {
        mySdk = sdk;
    }

    @Override
    public @Nullable Sdk getSdk() {
        return mySdk;
    }

    private class ConnectDebuggerAction extends ToggleAction implements DumbAware {
        private boolean mySelected = false;
        private @Nullable XDebugSession mySession = null;

        ConnectDebuggerAction() {
            super(
                PyLocalize.consoleAttachDebugger(),
                PyLocalize.consoleAttachDebuggerDescription(),
                ExecutionDebugIconGroup.actionAttachtoprocess()
            );
        }

        @Override
        public boolean isSelected(AnActionEvent e) {
            return mySelected;
        }

        @Override
        public void update(AnActionEvent e) {
            if (mySession != null) {
                e.getPresentation().setEnabled(false);
            }
            else {
                super.update(e);
                e.getPresentation().setEnabled(true);
            }
        }

        @Override
        @RequiredUIAccess
        public void setSelected(AnActionEvent e, boolean state) {
            mySelected = state;

            if (mySelected) {
                try {
                    mySession = connectToDebugger();
                }
                catch (Exception e1) {
                    LOG.error(e1);
                    Messages.showErrorDialog(
                        PyLocalize.consoleCannotConnectToDebugger().get(),
                        PyLocalize.consoleErrorConnectingDebugger().get()
                    );
                }
            }
            else {
                //TODO: disable debugging
            }
        }
    }

    /**
     * Consulo-specific: opens one more Python console (kept from the previous Consulo runner toolbar).
     */
    private static class NewConsoleAction extends AnAction implements DumbAware {
        NewConsoleAction() {
            super(
                LocalizeValue.localizeTODO("New Console"),
                LocalizeValue.localizeTODO("Creates new python console"),
                PlatformIconGroup.generalAdd()
            );
        }

        @Override
        @RequiredUIAccess
        public void actionPerformed(AnActionEvent e) {
            PythonConsoleStarter.launchPythonConsoleRunner(e.getRequiredData(Project.KEY), e.getData(Module.KEY), runner -> runner.run(true));
        }
    }

    private XDebugSession connectToDebugger() throws ExecutionException {
        ServerSocket serverSocket = PythonCommandLineState.createServerSocket();

        XDebugProcessStarter starter = new XDebugProcessStarter() {
            @Override
            public XDebugProcess start(XDebugSession session) {
                PythonDebugLanguageConsoleView debugConsoleView = new PythonDebugLanguageConsoleView(myProject, mySdk);

                PyConsoleDebugProcessHandler consoleDebugProcessHandler =
                    new PyConsoleDebugProcessHandler(myProcessHandler);

                PyConsoleDebugProcess consoleDebugProcess =
                    new PyConsoleDebugProcess(
                        session,
                        serverSocket,
                        debugConsoleView,
                        consoleDebugProcessHandler,
                        // Consulo-specific: the console process may run on another machine
                        mySdk != null ? mySdk.getPlatform() : Platform.current()
                    );
                // Consulo-specific: the paths of a console process of another machine name the files of that machine
                Platform consolePlatform = mySdk != null ? mySdk.getPlatform() : Platform.current();
                if (!consolePlatform.equals(Platform.current())) {
                    consoleDebugProcess.setPositionConverter(new PyLocalPositionConverter(consolePlatform));
                }

                PythonDebugConsoleCommunication<PyConsoleDebugProcess> communication =
                    PyDebugRunner.initDebugConsole(myProject, consoleDebugProcess, debugConsoleView, consoleDebugProcessHandler, session);

                communication.addCommunicationListener(new ConsoleCommunicationListener() {
                    @Override
                    public void commandExecuted(boolean more) {
                        session.rebuildViews();
                    }

                    @Override
                    public void inputRequested() {
                    }
                });

                myPydevConsoleCommunication.setDebugCommunication(communication);
                debugConsoleView.attachToProcess(consoleDebugProcessHandler);

                consoleDebugProcess.waitForNextConnection();

                try {
                    consoleDebugProcess.connect(myPydevConsoleCommunication);
                }
                catch (Exception e) {
                    LOG.error(e); //TODO
                }

                myProcessHandler.notifyTextAvailable(PyLocalize.pydevConsoleDebuggerConnected().get(), ProcessOutputTypes.STDERR);

                return consoleDebugProcess;
            }
        };
        // TODO consulo: XDebuggerManager.newSessionBuilder() missing - startSessionAndShowTab() shows the tab on suspend only
        return XDebuggerManager.getInstance(myProject).startSessionAndShowTab(
            PyLocalize.pydevConsoleRunnerPythonConsoleDebugger().get(),
            PythonPsiIconGroup.python(),
            null,
            true,
            starter
        );
    }

    @Override
    public ProcessHandler getProcessHandler() {
        return myProcessHandler;
    }

    @Override
    public PythonConsoleView getConsoleView() {
        return myConsoleView;
    }

    public static class PythonConsoleRunParams implements PythonRunParams {
        private final PyConsoleOptions.PyConsoleSettings myConsoleSettings;
        private final @Nullable String myWorkingDir;
        private final Sdk mySdk;
        private final Map<String, String> myEnvironmentVariables;
        private List<String> myEnvFiles;

        public PythonConsoleRunParams(
            PyConsoleOptions.PyConsoleSettings consoleSettings,
            @Nullable String workingDir,
            Sdk sdk,
            Map<String, String> envs
        ) {
            myConsoleSettings = consoleSettings;
            myWorkingDir = workingDir;
            mySdk = sdk;
            myEnvironmentVariables = new HashMap<>();
            myEnvironmentVariables.putAll(envs);
            myEnvironmentVariables.putAll(consoleSettings.getEnvs());
            myEnvFiles = consoleSettings.myEnvFiles;
            PyDebuggerSettings debuggerSettings = PyDebuggerSettings.getInstance();
            if (debuggerSettings.getValuesPolicy() != ValuesPolicy.SYNC) {
                myEnvironmentVariables.put(PyDebugValue.POLICY_ENV_VARS.get(debuggerSettings.getValuesPolicy()), "True");
            }
            if (PyConsoleOutputCustomizer.getInstance().isInlineOutputSupported()) {
                myEnvironmentVariables.put(INLINE_OUTPUT_SUPPORTED, "True");
            }
            if (Registry.is("python.console.asyncio.repl", true)) {
                myEnvironmentVariables.put(ASYNCIO_REPL_ENV, "True");
            }
            if (myConsoleSettings.myInterpreterOptions.contains(ASYNCIO_REPL_COMMAND)) {
                myConsoleSettings.myInterpreterOptions = "";
            }
            PythonEnvUtil.setPythonDontWriteBytecode(myEnvironmentVariables);
        }

        @Override
        public String getInterpreterOptions() {
            return myConsoleSettings.getInterpreterOptions();
        }

        @Override
        public void setInterpreterOptions(String interpreterOptions) {
            throw new UnsupportedOperationException();
        }

        @Override
        public @Nullable String getWorkingDirectory() {
            return myWorkingDir;
        }

        @Override
        public void setWorkingDirectory(String workingDirectory) {
            throw new UnsupportedOperationException();
        }

        /**
         * The console runner gives an SDK, which the registry might not hold. So these parameters keep it as it is.
         */
        public Sdk getSdk() {
            return mySdk;
        }

        /**
         * Consulo: JB {@code getPythonInterpreter()} finds the {@code PythonInterpreter} of the SDK; Consulo keeps its own SDK design
         * and gives the SDK home.
         */
        @Override
        public @Nullable String getSdkHome() {
            return mySdk.getHomePath();
        }

        @Override
        public void setSdkHome(String sdkHome) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setModule(Module module) {
            throw new UnsupportedOperationException();
        }

        @Override
        public @Nullable String getModuleName() {
            return myConsoleSettings.getModuleName();
        }

        @Override
        public boolean isUseModuleSdk() {
            return myConsoleSettings.isUseModuleSdk();
        }

        @Override
        public void setUseModuleSdk(boolean useModuleSdk) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isPassParentEnvs() {
            return myConsoleSettings.isPassParentEnvs();
        }

        @Override
        public void setPassParentEnvs(boolean passParentEnvs) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Map<String, String> getEnvs() {
            return myEnvironmentVariables;
        }

        @Override
        public void setEnvs(Map<String, String> envs) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean shouldAddContentRoots() {
            return myConsoleSettings.shouldAddContentRoots();
        }

        @Override
        public boolean shouldAddSourceRoots() {
            return myConsoleSettings.shouldAddSourceRoots();
        }

        @Override
        public void setAddContentRoots(boolean flag) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setAddSourceRoots(boolean flag) {
            throw new UnsupportedOperationException();
        }

        public boolean shouldDebugJustMyCode() {
            return myConsoleSettings.shouldDebugJustMyCode();
        }

        public void setDebugJustMyCode(boolean flag) {
            LOG.warn("Tried to set debugJustMyCode flag for PythonConsoleRunParams, which is an unsupported operation");
        }

        public List<String> getEnvFilePaths() {
            return myEnvFiles;
        }

        public void setEnvFilePaths(List<String> strings) {
            myEnvFiles = strings;
        }
    }

    public interface ConsoleListenerEx extends ConsoleListener {
        void handleInitializationError(Throwable th);
    }

    /**
     * Consulo-specific: the {@link Process} of a console whose handler has no {@link java.lang.Process} - a process on the machine of a
     * remote interpreter. The communication server asks it only whether the console is alive and for its exit code.
     */
    private static final class ProcessHandlerProcess extends Process {
        private final ProcessHandler myProcessHandler;

        private ProcessHandlerProcess(ProcessHandler processHandler) {
            myProcessHandler = processHandler;
        }

        @Override
        public OutputStream getOutputStream() {
            return OutputStream.nullOutputStream();
        }

        @Override
        public InputStream getInputStream() {
            return InputStream.nullInputStream();
        }

        @Override
        public InputStream getErrorStream() {
            return InputStream.nullInputStream();
        }

        @Override
        public int waitFor() {
            myProcessHandler.waitFor();
            return exitValue();
        }

        @Override
        public boolean isAlive() {
            return !myProcessHandler.isProcessTerminated();
        }

        @Override
        public int exitValue() {
            if (!myProcessHandler.isProcessTerminated()) {
                throw new IllegalThreadStateException("The Python Console process has not exited");
            }
            Integer exitCode = myProcessHandler.getExitCode();
            return exitCode != null ? exitCode : -1;
        }

        @Override
        public void destroy() {
            myProcessHandler.destroyProcess();
        }
    }
}
