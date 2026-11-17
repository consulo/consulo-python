// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.console;

import com.jetbrains.python.console.protocol.CompletionOption;
import com.jetbrains.python.console.protocol.DebugValue;
import com.jetbrains.python.console.protocol.GetArrayResponse;
import com.jetbrains.python.console.protocol.KeyboardInterruptException;
import com.jetbrains.python.console.protocol.PythonConsoleBackendService;
import com.jetbrains.python.console.protocol.PythonConsoleFrontendService;
import com.jetbrains.python.console.protocol.PythonTableException;
import com.jetbrains.python.console.protocol.PythonUnhandledException;
import com.jetbrains.python.console.pydev.AbstractConsoleCommunication;
import com.jetbrains.python.console.pydev.InterpreterResponse;
import com.jetbrains.python.console.pydev.PydevCompletionVariant;
import com.jetbrains.python.debugger.ArrayChunk;
import com.jetbrains.python.debugger.PyDebugValue;
import com.jetbrains.python.debugger.PyDebugValueExecutionService;
import com.jetbrains.python.debugger.PyDebuggerException;
import com.jetbrains.python.debugger.PyFrameAccessor;
import com.jetbrains.python.debugger.PyFrameListener;
import com.jetbrains.python.debugger.PyReferrersLoader;
import com.jetbrains.python.debugger.PyUserTypeRenderer;
import com.jetbrains.python.debugger.pydev.GetVariableCommand;
import com.jetbrains.python.debugger.pydev.ProcessDebugger;
import com.jetbrains.python.debugger.pydev.SetUserTypeRenderersCommand;
import com.jetbrains.python.debugger.pydev.dataviewer.DataViewerCommandBuilder;
import com.jetbrains.python.debugger.pydev.dataviewer.DataViewerCommandResult;
import com.jetbrains.python.debugger.pydev.tables.PyDevCommandParameters;
import com.jetbrains.python.debugger.pydev.tables.PyDevImageCommandParameters;
import com.jetbrains.python.impl.console.actions.CommandQueueForPythonConsoleService;
import com.jetbrains.python.impl.debugger.containerview.PyViewNumericContainerAction;
import com.jetbrains.python.impl.debugger.settings.PyDebuggerSettings;
import com.jetbrains.python.impl.debugger.variablesview.usertyperenderers.ConfigureTypeRenderersActionKt;
import com.jetbrains.python.impl.debugger.variablesview.usertyperenderers.ConfigureTypeRenderersHyperLink;
import com.jetbrains.python.impl.debugger.variablesview.usertyperenderers.PyUserNodeRenderer;
import com.jetbrains.python.impl.debugger.variablesview.usertyperenderers.PyUserTypeRenderersSettings;
import com.jetbrains.python.parsing.console.PythonConsoleData;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.tables.TableCommandParameters;
import com.jetbrains.python.tables.TableCommandType;
import consulo.application.Application;
import consulo.application.progress.EmptyProgressIndicator;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.ProgressManager;
import consulo.application.progress.Task;
import consulo.application.util.ApplicationUtil;
import consulo.component.ProcessCanceledException;
import consulo.execution.debug.XSourcePosition;
import consulo.execution.debug.frame.XCompositeNode;
import consulo.execution.debug.frame.XDebuggerTreeNodeHyperlink;
import consulo.execution.debug.frame.XStackFrame;
import consulo.execution.debug.frame.XStandaloneVariablesView;
import consulo.execution.debug.frame.XValueChildrenList;
import consulo.execution.debug.frame.XValueNode;
import consulo.fileEditor.FileEditorManager;
import consulo.language.editor.WriteCommandAction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.Lists;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.StandardFileSystems;
import consulo.virtualFileSystem.VirtualFile;
import org.apache.thrift.TException;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Function;

import static com.jetbrains.python.debugger.pydev.dataviewer.DataViewerCommandResult.ResultType.UNHANDLED_ERROR;
import static com.jetbrains.python.impl.console.PydevConsoleCommunicationUtil.createArrayChunk;
import static com.jetbrains.python.impl.console.PydevConsoleCommunicationUtil.createPyDebugValue;
import static com.jetbrains.python.impl.console.PydevConsoleCommunicationUtil.parseVars;

/**
 * Communication with Python console backend using Thrift services.
 *
 * @author Fabio
 */
public abstract class PydevConsoleCommunication extends AbstractConsoleCommunication implements PyFrameAccessor {
    private static final Logger LOG = Logger.getInstance(PydevConsoleCommunication.class);

