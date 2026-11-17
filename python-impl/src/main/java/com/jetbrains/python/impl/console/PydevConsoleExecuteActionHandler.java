// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.console;

import com.jetbrains.python.PyLanguageFacade;
import com.jetbrains.python.console.pydev.ConsoleCommunication;
import com.jetbrains.python.console.pydev.ConsoleCommunicationListener;
import com.jetbrains.python.impl.console.actions.CommandQueueForPythonConsoleService;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyStatementList;
import consulo.application.Application;
import consulo.colorScheme.EditorColorsManager;
import consulo.colorScheme.TextAttributes;
import consulo.document.Document;
import consulo.document.RangeMarker;
import consulo.execution.ui.console.ConsoleViewContentType;
import consulo.execution.ui.console.language.LanguageConsoleView;
import consulo.ide.impl.idea.execution.console.LanguageConsoleImpl;
import consulo.language.editor.hint.HintManager;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.process.ProcessHandler;
import consulo.project.Project;
import consulo.undoRedo.CommandProcessor;
import consulo.util.lang.StringUtil;

import java.awt.*;

public class PydevConsoleExecuteActionHandler extends PythonConsoleExecuteActionHandler implements ConsoleCommunicationListener {
    private final LanguageConsoleView myConsoleView;
    private final ConsoleCommunication myConsoleCommunication;

    private final Project project;

    private final PyConsoleEnterHandler myEnterHandler = new PyConsoleEnterHandler();
    private int myIpythonInputPromptCount = 2;

    private boolean myEnabled = false;

    public PydevConsoleExecuteActionHandler(
        LanguageConsoleView consoleView,
        ProcessHandler processHandler,
        ConsoleCommunication consoleCommunication
    ) {
        super(processHandler, false);
        myConsoleView = consoleView;
        myConsoleCommunication = consoleCommunication;
        project = myConsoleView.getProject();
        myConsoleCommunication.addCommunicationListener(this);
    }

    @Override
    public final ConsoleCommunication getConsoleCommunication() {
        return myConsoleCommunication;
    }

    /**
     * Whether the command queue applies to this console. The Debug Console overrides it: the two consoles are
     * configured separately, see PY-91913.
     */
    protected boolean isCommandQueueEnabled() {
        return PyConsoleOptions.getInstance(project).isCommandQueueEnabled();
    }

    protected int getIpythonInputPromptCount() {
        return myIpythonInputPromptCount;
    }

    protected void setIpythonInputPromptCount(int ipythonInputPromptCount) {
        myIpythonInputPromptCount = ipythonInputPromptCount;
    }

    public void decreaseInputPromptCount(int value) {
        setIpythonInputPromptCount(getIpythonInputPromptCount() - value);
    }

    @Override
    public boolean isEnabled() {
        return myEnabled;
    }

    @Override
    public void setEnabled(boolean value) {
        myEnabled = value;
        updateConsoleState();
    }

    @Override
    public void processLine(String line) {
        executeMultiLine(line);
    }

    private void executeMultiLine(String text) {
        String commandText;
        if (!text.endsWith("\n")) {
            commandText = text + "\n";
        }
        else {
            commandText = text;
        }
        sendLineToConsole(new ConsoleCommunication.ConsoleCodeFragment(commandText, checkSingleLine(text)));
    }

    @Override
    public boolean checkSingleLine(String text) {
        var languageLevel = PyLanguageFacade.getInstance().getEffectiveLanguageLevel(project, myConsoleView.getVirtualFile());
        PyFile pyFile = (PyFile) PyElementGenerator.getInstance(project).createDummyFile(languageLevel, text);
        return PsiTreeUtil.findChildOfAnyType(pyFile, PyStatementList.class) == null && pyFile.getStatements().size() < 2;
    }

    private void sendLineToConsole(ConsoleCommunication.ConsoleCodeFragment code) {
        ConsoleCommunication consoleComm = getConsoleCommunication();
        if (!consoleComm.isWaitingForInput()) {
            executingPrompt();
        }
        if (isIpythonEnabled() && !consoleComm.isWaitingForInput() && !code.getText().isBlank()) {
            setIpythonInputPromptCount(getIpythonInputPromptCount() + 1);
        }
        if (isCommandQueueEnabled()) {
            // add new command to CommandQueue service
            CommandQueueForPythonConsoleService.getInstance(project).addNewCommand(this, code);
        }
        else {
            consoleComm.execInterpreter(code, it -> null);
        }
    }

    @Override
    public void updateConsoleState() {
        if (!isEnabled()) {
            executingPrompt();
        }
        else if (getConsoleCommunication().isWaitingForInput()) {
            waitingForInputPrompt();
        }
        else if (canExecuteNow()) {
            if (getConsoleCommunication().needsMore()) {
                more();
            }
            else {
                inPrompt();
            }
        }
        else {
            if (isCommandQueueEnabled()) {
                inPrompt();
            }
            else {
                executingPrompt();
            }
        }
    }

    private void inPrompt() {
        if (isIpythonEnabled()) {
            ipythonInPrompt();
        }
        else {
            ordinaryPrompt();
        }
    }

    private void ordinaryPrompt() {
        if (!PyConsoleUtil.ORDINARY_PROMPT.equals(myConsoleView.getPrompt())) {
            myConsoleView.setPrompt(PyConsoleUtil.ORDINARY_PROMPT);
            setIndentPrompt(myConsoleView, PyConsoleUtil.INDENT_PROMPT);
            PyConsoleUtil.scrollDown(myConsoleView.getCurrentEditor());
        }
    }

    private boolean isIpythonEnabled() {
        return PyConsoleUtil.getOrCreateIPythonData(myConsoleView.getVirtualFile()).isIPythonEnabled();
    }

