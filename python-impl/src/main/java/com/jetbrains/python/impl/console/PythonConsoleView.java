// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

import com.jetbrains.python.console.pydev.ConsoleCommunication;
import com.jetbrains.python.console.pydev.ConsoleCommunicationListener;
import com.jetbrains.python.debugger.PyDebugValueDescriptor;
import com.jetbrains.python.debugger.PyStackFrameInfo;
import com.jetbrains.python.highlighting.PyHighlighter;
import com.jetbrains.python.impl.console.actions.CommandQueueForPythonConsoleService;
import com.jetbrains.python.impl.console.actions.CommandQueueListener;
import com.jetbrains.python.impl.console.completion.PythonConsoleAutopopupBlockingHandler;
import com.jetbrains.python.impl.console.pythonCommandQueue.PythonCommandQueuePanel;
import com.jetbrains.python.impl.debugger.PyDebuggerEditorsProvider;
import com.jetbrains.python.impl.debugger.PyDebuggerOptionsProvider;
import com.jetbrains.python.impl.debugger.PyStackFrame;
import com.jetbrains.python.impl.psi.impl.PyExpressionCodeFragmentImpl;
import com.jetbrains.python.impl.psi.impl.PythonLanguageLevelPusher;
import com.jetbrains.python.impl.sdk.PythonSdkType;
import com.jetbrains.python.impl.sdk.flavors.PythonSdkFlavor;
import com.jetbrains.python.psi.LanguageLevel;
import consulo.application.Application;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.ProgressManager;
import consulo.application.progress.Task;
import consulo.application.ui.wm.IdeFocusManager;
import consulo.codeEditor.Editor;
import consulo.codeEditor.EditorColors;
import consulo.codeEditor.EditorFactory;
import consulo.content.bundle.Sdk;
import consulo.dataContext.DataManager;
import consulo.disposer.Disposer;
import consulo.document.Document;
import consulo.document.util.TextRange;
import consulo.execution.debug.frame.XStandaloneVariablesView;
import consulo.execution.debug.frame.XStandaloneVariablesViewFactory;
import consulo.execution.ui.console.ConsoleViewContentType;
import consulo.execution.ui.console.ConsoleViewUtil;
import consulo.execution.ui.console.ObservableConsoleView;
import consulo.execution.ui.console.OpenFileHyperlinkInfo;
import consulo.ide.impl.idea.execution.console.LanguageConsoleImpl;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiUtilCore;
import consulo.logging.Logger;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.process.ProcessOutputType;
import consulo.process.ProcessOutputTypes;
import consulo.project.Project;
import consulo.project.ui.wm.ToolWindowManager;
import consulo.project.ui.wm.ToolWindowManagerListener;
import consulo.python.impl.localize.PyLocalize;
import consulo.ui.ex.JBColor;
import consulo.ui.ex.action.ActionToolbar;
import consulo.ui.ex.awt.JBSplitter;
import consulo.ui.ex.awt.OnePixelSplitter;
import consulo.ui.ex.awt.UIUtil;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.ui.ex.content.Content;
import consulo.ui.ex.popup.JBPopup;
import consulo.ui.ex.popup.JBPopupFactory;
import consulo.ui.ex.popup.event.JBPopupListener;
import consulo.ui.ex.popup.event.LightweightWindowEvent;
import consulo.ui.ex.toolWindow.ToolWindow;
import consulo.ui.ex.toolWindow.ToolWindowAnchor;
import consulo.undoRedo.CommandProcessor;
import consulo.util.concurrent.ActionCallback;
import consulo.util.dataholder.Key;
import consulo.util.lang.StringUtil;
import consulo.util.lang.TimeoutUtil;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.jetbrains.python.impl.console.PydevConsoleRunner.CONSOLE_COMMUNICATION_KEY;

public final class PythonConsoleView extends LanguageConsoleImpl implements ObservableConsoleView, PyCodeExecutor {
    public static final Key<Boolean> CONSOLE_KEY = new Key<>("PYDEV_CONSOLE_KEY");
    private static final Key<Map<Integer, Integer>> COUNTER_LINE_NUMBER = new Key<>("PYDEV_COUNTER_LINE_NUMBER");
    private static final Logger LOG = Logger.getInstance(PythonConsoleView.class);
    private final boolean myTestMode;