    protected volatile boolean keyboardInterruption;
    /**
     * Input that should be sent to the server (waiting for raw_input)
     */
    protected volatile @Nullable String inputReceived;
    /**
     * Response that should be sent back to the shell.
     */
    protected volatile @Nullable InterpreterResponse nextResponse;

    private boolean myExecuting;
    private @Nullable PythonDebugConsoleCommunication myDebugCommunication;
    private boolean myNeedsMore = false;
    /**
     * UI sends a lot of repeated requests for the same expression
     * Store result of the previous request to avoid sending the same command several times
     */
    private @Nullable Pair<String, String> myPrevNameToDescription = null;

    private int myFullValueSeq = 0;
    private final Map<Integer, List<PyFrameAccessor.PyAsyncValue<String>>> myCallbackHashMap = new ConcurrentHashMap<>();

    private @Nullable PythonConsoleView myConsoleView;
    private final List<PyFrameListener> myFrameListeners = Lists.newLockFreeCopyOnWriteList();

    private @Nullable XCompositeNode myCurrentRootNode;

    public @Nullable PsiFile getHistoryPsiFile() {
        return myConsoleView != null ? myConsoleView.getHistoryPsiFile() : null;
    }

    /**
     * Initializes the bidirectional RPC communication.
     */
    public PydevConsoleCommunication(Project project) {
        super(project);
    }

    /**
     * Returns thread safe, Python Console process-aware and disposable
     * {@link PythonConsoleBackendService.Iface}. Requests to the returned
     * {@link PythonConsoleBackendService.Iface} will be processed sequentially.
     * If Python Console process is detected to be finished the current request
     * will be interrupted and {@link PyConsoleProcessFinishedException} is
     * thrown.
     *
     * @return thread safe and related Python Console process-aware
     * {@link PythonConsoleBackendService.Iface}
     * @throws CommunicationClosedException if transport is closed
     */
    protected abstract PythonConsoleBackendServiceDisposable getPythonConsoleBackendClient();

    /**
     * Sends <i>handshake</i> message to Python Console backend. Returns
     * {@code true} if Python Console backend replies with <i>PyCharm</i> string.
     * Returns {@code false} if Python Console backend replies with unexpected
     * message or Python Console process is finished or Python Console is closed.
     *
     * @return whether <i>handshake</i> with Python Console backend succeeded
     * @throws RuntimeException if transport (protocol) error occurs
     */
    public boolean handshake() {
        if (!isCommunicationClosed()) {
            try {
                return "PyCharm".equals(getPythonConsoleBackendClient().handshake());
            }
            catch (CommunicationClosedException | PyConsoleProcessFinishedException e) {
                return false;
            }
            catch (TException e) {
                throw new RuntimeException(e);
            }
        }
        else {
            return false;
        }
    }

    /**
     * Stops the communication with the client (passes message for it to quit).
     */
    public void close() {
        PyDebugValueExecutionService.getInstance(myProject).sessionStopped(this);
        notifySessionStopped();
        myCallbackHashMap.clear();

        new Task.Backgroundable(myProject, PyLocalize.consoleCloseConsoleCommunication(), false) {
            @Override
            public void run(ProgressIndicator indicator) {
                try {
                    closeCommunication().get();
                }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                catch (ExecutionException e) {
                    // it could help us diagnose some intricate cases
                    LOG.debug(e);
                }
            }
        }.queue();
    }

    /**
     * Stops the communication with the client (passes message for it to quit).
     *
     * @return {@link Future} that allows to wait for Python Console transport
     * thread(s) to finish its execution
     */
    public Future<?> closeAsync() {
        PyDebugValueExecutionService.getInstance(myProject).sessionStopped(this);
        myCallbackHashMap.clear();

        return closeCommunication();
    }

    /**
     * Closes the communication with Python Console backend gracefully. Returns
     * {@link Future} that allows to wait for communication resources
     * (corresponding {@link java.util.concurrent.ExecutorService} and threads)
     * to be finished.
     * <p>
     * The method is not expected to throw any exception as well as the returned
     * {@link Future}.
     *
     * @return {@link Future}
     */
    protected abstract Future<?> closeCommunication();

    public abstract boolean isCommunicationClosed();

    /*
     * Variables that control when we're expecting to give some input to the server or when we're
     * adding some line to be executed
     */

    @Override
    public @Nullable Project getProject() {
        return myProject;
    }

    /**
     * Helper to keep on busy loop.
     */
    private final Object lock = new Object();