    protected void ipythonInPrompt() {
        ipythonInPrompt("In [" + getIpythonInputPromptCount() + "]:");
    }

    protected void ipythonInPrompt(String prompt) {
        myConsoleView.setPromptAttributes(new ConsoleViewContentType("", new TextAttributes()) {
            @Override
            public TextAttributes getAttributes() {
                TextAttributes attrs = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(USER_INPUT_KEY);
                attrs.setFontType(Font.PLAIN);
                return attrs;
            }
        });

        String indentPrompt = StringUtil.repeatSymbol(' ', Math.max(0, prompt.length() - PyConsoleUtil.IPYTHON_INDENT_PROMPT.length()))
            + PyConsoleUtil.IPYTHON_INDENT_PROMPT;
        myConsoleView.setPrompt(prompt);
        setIndentPrompt(myConsoleView, indentPrompt);
        PyConsoleUtil.scrollDown(myConsoleView.getCurrentEditor());
    }

    private void executingPrompt() {
        myConsoleView.setPrompt(PyConsoleUtil.EXECUTING_PROMPT);
        setIndentPrompt(myConsoleView, PyConsoleUtil.EXECUTING_PROMPT);
    }

    private void waitingForInputPrompt() {
        if (!PyConsoleUtil.INPUT_PROMPT.equals(myConsoleView.getPrompt())
            && !PyConsoleUtil.HELP_PROMPT.equals(myConsoleView.getPrompt())) {
            myConsoleView.setPrompt(PyConsoleUtil.INPUT_PROMPT);
            setIndentPrompt(myConsoleView, PyConsoleUtil.INPUT_PROMPT);
            PyConsoleUtil.scrollDown(myConsoleView.getCurrentEditor());
        }
    }

    private void more() {
        String prompt;
        if (isIpythonEnabled()) {
            prompt = PyConsoleUtil.IPYTHON_INDENT_PROMPT;
        }
        else {
            prompt = PyConsoleUtil.INDENT_PROMPT;
        }
        if (!prompt.equals(myConsoleView.getPrompt())) {
            myConsoleView.setPrompt(prompt);
            setIndentPrompt(myConsoleView, prompt);
            PyConsoleUtil.scrollDown(myConsoleView.getCurrentEditor());
        }
    }

    @Override
    public void commandExecuted(boolean more) {
        updateConsoleState();
    }

    @Override
    public void inputRequested() {
        setEnabled(true);
    }

    @Override
    public String getCantExecuteMessage() {
        if (!isEnabled()) {
            return getConsoleIsNotEnabledMessage();
        }
        else if (!canExecuteNow()) {
            return getPrevCommandRunningMessage();
        }
        else {
            return "Can't execute the command";
        }
    }

    @Override
    public void runExecuteAction(LanguageConsoleView console) {
        if (isEnabled()) {
            if (isCommandQueueEnabled()) {
                doRunExecuteAction(console);
            }
            else {
                if (!canExecuteNow()) {
                    HintManager.getInstance().showErrorHint(console.getConsoleEditor(), getPrevCommandRunningMessage());
                }
                else {
                    doRunExecuteAction(console);
                }
            }
        }
        else {
            HintManager.getInstance().showErrorHint(console.getConsoleEditor(), getConsoleIsNotEnabledMessage());
        }
    }

    private void doRunExecuteAction(LanguageConsoleView console) {
        Document doc = myConsoleView.getEditorDocument();
        RangeMarker endMarker = doc.createRangeMarker(doc.getTextLength(), doc.getTextLength());
        endMarker.setGreedyToLeft(false);
        endMarker.setGreedyToRight(true);
        boolean isComplete = myEnterHandler.handleEnterPressed(console.getConsoleEditor());
        if (isComplete || getConsoleCommunication().isWaitingForInput()) {
            deleteString(doc, endMarker);
            if (shouldCopyToHistory(console)) {
                if (console instanceof PythonConsoleView pythonConsole) {
                    pythonConsole.flushDeferredText();
                    pythonConsole.storeExecutionCounterLineNumber(
                        getIpythonInputPromptCount(),
                        pythonConsole.getHistoryViewer().getDocument().getLineCount()
                            + console.getConsoleEditor().getDocument().getLineCount()
                    );
                }
                copyToHistoryAndExecute(console);
            }
            else {
                processLine(myConsoleView.getConsoleEditor().getDocument().getText());
            }
        }
    }

    private void copyToHistoryAndExecute(LanguageConsoleView console) {
        super.runExecuteAction(console);
    }

    @Override
    public boolean canExecuteNow() {
        return !getConsoleCommunication().isExecuting() || getConsoleCommunication().isWaitingForInput();
    }

    protected String getConsoleIsNotEnabledMessage() {
        return getNotEnabledMessage();
    }

    public static String getPrevCommandRunningMessage() {
        return "Previous command is still running. Please wait or press Ctrl+C in console to interrupt.";
    }

    public static String getNotEnabledMessage() {
        return "Console is not enabled.";
    }

    private static boolean shouldCopyToHistory(LanguageConsoleView console) {
        return !PyConsoleUtil.isPagingPrompt(console.getPrompt());
    }

    public static void deleteString(Document document, RangeMarker endMarker) {
        if (endMarker.getEndOffset() - endMarker.getStartOffset() > 0) {
            Application.get().runWriteAction(() -> {
                CommandProcessor.getInstance().runUndoTransparentAction(() -> {
                    document.deleteString(endMarker.getStartOffset(), endMarker.getEndOffset());
                });
            });
        }
    }

    private static void setIndentPrompt(LanguageConsoleView consoleView, String value) {
        if (consoleView instanceof LanguageConsoleImpl languageConsole) {
            languageConsole.getConsolePromptDecorator().setIndentPrompt(value);
        }
    }
}