    private PythonConsoleExecuteActionHandler myExecuteActionHandler;
    private PyConsoleSourceHighlighter mySourceHighlighter;
    private boolean myDebugConsole = false;
    private boolean myIsIPythonOutput;
    private final PyHighlighter myPyHighlighter;
    private boolean myHyperlink;

    private XStandaloneVariablesView mySplitView;
    private final ActionCallback myInitialized = new ActionCallback();
    private boolean isShowVars;
    private @Nullable String mySdkHomePath;
    private PydevConsoleRunner myRunner;

    private final Map<String, Map<String, PyDebugValueDescriptor>> myDescriptorsCache = new ConcurrentHashMap<>();

    private @Nullable PythonCommandQueuePanel myCommandQueuePanel;
    private @Nullable ConsoleCommunication myCommandQueueCommunication;
    private JBPopup myCommandQueue;
    private Dimension commandQueueDimension;
    private boolean isShowQueue;

    private ActionToolbar myToolbar;
    private boolean myIsToolwindowHorizontal = true;

    // TODO consulo: Target API (TargetEnvironment, PyTargetedCodeExecutor) is not ported, see setTargetEnvironment/executeCode(Function)

    /**
     * Context psi file for Python Console Editor. Needs for static code completion.
     */
    private final PsiFile myHistoryPsiFile;

    public PsiFile getHistoryPsiFile() {
        return myHistoryPsiFile;
    }

    private static Helper createHelper(Project project, String title) {
        return new Helper(
            project,
            Objects.requireNonNull(PsiUtilCore.getVirtualFile(new PyExpressionCodeFragmentImpl(project, title + ".py", "", true)))
        );
    }


    /**
     * @param testMode this console will be used to display test output and should support TC messages
     */
    public PythonConsoleView(Project project, String title, @Nullable Sdk sdk, boolean testMode) {
        super(createHelper(project, title));
        try {
            myHistoryPsiFile = new PyExpressionCodeFragmentImpl(project, "dummy.py", "", true);
            if (PsiUtilCore.getPsiFile(project, getVirtualFile()) instanceof PyExpressionCodeFragmentImpl codeFragment) {
                codeFragment.setContext(myHistoryPsiFile);
            }

            getConsoleEditor().setFile(getVirtualFile());
            myTestMode = testMode;
            isShowVars = PyConsoleOptions.getInstance(project).isShowVariableByDefault();
            VirtualFile virtualFile = getVirtualFile();
            PythonLanguageLevelPusher.specifyFileLanguageLevel(virtualFile, PythonSdkType.getLanguageLevelForSdk(sdk));
            virtualFile.putUserData(CONSOLE_KEY, true);
            // Mark editor as console one, to prevent autopopup completion if runtime completion is enabled
            if (PyConsoleOptions.getInstance(getProject()).isRuntimeCodeCompletion()) {
                getConsoleEditor().putUserData(PythonConsoleAutopopupBlockingHandler.REPL_KEY, new Object());
            }
            getHistoryViewer().putUserData(ConsoleViewUtil.EDITOR_IS_CONSOLE_HISTORY_VIEW, true);
            getHistoryViewer().putUserData(COUNTER_LINE_NUMBER, new HashMap<>());
            super.setPrompt(null);
            setUpdateFoldingsEnabled(false);
            LanguageLevel languageLevel = LanguageLevel.getDefault();
            if (sdk != null) {
                PythonSdkFlavor sdkFlavor = PythonSdkFlavor.getFlavor(sdk);
                if (sdkFlavor != null) {
                    languageLevel = sdkFlavor.getLanguageLevel(sdk);
                }
                mySdkHomePath = sdk.getHomePath();
            }
            myPyHighlighter = new PyHighlighter(languageLevel);
            addToolwindowPositionListener(project);
        }
        catch (Throwable e) {
            releaseHalfBuiltConsole(e);
            throw e;
        }
    }