    private void execNotifyAboutMagic(List<String> commands, boolean isAutoMagic) {
        if (getConsoleFile() != null) {
            PythonConsoleData consoleData = PyConsoleUtil.getOrCreateIPythonData(getConsoleFile());
            consoleData.setIPythonAutomagic(isAutoMagic);
            consoleData.setIPythonMagicCommands(commands);
        }
    }

    private boolean execIPythonEditor(@Nullable String path) {
        VirtualFile file = StringUtil.isEmpty(path) ? null : StandardFileSystems.local().findFileByPath(path);
        if (file != null) {
            Application.get().invokeLater(() -> FileEditorManager.getInstance(myProject).openFile(file, true));

            return true;
        }

        return false;
    }

    private void execNotifyFinished(boolean more) {
        myNeedsMore = more;
        setExecuting(false);
        notifyCommandExecuted(more);
    }

    private void setExecuting(boolean executing) {
        myExecuting = executing;
    }

    private Object execRequestInput() throws KeyboardInterruptException {
        waitingForInput = true;
        inputReceived = null;
        keyboardInterruption = false;
        boolean needInput = true;

        //let the busy loop from execInterpreter free and enter a busy loop
        //in this function until execInterpreter gives us an input
        nextResponse = new InterpreterResponse(false, needInput);

        notifyInputRequested();

        //busy loop until we have an input
        while (inputReceived == null) {
            if (keyboardInterruption) {
                waitingForInput = false;

                throw new KeyboardInterruptException();
            }
            synchronized (lock) {
                try {
                    lock.wait(10);
                }
                catch (InterruptedException e) {
                    //pass
                }
            }
        }
        return inputReceived;
    }

    /**
     * Executes the needed command
     *
     * @return a Pair with (null, more) or (error, false)
     */
    protected Pair<@Nullable String, Boolean> exec(ConsoleCodeFragment command) throws PythonUnhandledException {
        setExecuting(true);

        // add code fragment to myConsoleView.getHistoryPsiFile()
        PsiFile psi = getHistoryPsiFile();
        PythonConsoleView consoleView = myConsoleView;
        if (consoleView != null && psi != null) {
            Application.get().invokeLater(
                () -> WriteCommandAction.runWriteCommandAction(
                    myProject,
                    null,
                    null,
                    () -> {
                        PsiElement[] newElems = PyElementGenerator.getInstance(myProject)
                            .createDummyFile(LanguageLevel.forElement(consoleView.getFile()), command.getText())
                            .getChildren();
                        for (PsiElement elem : newElems) {
                            psi.add(elem);
                        }
                    },
                    psi
                )
            );
        }

        boolean more;
        try {
            if (command.isSingleLine()) {
                more = getPythonConsoleBackendClient().execLine(command.getText());
            }
            else {
                more = getPythonConsoleBackendClient().execMultipleLines(command.getText());
            }
        }
        catch (PythonUnhandledException e) {
            setExecuting(false);
            throw e;
        }
        catch (TException e) {
            setExecuting(false);
            throw new RuntimeException(e);
        }

        if (more) {
            setExecuting(false);
        }

        return Pair.create(null, more);
    }

    private static LocalizeValue createRuntimeMessage(LocalizeValue taskName) {
        return PyLocalize.consoleGettingFromRuntime(taskName);
    }

    /**
     * @return completions from the client
     */
    @Override
    public List<PydevCompletionVariant> getCompletions(String text, String actTok) throws Exception {
        if (waitingForInput || isExecuting()) {
            return Collections.emptyList();
        }
        ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
        indicator.setText(createRuntimeMessage(PyLocalize.consoleGettingCompletion()));
        return ApplicationUtil.runWithCheckCanceled(
            () -> {
                return doGetCompletions(text, actTok);
            },
            indicator
        );
    }

    private List<PydevCompletionVariant> doGetCompletions(String text, String actTok) throws Exception {
        try {
            PythonDebugConsoleCommunication debugCommunication = myDebugCommunication;
            if (debugCommunication != null && debugCommunication.isSuspended()) {
                return debugCommunication.getCompletions(text, actTok);
            }
            else {
                List<CompletionOption> fromServer = getPythonConsoleBackendClient().getCompletions(text, actTok);
                return ContainerUtil.map(fromServer, option -> toPydevCompletionVariant(option));
            }
        }
        catch (PythonUnhandledException e) {
            LOG.warn("Completion error in Python Console: " + e.traceback);
            return Collections.emptyList();
        }
    }

    public @Nullable String execRaw(String command) throws PyDebuggerException {
        if (!isCommunicationClosed()) {
            return executeBackgroundTask(
                () -> {
                    try {
                        return getPythonConsoleBackendClient().execRaw(command);
                    }
                    catch (PythonUnhandledException e) {
                        throw new PyDebuggerException(e.traceback);
                    }
                    finally {
                        notifyCommandExecuted(false);
                    }
                },
                true,
                createRuntimeMessage(PyLocalize.consoleGettingTableData()),
                PyLocalize.consoleTableFailedToLoad().get()
            );
        }
        else {
            return null;
        }
    }

    @Override
    public @Nullable String execTableCommand(
        String command,
        TableCommandType commandType,
        @Nullable TableCommandParameters tableCommandParameters
    ) throws PyDebuggerException {
        if (!isCommunicationClosed()) {
            return executeBackgroundTask(
                () -> {
                    String startIndex = "";
                    String endIndex = "";
                    String format = "";
                    try {
                        if (tableCommandParameters instanceof PyDevCommandParameters) {
                            startIndex = String.valueOf(((PyDevCommandParameters) tableCommandParameters).getStart());
                            endIndex = String.valueOf(((PyDevCommandParameters) tableCommandParameters).getEnd());
                            format = String.valueOf(((PyDevCommandParameters) tableCommandParameters).getFormat());
                        }
                        return getPythonConsoleBackendClient().execTableCommand(command, commandType.name(), startIndex, endIndex, format);
                    }
                    catch (PythonTableException e) {
                        throw new PyDebuggerException(e.message);
                    }
                },
                true,
                createRuntimeMessage(PyLocalize.consoleGettingTableData()),
                PyLocalize.consoleTableFailedToLoad().get()
            );
        }
        else {
            return null;
        }
    }

    @Override
    public @Nullable String execTableImageCommand(
        String command,
        TableCommandType commandType,
        @Nullable TableCommandParameters tableCommandParameters
    ) throws PyDebuggerException {
        if (!isCommunicationClosed()) {
            return executeBackgroundTask(
                () -> {
                    String offset = "";
                    String imageId = "";
                    try {
                        if (tableCommandParameters instanceof PyDevImageCommandParameters) {
                            offset = String.valueOf(((PyDevImageCommandParameters) tableCommandParameters).getOffset());
                            imageId = String.valueOf(((PyDevImageCommandParameters) tableCommandParameters).getImageId());
                        }
                        return getPythonConsoleBackendClient().execTableImageCommand(command, commandType.name(), offset, imageId);
                    }
                    catch (PythonTableException e) {
                        throw new PyDebuggerException(e.message);
                    }
                },
                true,
                createRuntimeMessage(PyLocalize.consoleGettingTableData()),
                PyLocalize.consoleTableFailedToLoad().get()
            );
        }
        else {
            return null;
        }
    }

    public List<PydevCompletionVariant> gerCompletionVariants(String text, String actTok) throws Exception {
        return doGetCompletions(text, actTok);
    }

    private static PydevCompletionVariant toPydevCompletionVariant(CompletionOption option) {
        String args = String.join(" ", option.arguments);
        return new PydevCompletionVariant(option.name, option.documentation, args, option.type.getValue());
    }

    private void executeBackgroundTaskSuppressException(
        Callable<?> task,
        LocalizeValue userVisibleMessage,
        String errorLogMessage
    ) {
        try {
            executeBackgroundTask(task, false, userVisibleMessage, errorLogMessage);
        }
        catch (Exception e) {
            LOG.error(e);
        }
    }

    private <T> @Nullable T executeBackgroundTask(
        Callable<T> task,
        boolean waitForResult,
        LocalizeValue userVisibleMessage,
        String errorLogMessage
    ) throws PyDebuggerException {
        CompletableFuture<@Nullable T> future = new CompletableFuture<>();
        new Task.Backgroundable(myProject, userVisibleMessage, false) {
            @Override
            public void run(ProgressIndicator indicator) {
                try {
                    T result = task.call();
                    future.complete(result);
                }
                catch (PythonUnhandledException e) {
                    LOG.error(errorLogMessage + e.traceback);
                    future.complete(null);
                }
                catch (Exception e) {
                    future.completeExceptionally(e);
                }
            }
        }.queue();
        if (waitForResult) {
            try {
                return future.get();
            }
            catch (InterruptedException | ExecutionException e) {
                throw new PyDebuggerException(errorLogMessage + e.getMessage(), e);
            }
        }
        return null;
    }