    /**
     * Releases a console whose constructor did not finish.
     * The caller sees the first cause, so a failure of the release only adds itself to it.
     */
    private void releaseHalfBuiltConsole(Throwable cause) {
        try {
            Disposer.dispose(this);
        }
        catch (Throwable releaseError) {
            cause.addSuppressed(releaseError);
        }
    }

    public void setCommandQueueTitle(String title) {
        if (myCommandQueue != null) {
            myCommandQueue.setCaption(PyLocalize.pythonConsoleCommandQueueAddTitle(title).get());
        }
    }

    public void setConsoleCommunication(ConsoleCommunication communication) {
        getFile().putCopyableUserData(CONSOLE_COMMUNICATION_KEY, communication);

        if (isShowVars && communication instanceof PydevConsoleCommunication) {
            myIsToolwindowHorizontal = isToolwindowHorizontal(PythonConsoleToolWindow.getInstance(getProject()).getToolWindow());
            showVariables((PydevConsoleCommunication) communication);
        }

        if (communication instanceof PydevConsoleCommunication || communication instanceof PythonDebugConsoleCommunication) {
            myCommandQueueCommunication = communication;
            if (myCommandQueuePanel != null) {
                myCommandQueuePanel.setCommunication(communication);
            }
        }

        addCommandQueuePanelListener(communication);
    }

    public void addCommandQueuePanelListener(ConsoleCommunication communication) {
        CommandQueueForPythonConsoleService.getInstance(getProject())
            .addListener(communication, new CommandQueueListener() {
                @Override
                public void removeCommand(ConsoleCommunication.ConsoleCodeFragment command) {
                    Application.get().invokeLater(() -> {
                        if (myCommandQueuePanel != null) {
                            myCommandQueuePanel.removeCommand(command);
                        }
                    });
                }

                @Override
                public void addCommand(ConsoleCommunication.ConsoleCodeFragment command) {
                    getOrCreateCommandQueuePanel().addCommand(command);
                }

                @Override
                public void removeAll() {
                    if (myCommandQueuePanel != null) {
                        myCommandQueuePanel.removeAllCommands();
                    }
                }

                @Override
                public void disableConsole() {
                    if (myCommandQueuePanel != null) {
                        myCommandQueuePanel.removeAllCommands();
                    }
                    isShowQueue = false;
                    restoreQueueWindow(false);
                }
            });
    }

    /**
     * Add folding to Console view
     *
     * @param addOnce If true, folding will be added once when an appropriate area is found.
     *                Otherwise, folding can be expanded by newly added text.
     */
    private @Nullable PyConsoleStartFolding createConsoleFolding(boolean addOnce) {
        PyConsoleStartFolding startFolding = new PyConsoleStartFolding(this, addOnce);
        myExecuteActionHandler.getConsoleCommunication().addCommunicationListener(startFolding);
        Editor editor = getEditor();
        if (editor == null) {
            return null;
        }
        editor.getDocument().addDocumentListener(startFolding);
        editor.getFoldingModel().addListener(startFolding, this);
        return startFolding;
    }

    public void addConsoleFolding(boolean isDebugConsole, boolean addOnce) {
        try {
            if (isDebugConsole && myExecuteActionHandler != null && getEditor() != null) {
                PyConsoleStartFolding folding = createConsoleFolding(addOnce);
                if (folding != null) {
                    // in debug console we should add folding from the place where the folding was turned on
                    folding.setStartLineOffset(getEditor().getDocument().getTextLength());
                    folding.setNumberOfCommandToStop(2);
                }
            }
            else {
                myInitialized.doWhenDone(() -> createConsoleFolding(addOnce));
            }
        }
        catch (Exception e) {
            LOG.error(e.getMessage());
        }
    }

    public void setExecutionHandler(PythonConsoleExecuteActionHandler consoleExecuteActionHandler) {
        myExecuteActionHandler = consoleExecuteActionHandler;
    }

    public PythonConsoleExecuteActionHandler getExecuteActionHandler() {
        return myExecuteActionHandler;
    }

    public void setConsoleEnabled(boolean flag) {
        if (myExecuteActionHandler != null) {
            myExecuteActionHandler.setEnabled(flag);
        }
        else {
            myInitialized.doWhenDone(() -> myExecuteActionHandler.setEnabled(flag));
        }
    }