    /**
     * @return the description of the given attribute in the shell
     */
    @Override
    public String getDescription(String text) throws Exception {
        PythonDebugConsoleCommunication debugCommunication = myDebugCommunication;
        if (debugCommunication != null && debugCommunication.isSuspended()) {
            return debugCommunication.getDescription(text);
        }
        if (waitingForInput) {
            return "Unable to get description: waiting for input.";
        }
        Pair<String, String> prevNameToDescription = myPrevNameToDescription;
        if (prevNameToDescription != null) {
            if (prevNameToDescription.first.equals(text)) {
                return prevNameToDescription.second;
            }
        }
        else {
            // add temporary value to avoid repeated requests for the same expression
            myPrevNameToDescription = Pair.create(text, "");
        }
        // TODO consulo: Application.assertIsNonDispatchThread() missing

        ProgressManager progressManager = ProgressManager.getInstance();
        ProgressIndicator indicator =
            progressManager.hasProgressIndicator() ? progressManager.getProgressIndicator() : new EmptyProgressIndicator();
        indicator.setText(createRuntimeMessage(PyLocalize.consoleGettingDocumentation()));
        return ApplicationUtil.runWithCheckCanceled(
            () -> {
                String resultDescription = getPythonConsoleBackendClient().getDescription(text);
                myPrevNameToDescription = Pair.create(text, resultDescription);
                return resultDescription;
            },
            indicator
        );
    }

    /**
     * Executes a given line in the interpreter.
     *
     * @param command the command to be executed in the client
     */
    @Override
    public void execInterpreter(ConsoleCodeFragment command, Function<InterpreterResponse, Object> onResponseReceived) {
        PythonDebugConsoleCommunication debugCommunication = myDebugCommunication;
        if (debugCommunication != null && debugCommunication.isSuspended()) {
            debugCommunication.execInterpreter(command, onResponseReceived);
            return; //TODO: handle text input and other cases
        }
        nextResponse = null;
        if (waitingForInput && myConsoleView != null && myConsoleView.isInitialized()) {
            inputReceived = command.getText();
            waitingForInput = false;
            //the thread that we started in the last exec is still alive if we were waiting for an input.
        }
        else {
            //create a thread that'll keep locked until an answer is received from the server.
            new Task.Backgroundable(myProject, PyLocalize.consoleWaitingExecutionResult(), false) {
                @Override
                public void run(ProgressIndicator indicator) {
                    try {
                        if (indicator.isCanceled()) {
                            return;
                        }
                        Pair<@Nullable String, Boolean> executed = exec(command);
                        nextResponse = new InterpreterResponse(executed.second, false);
                    }
                    catch (ProcessCanceledException e) {
                        //ignore
                    }
                    catch (PythonUnhandledException e) {
                        LOG.error("Error in execInterpreter():" + e.traceback);
                        nextResponse = new InterpreterResponse(false, false);
                        notifyCommandExecuted(true);
                    }
                    catch (Exception e) {
                        nextResponse = new InterpreterResponse(false, false);
                        notifyCommandExecuted(true);
                    }
                    finally {
                        InterpreterResponse response = nextResponse;
                        if (response != null && response.more) {
                            myNeedsMore = true;
                        }
                        onResponseReceived.apply(response);
                    }
                }
            }.queue();
        }
    }

    @Override
    public void interrupt() {
        if (waitingForInput) {
            // we do not want to forcibly `interrupt()` the `requestInput()` on the
            // Python side otherwise the message queue to the IDE will be broken
            keyboardInterruption = true;
            return;
        }
        executeBackgroundTaskSuppressException(
            () ->
            {
                getPythonConsoleBackendClient().interrupt();
                return null;
            },
            PyLocalize.consoleInterruptingExecution(),
            ""
        );
    }

    @Override
    public boolean isExecuting() {
        return myExecuting;
    }

    @Override
    public boolean needsMore() {
        return myNeedsMore;
    }

    @Override
    public @Nullable PyDebugValue evaluate(String expression, boolean execute, boolean doTrunc) throws PyDebuggerException {
        if (!isCommunicationClosed()) {
            return executeBackgroundTask(
                () -> {
                    List<DebugValue> debugValues = getPythonConsoleBackendClient().evaluate(expression, doTrunc);
                    return createPyDebugValue(debugValues.iterator().next(), this);
                },
                true,
                PyLocalize.consoleEvaluatingExpressionInConsole(),
                "Error in evaluate():"
            );
        }
        else {
            return null;
        }
    }

    @Override
    public @Nullable XValueChildrenList loadFrame(@Nullable XStackFrame contextFrame) throws PyDebuggerException {
        return loadFrame(() -> {
            List<DebugValue> frame = getPythonConsoleBackendClient().getFrame(ProcessDebugger.GROUP_TYPE.DEFAULT.ordinal());
            XValueChildrenList frameValues = parseVars(frame, null, this);
            notifyVariablesLoaded(frameValues);
            return frameValues;
        });
    }

    @Override
    public @Nullable XValueChildrenList loadSpecialVariables(ProcessDebugger.GROUP_TYPE groupType) throws PyDebuggerException {
        return loadFrame(() -> {
            List<DebugValue> frame = getPythonConsoleBackendClient().getFrame(groupType.ordinal());
            XValueChildrenList values = parseVars(frame, null, this);
            PyDebugValue.getAsyncValues(null, this, values);
            return values;
        });
    }

    private <T> @Nullable XValueChildrenList loadFrame(Callable<T> task) throws PyDebuggerException {
        if (!isCommunicationClosed()) {
            return (XValueChildrenList) executeBackgroundTask(
                task,
                true,
                createRuntimeMessage(PyLocalize.consoleGettingFrameVariables()),
                "Error in loadFrame():"
            );
        }
        else {
            return new XValueChildrenList();
        }
    }

    public synchronized int getNextFullValueSeq() {
        myFullValueSeq++;
        return myFullValueSeq;
    }

    @Override
    public void loadAsyncVariablesValues(@Nullable XStackFrame frame, List<PyAsyncValue<String>> pyAsyncValues) {
        PyDebugValueExecutionService.getInstance(myProject).submitTask(this, () -> {
            try {
                List<String> evaluationExpressions = new ArrayList<>();
                for (PyAsyncValue<String> asyncValue : pyAsyncValues) {
                    evaluationExpressions.add(GetVariableCommand.composeName(asyncValue.getDebugValue()));
                }
                int seq = getNextFullValueSeq();
                myCallbackHashMap.put(seq, pyAsyncValues);

                getPythonConsoleBackendClient().loadFullValue(seq, evaluationExpressions);

                // previously `loadFullValue()` might return `List<PyDebugValue>` but this is no longer true
            }
            catch (CommunicationClosedException | PyConsoleProcessFinishedException | TException e) {
                for (PyAsyncValue<String> asyncValue : pyAsyncValues) {
                    PyDebugValue value = asyncValue.getDebugValue();
                    for (XValueNode node : value.getValueNodes()) {
                        if (node != null && !node.isObsolete()) {
                            if (e.getMessage().startsWith("Timeout") || e.getMessage().startsWith("Console already exited")) {
                                value.updateNodeValueAfterLoading(
                                    node,
                                    " ",
                                    LocalizeValue.empty(),
                                    PyLocalize.debuggerVariablesViewLoadingTimedOut().get()
                                );
                            }
                            else {
                                LOG.error(e);
                            }
                        }
                    }
                }
            }
        });
    }

    @Override
    public @Nullable XValueChildrenList loadVariableDefaultView(PyDebugValue variable) throws PyDebuggerException {
        if (!isCommunicationClosed()) {
            return executeBackgroundTask(
                () -> {
                    String name = variable.getOffset() == 0 ? GetVariableCommand.composeName(variable)
                        : variable.getOffset() + "\t" + GetVariableCommand.composeName(variable);
                    List<DebugValue> ret = getPythonConsoleBackendClient().getVariable(name);
                    return parseVars(ret, variable, this);
                },
                true,
                createRuntimeMessage(PyLocalize.consoleGettingVariableValue()),
                "Error in loadVariable():"
            );
        }
        else {
            return new XValueChildrenList();
        }
    }

    @Override
    public @Nullable XValueChildrenList loadVariable(PyDebugValue var) throws PyDebuggerException {
        PyUserNodeRenderer typeRenderer = ConfigureTypeRenderersActionKt.getTypeRenderer(var);
        if (typeRenderer != null) {
            return executeBackgroundTask(
                () -> {
                    return ConfigureTypeRenderersActionKt.loadTypeRendererChildren(this, var, typeRenderer);
                },
                true,
                createRuntimeMessage(PyLocalize.consoleGettingVariableValue()),
                "Error in loadVariable():"
            );
        }
        else {
            return loadVariableDefaultView(var);
        }
    }

    @Override
    public void setCurrentRootNode(XCompositeNode node) {
        myCurrentRootNode = node;
    }