    public void inputRequested() {
        if (myExecuteActionHandler != null) {
            ConsoleCommunication consoleCommunication = myExecuteActionHandler.getConsoleCommunication();
            if (consoleCommunication instanceof PythonDebugConsoleCommunication) {
                consoleCommunication.notifyInputRequested();
            }
        }
    }

    public void inputReceived() {
        // If user's input was entered while debug console was turned off, we shouldn't wait for it anymore
        if (myExecuteActionHandler != null) {
            myExecuteActionHandler.getConsoleCommunication().notifyInputReceived();
        }
    }

    @Override
    public void requestFocus() {
        myInitialized.doWhenDone(() ->
            IdeFocusManager.getGlobalInstance().requestFocus(getConsoleEditor().getContentComponent(), true));
    }

    @Override
    public void executeCode(@Nullable String code, @Nullable Editor editor) {
        myInitialized.doWhenDone(() -> executeCodeImpl(code));
    }

    /**
     * Must be called when {@link #myInitialized} is resolved.
     *
     * @param code the code to be executed
     */
    private void executeCodeImpl(@Nullable String code) {
        if (code != null) {
            if (isCommandQueueEnabled()) {
                executeInConsole(code);
            }
            else {
                ProgressManager.getInstance().run(new Task.Backgroundable(null, PyLocalize.consoleExecutingCodeInConsole(), true) {
                    @Override
                    public void run(ProgressIndicator indicator) {
                        while (!myExecuteActionHandler.isEnabled() || !myExecuteActionHandler.canExecuteNow()) {
                            if (indicator.isCanceled()) {
                                break;
                            }
                            TimeoutUtil.sleep(300);
                        }
                        if (!indicator.isCanceled()) {
                            executeInConsole(code);
                        }
                    }
                });
            }
        }
        else {
            requestFocus();
        }
    }