    @Override
    public boolean isSimplifiedView() {
        return PyDebuggerSettings.getInstance().isSimplifiedView();
    }

    @Override
    public @Nullable XCompositeNode getCurrentRootNode() {
        return myCurrentRootNode;
    }

    @Override
    public void changeVariable(PyDebugValue variable, String value) {
        if (!isCommunicationClosed()) {
            executeBackgroundTaskSuppressException(
                () -> {
                    // NOTE: The actual change is being scheduled in the exec_queue in main thread
                    // This method is async now
                    getPythonConsoleBackendClient().changeVariable(variable.getEvaluationExpression(), value);
                    return null;
                },
                PyLocalize.consoleChangingVariable(),
                "Error in changeVariable():"
            );
        }
    }

    @Override
    public @Nullable PyReferrersLoader getReferrersLoader() {
        return null;
    }

    @Override
    public @Nullable ArrayChunk getArrayItems(PyDebugValue var, int rowOffset, int colOffset, int rows, int cols, String format)
        throws PyDebuggerException {
        if (!isCommunicationClosed()) {
            return executeBackgroundTask(
                () -> {
                    GetArrayResponse ret =
                        getPythonConsoleBackendClient().getArray(var.getEvaluationExpression(), rowOffset, colOffset, rows, cols, format);
                    return createArrayChunk(ret, this);
                },
                true,
                createRuntimeMessage(PyLocalize.consoleGettingArray()),
                "Error in getArrayItems():"
            );
        }
        else {
            return null;
        }
    }

    @Override
    public @Nullable DataViewerCommandResult executeDataViewerCommand(DataViewerCommandBuilder builder) throws PyDebuggerException {
        if (!isCommunicationClosed()) {
            return executeBackgroundTask(
                () -> {
                    try {
                        String[] args = builder.getArgs();
                        getPythonConsoleBackendClient().execDataViewerAction(
                            builder.getVar().getName(),
                            builder.getAction().name(),
                            args == null ? "" : String.join("\t", args)
                        );

                        return DataViewerCommandResult.makeSuccessResult("Export successful");
                    }
                    catch (PythonUnhandledException e) {
                        return DataViewerCommandResult.errorFromExportTraceback(e.getTraceback());
                    }
                },
                true,
                PyLocalize.consoleExecutingDataviewerCommand(),
                "Error in DataViewer command:"
            );
        }
        return DataViewerCommandResult.makeErrorResult(UNHANDLED_ERROR, "Console communication is closed");
    }

    @Override
    public @Nullable XSourcePosition getSourcePositionForName(@Nullable String name, @Nullable String parentType) {
        return null;
    }

    @Override
    public @Nullable XSourcePosition getSourcePositionForType(String type) {
        return null;
    }

    /**
     * Request that pydevconsole connect (with pydevd) to the specified port
     *
     * @param localPort port for pydevd to connect to.
     * @param dbgOpts   additional debugger options (that are normally passed via command line) to apply
     * @throws Exception if connection fails
     */
    public void connectToDebugger(
        int localPort,
        @Nullable String debuggerHost,
        Map<String, Boolean> dbgOpts,
        Map<String, String> extraEnvs
    ) throws Exception {
        if (waitingForInput) {
            throw new Exception("Can't connect debugger now, waiting for input");
        }
        executeBackgroundTask(
            () -> {
                // though `connectToDebugger` returns "connect complete" string, let us just ignore it
                getPythonConsoleBackendClient().connectToDebugger(localPort, debuggerHost, dbgOpts, extraEnvs);
                return null;
            },
            true,
            PyLocalize.consoleConnectingToDebugger(),
            "Error in connectToDebugger():"
        );
    }

    @Override
    public void notifyCommandExecuted(boolean more) {
        super.notifyCommandExecuted(more);
        Application.get().getMessageBus().syncPublisher(PyFrameListener.class).frameChanged();
        for (PyFrameListener listener : myFrameListeners) {
            listener.frameChanged();
        }
    }

    private void notifyVariablesLoaded(XValueChildrenList values) {
        Application.get().getMessageBus().syncPublisher(PyFrameListener.class).valuesUpdated(this, values);
        for (PyFrameListener listener : myFrameListeners) {
            listener.valuesUpdated(this, values);
        }
    }

    void notifyViewCreated(XStandaloneVariablesView view) {
        Application.get().getMessageBus().syncPublisher(PyFrameListener.class).viewCreated(this, view);
        for (PyFrameListener listener : myFrameListeners) {
            listener.viewCreated(this, view);
        }
    }