    public void executeInConsole(String code) {
        CountDownLatch latch = new CountDownLatch(1);

        // TODO consulo: TransactionGuard missing, the transaction is run on the EDT (at once when already there)
        UIUtil.invokeLaterIfNeeded(() -> {
            String codeToExecute = code.endsWith("\n") || myExecuteActionHandler.checkSingleLine(code) ? code : code + "\n";
            Document document = getConsoleEditor().getDocument();
            String oldText = document.getText();
            Application.get().runWriteAction(() -> {
                setInputText(codeToExecute);
                PsiDocumentManager.getInstance(getProject()).commitDocument(document);
                PsiFile psiFile = PsiDocumentManager.getInstance(getProject()).getPsiFile(document);
                if (psiFile != null) {
                    CommandProcessor.getInstance().runUndoTransparentAction(
                        () -> CodeStyleManager.getInstance(getProject()).adjustLineIndent(psiFile, new TextRange(0, psiFile.getTextLength()))
                    );
                }
                int oldOffset = getConsoleEditor().getCaretModel().getOffset();
                getConsoleEditor().getCaretModel().moveToOffset(document.getTextLength());
                myExecuteActionHandler.runExecuteAction(this);

                if (!StringUtil.isEmpty(oldText)) {
                    Application.get().runWriteAction(() -> setInputText(oldText));
                    getConsoleEditor().getCaretModel().moveToOffset(oldOffset);
                }
            });

            latch.countDown();
        });

        try {
            latch.await(1, TimeUnit.MINUTES);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Marks this view as the Debug Console one. The Debug Console keeps its own settings, see PY-91913.
     */
    public void markAsDebugConsole() {
        myDebugConsole = true;
    }

    /**
     * Whether the command queue applies to this console. The Debug Console and the Python Console are configured
     * separately, and this class serves both.
     */
    public boolean isCommandQueueEnabled() {
        return myDebugConsole
            ? PyDebuggerOptionsProvider.getInstance(getProject()).isDebugConsoleCommandQueueEnabled()
            : PyConsoleOptions.getInstance(getProject()).isCommandQueueEnabled();
    }

    public void executeStatement(String statement, Key<?> attributes) {
        print(statement, outputTypeForAttributes(attributes));
        myExecuteActionHandler.processLine(statement);
    }

    /**
     * Executes {@code statement} and shows it with Python syntax highlighting, the way executed input looks.
     * <p>
     * Used for the Debug Console start script: it is code, so it should not read as plain system output.
     */
    public void executeStatementWithHighlighting(String statement) {
        new PyConsoleSourceHighlighter(this, myPyHighlighter).printHighlightedSource(statement);
        myExecuteActionHandler.processLine(statement);
    }

    public void printText(String text, ConsoleViewContentType outputType) {
        super.print(text, outputType);
    }

    public void print(String text, Key<?> attributes) {
        print(text, outputTypeForAttributes(attributes));
    }

    @Override
    public void print(String text, ConsoleViewContentType outputType) {
        if (myTestMode) {
            // TODO consulo: TeamCity ServiceMessage parser missing (PyTestsShared.processTCMessage)
        }
        detectIPython(text);
        if (PyConsoleUtil.detectIPythonEnd(text)) {
            myIsIPythonOutput = false;
            mySourceHighlighter = null;
        }
        else if (PyConsoleUtil.detectIPythonStart(text)) {
            myIsIPythonOutput = true;
        }
        else {
            if (mySourceHighlighter == null || outputType == ConsoleViewContentType.ERROR_OUTPUT) {
                if (myHyperlink) {
                    printHyperlink(text, outputType);
                }
                else {
                    //Print text normally with converted attributes
                    super.print(text, outputType);
                }
                myHyperlink = detectHyperlink(text);
                if (mySourceHighlighter == null && myIsIPythonOutput && PyConsoleUtil.detectSourcePrinting(text)) {
                    mySourceHighlighter = new PyConsoleSourceHighlighter(this, myPyHighlighter);
                }
            }
            else {
                try {
                    mySourceHighlighter.printHighlightedSource(text);
                }
                catch (Exception e) {
                    LOG.error(e);
                }
            }
        }
    }

    public void detectIPython(String text) {
        VirtualFile file = getVirtualFile();
        if (PyConsoleUtil.detectIPythonImported(text)) {
            PyConsoleUtil.markIPython(file);
            PythonConsoleExecuteActionHandler handler = getExecuteActionHandler();
            if (handler != null) {
                handler.updateConsoleState();
            }
        }
        if (PyConsoleUtil.detectIPythonAutomagicOn(text)) {
            PyConsoleUtil.setIPythonAutomagic(file, true);
        }
        if (PyConsoleUtil.detectIPythonAutomagicOff(text)) {
            PyConsoleUtil.setIPythonAutomagic(file, false);
        }
    }

    private boolean detectHyperlink(String text) {
        return myIsIPythonOutput && text.startsWith("File:");
    }

    private void printHyperlink(String text, ConsoleViewContentType contentType) {
        if (!StringUtil.isEmpty(text)) {
            VirtualFile vFile = LocalFileSystem.getInstance().findFileByPath(text.trim());

            if (vFile != null) {
                OpenFileHyperlinkInfo hyperlink = new OpenFileHyperlinkInfo(getProject(), vFile, -1);

                super.printHyperlink(text, hyperlink);
            }
            else {
                super.print(text, contentType);
            }
        }
    }

    public ConsoleViewContentType outputTypeForAttributes(Key<?> attributes) {
        ConsoleViewContentType outputType;
        if (attributes == ProcessOutputTypes.STDERR) {
            outputType = ConsoleViewContentType.ERROR_OUTPUT;
        }
        else if (isSystem(attributes)) {
            outputType = ConsoleViewContentType.SYSTEM_OUTPUT;
        }
        else {
            outputType = ConsoleViewContentType.getConsoleViewType(attributes);
        }

        return outputType;
    }

    // Consulo-specific: ProcessOutputType.isSystem(Key) is missing in consulo-process-api
    private static boolean isSystem(Key<?> key) {
        ProcessOutputType outputType = ProcessOutputType.tryCast(key);
        return key == ProcessOutputTypes.SYSTEM || outputType != null && outputType.getBaseOutputType() == ProcessOutputType.SYSTEM;
    }

    public void setSdk(Sdk sdk) {
        getFile().putCopyableUserData(PydevConsoleRunner.CONSOLE_SDK, sdk);
    }

    public void showVariables(PydevConsoleCommunication consoleCommunication) {
        Project project = getProject();
        PyStackFrame stackFrame = new PyStackFrame(project, consoleCommunication, new PyStackFrameInfo("", "", "", null), null);
        stackFrame.restoreChildrenDescriptors(myDescriptorsCache);
        XStandaloneVariablesView view = project.getInstance(XStandaloneVariablesViewFactory.class)
            .create(new PyDebuggerEditorsProvider(), stackFrame);
        consoleCommunication.addCommunicationListener(new ConsoleCommunicationListener() {
            @Override
            public void commandExecuted(boolean more) {
                view.rebuildView();
            }

            @Override
            public void inputRequested() {
            }
        });
        mySplitView = view;

        // TODO consulo: XStandaloneVariablesView.getMainComponent() missing, the scroll pane border is not reset

        Disposer.register(this, view);
        splitWindow();
        consoleCommunication.notifyViewCreated(view);
    }

    private static boolean isToolwindowHorizontal(ToolWindow toolWindow) {
        return toolWindow.getAnchor() == ToolWindowAnchor.BOTTOM || toolWindow.getAnchor() == ToolWindowAnchor.TOP;
    }

    private void addToolwindowPositionListener(Project project) {
        project.getMessageBus().connect(this).subscribe(ToolWindowManagerListener.class, new ToolWindowManagerListener() {
            @Override
            public void stateChanged(ToolWindowManager toolWindowManager) {
                ToolWindow consoleToolWindow = PythonConsoleToolWindow.getInstance(project).getToolWindow();
                if (myIsToolwindowHorizontal != isToolwindowHorizontal(consoleToolWindow)) {
                    myIsToolwindowHorizontal = !myIsToolwindowHorizontal;
                    if (isShowVars) {
                        restoreWindow();
                        ConsoleCommunication communication = getFile().getCopyableUserData(CONSOLE_COMMUNICATION_KEY);
                        if (communication instanceof PydevConsoleCommunication) {
                            showVariables((PydevConsoleCommunication) communication);
                        }
                    }
                }
            }
        });
    }

    /**
     * Builds the command-queue panel the first time something needs it.
     */
    private PythonCommandQueuePanel getOrCreateCommandQueuePanel() {
        if (myCommandQueuePanel == null) {
            myCommandQueuePanel = new PythonCommandQueuePanel(this);
            if (myCommandQueueCommunication != null) {
                myCommandQueuePanel.setCommunication(myCommandQueueCommunication);
            }
        }
        return myCommandQueuePanel;
    }

    //the main function for drawing the queue
    public void showQueue() {
        JBPopupListener listener = new JBPopupListener() {

            @Override
            public void onClosed(LightweightWindowEvent event) {
                // Consulo-specific: AbstractPopup.addResizeListener is not in the API, the size is remembered on close
                if (myCommandQueue != null) {
                    commandQueueWasResized();
                }
                isShowQueue = false;
            }
        };
        String commandQueueName = getConsoleDisplayName(getProject());
        myCommandQueue = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(getOrCreateCommandQueuePanel(), null)
            .setMovable(true)
            .setResizable(true)
            .setShowShadow(true)
            .setCancelOnClickOutside(false)
            .setTitle(PyLocalize.pythonConsoleCommandQueueAddTitle(
                commandQueueName != null ? commandQueueName : "Python Console"))
            .setCancelButton(PlatformIconGroup.generalHidetoolwindow(), PyLocalize.tooltipHide())
            .addListener(listener)
            .setBorderColor(JBColor.background())
            .setCancelOnOtherWindowOpen(true)
            .createPopup();

        // TODO consulo: TitlePanel/AbstractPopup are not in the API, the title label foreground is not adjusted

        if (commandQueueDimension != null) {
            myCommandQueue.setSize(commandQueueDimension);
        }
        var editor = getConsoleEditor();
        // Consulo-specific: UIUtil.isShowing(Component) is not in the API
        if (editor.getContentComponent().isShowing()) {
            myCommandQueue.showInBestPositionFor(DataManager.getInstance().getDataContext(getConsoleEditor().getContentComponent()));
        }

        Disposer.register(this, myCommandQueue);
    }

    private void commandQueueWasResized() {
        commandQueueDimension = myCommandQueue.getSize();
    }

    @Override
    protected JComponent createCenterComponent() {
        //workaround for extra lines appearing in the console
        JComponent centerComponent = super.createCenterComponent();
        getHistoryViewer().getSettings().setAdditionalLinesCount(0);
        getHistoryViewer().getSettings().setUseSoftWraps(false);
        getConsoleEditor().getGutterComponentEx().getComponent().setBackground(TargetAWT.to(getConsoleEditor().getBackgroundColor()));
        getConsoleEditor().getGutterComponentEx().getComponent().revalidate();
        getConsoleEditor().getColorsScheme().setColor(EditorColors.GUTTER_BACKGROUND, getConsoleEditor().getBackgroundColor());

        // settings.set
        return centerComponent;
    }

    private void splitWindow() {
        Component console = getComponent(0);
        removeAll();
        OnePixelSplitter p = new OnePixelSplitter(!myIsToolwindowHorizontal, 2f / 3);
        p.setFirstComponent((JComponent) console);
        p.setSecondComponent(mySplitView.getComponent());
        p.setShowDividerControls(true);
        p.setHonorComponentsMinimumSize(true);

        add(p, BorderLayout.CENTER);
        validate();
        repaint();
    }

    // helper function for drawing the CommandQueue
    public void restoreQueueWindow(boolean removeCommand) {
        if (myCommandQueue != null) {
            Application.get().invokeLater(() -> Disposer.dispose(myCommandQueue));
        }
        if (removeCommand) {
            CommandQueueForPythonConsoleService.getInstance(getProject())
                .removeCommand(myRunner.getPydevConsoleCommunication(), true);
        }
    }

    public void restoreWindow() {
        Component component = getComponent(0);
        if (mySplitView != null && component instanceof JBSplitter pane) {
            removeAll();
            Disposer.dispose(mySplitView);
            mySplitView = null;
            add(pane.getFirstComponent(), BorderLayout.CENTER);
            validate();
            repaint();
        }
    }

    public @Nullable String getSdkHomePath() {
        return mySdkHomePath;
    }

    public boolean isInitialized() {
        return myInitialized.isDone();
    }

    public void initialized() {
        myInitialized.setDone();
    }

    public boolean isShowVars() {
        return isShowVars;
    }

    public void setShowVars(boolean showVars) {
        isShowVars = showVars;
    }

    public boolean isShowQueue() {
        return isShowQueue;
    }

    public void setShowQueue(boolean showQueue) {
        isShowQueue = showQueue;
    }

    public void setRunner(PydevConsoleRunner runner) {
        myRunner = runner;
    }

    public PydevConsoleRunner getRunner() {
        return myRunner;
    }

    @Override
    public void dispose() {
        super.dispose();

        Project project = getProject();
        if (!project.isDisposed()) {
            ConsoleCommunication communication = getFile().getCopyableUserData(CONSOLE_COMMUNICATION_KEY);
            if (communication != null) {
                CommandQueueForPythonConsoleService.getInstance(project).removeListener(communication);
            }
        }

        commandQueueDimension = null;
        if (myCommandQueuePanel != null) {
            var editor = myCommandQueuePanel.getQueueEditor();
            if (!editor.isDisposed()) {
                EditorFactory.getInstance().releaseEditor(editor);
            }
        }
    }

    public void setToolbar(ActionToolbar toolbar) {
        myToolbar = toolbar;
    }

    public ActionToolbar getToolbar() {
        return myToolbar;
    }

    // needs for correctly display the queue's name after renaming
    private static @Nullable String getConsoleDisplayName(Project project) {
        ToolWindow window = PythonConsoleToolWindow.getInstance(project).getToolWindow();
        Content content = window.getContentManager().getSelectedContent();
        if (content == null) {
            return null;
        }
        return content.getDisplayName();
    }

    public void storeExecutionCounterLineNumber(int counter, int lineNumber) {
        Map<Integer, Integer> counterMap = getHistoryViewer().getUserData(COUNTER_LINE_NUMBER);
        if (counterMap != null) {
            counterMap.put(counter, lineNumber);
        }
    }
}