    private void notifySessionStopped() {
        Application.get().getMessageBus().syncPublisher(PyFrameListener.class).sessionStopped(this);
        for (PyFrameListener listener : myFrameListeners) {
            listener.sessionStopped(this);
        }
    }

    @Override
    public void setUserTypeRenderersSettings() {
        if (!isCommunicationClosed()) {
            try {
                executeBackgroundTask(
                    () -> {
                        PyUserTypeRenderersSettings settings = PyUserTypeRenderersSettings.getInstance();
                        if (settings == null) {
                            return false;
                        }
                        List<PyUserTypeRenderer> renderers = settings.getApplicableRenderers();
                        if (renderers.isEmpty()) {
                            return false;
                        }
                        String renderersMessage = SetUserTypeRenderersCommand.createMessage(renderers);
                        return getPythonConsoleBackendClient().setUserTypeRenderers(renderersMessage);
                    },
                    false,
                    createRuntimeMessage(PyLocalize.consoleSettingUserTypeRenderers()),
                    "Error in setUserTypeRenderersSettings():"
                );
            }
            catch (PyDebuggerException e) {
                LOG.warn("Failed to send Type Renderers", e);
            }
        }
    }

    @Override
    public @Nullable XDebuggerTreeNodeHyperlink getUserTypeRenderersLink(String typeRendererId) {
        return new ConfigureTypeRenderersHyperLink(typeRendererId, getProject(), null);
    }

    public void setDebugCommunication(PythonDebugConsoleCommunication debugCommunication) {
        myDebugCommunication = debugCommunication;
    }

    public void setConsoleView(@Nullable PythonConsoleView consoleView) {
        myConsoleView = consoleView;
    }

    @Override
    public void showNumericContainer(PyDebugValue value) {
        PyViewNumericContainerAction.showNumericViewer(myProject, value);
    }

    @Override
    public void addFrameListener(PyFrameListener listener) {
        myFrameListeners.add(listener);
    }

    protected final PythonConsoleFrontendService.Iface createPythonConsoleFrontendHandler() {
        return new PythonConsoleFrontendHandler();
    }

    private class PythonConsoleFrontendHandler implements PythonConsoleFrontendService.Iface {

        @Override
        public void notifyFinished(boolean needsMoreInput, boolean exceptionOccurred) {
            if (PyConsoleOptions.getInstance(myProject).isCommandQueueEnabled()) {
                // notify the CommandQueue service that the command has been completed without exceptions
                // and it must be removed from the queue
                // or clear queue if exception occurred
                CommandQueueForPythonConsoleService.getInstance(myProject)
                    .removeCommand(PydevConsoleCommunication.this, exceptionOccurred);
            }
            execNotifyFinished(needsMoreInput);
        }

        @Override
        public String requestInput(String path) throws KeyboardInterruptException {
            return (String) execRequestInput();
        }

        @Override
        public void notifyAboutMagic(List<String> commands, boolean isAutoMagic) {
            execNotifyAboutMagic(commands, isAutoMagic);
        }

        @Override
        public void showConsole() {
            if (myConsoleView != null) {
                myConsoleView.setConsoleEnabled(true);
            }
        }

        @Override
        public void returnFullValue(int requestSeq, List<DebugValue> response) {
            List<PyAsyncValue<String>> values = myCallbackHashMap.remove(requestSeq);
            try {
                List<PyDebugValue> debugValues =
                    ContainerUtil.map(response, value -> createPyDebugValue(value, PydevConsoleCommunication.this));
                for (int i = 0; i < debugValues.size(); ++i) {
                    PyDebugValue resultValue = debugValues.get(i);
                    values.get(i).getCallback().ok(resultValue.getValue());
                }
            }
            catch (Exception e) {
                if (values != null) {
                    for (PyFrameAccessor.PyAsyncValue<String> vars : values) {
                        vars.getCallback().error(new PyDebuggerException(response.toString()));
                    }
                }
            }
        }

        @Override
        public boolean IPythonEditor(String path, String line) {
            return execIPythonEditor(path);
        }

        @Override
        public void sendRichOutput(Map<String, String> data) {
            if (myConsoleView == null) {
                return;
            }
            if (data.isEmpty()) {
                return;
            }
            PyConsoleOutputCustomizer.getInstance().showRichOutput(myConsoleView, data);
        }
    }

    protected static class CommunicationClosedException extends RuntimeException {
    }
}
