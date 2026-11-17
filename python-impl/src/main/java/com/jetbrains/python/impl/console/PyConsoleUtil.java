// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.console;

import com.jetbrains.python.console.pydev.ConsoleCommunication;
import com.jetbrains.python.impl.console.actions.CommandQueueForPythonConsoleService;
import com.jetbrains.python.parsing.console.PythonConsoleData;
import consulo.application.Application;
import consulo.codeEditor.Editor;
import consulo.codeEditor.EditorEx;
import consulo.document.Document;
import consulo.document.util.TextRange;
import consulo.language.editor.completion.lookup.LookupManager;
import consulo.language.psi.PsiFile;
import consulo.process.ProcessOutputTypes;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.ActionManager;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.AnActionWithSyncUpdate;
import consulo.ui.ex.action.ActionUpdateThread;
import consulo.ui.ex.action.IdeActions;
import consulo.ui.ex.awt.IJSwingUtilities;
import consulo.undoRedo.CommandProcessor;
import consulo.util.dataholder.Key;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

public final class PyConsoleUtil {
    public static final String ORDINARY_PROMPT = ">>>";
    public static final String INPUT_PROMPT = ">?";
    public static final String INDENT_PROMPT = "...";
    public static final String IPYTHON_INDENT_PROMPT = "...:";

    public static final String HELP_PROMPT = "help>";
    public static final String EXECUTING_PROMPT = "";

    private static final String IPYTHON_PAGING_PROMPT = "---Return to continue, q to quit---";

    public static final String ASYNCIO_REPL_ENV = "ASYNCIO_REPL";


    public static final Key<PythonConsoleData> PYTHON_CONSOLE_DATA = Key.create("python-console-data");

    private PyConsoleUtil() {
    }

    public static boolean isPagingPrompt(@Nullable String prompt) {
        return prompt != null && IPYTHON_PAGING_PROMPT.equals(prompt.trim());
    }


    public static void scrollDown(Editor currentEditor) {
        Application.get().invokeLater(
            () -> {
                if (!currentEditor.isDisposed()) {
                    currentEditor.getCaretModel().moveToOffset(currentEditor.getDocument().getTextLength());
                }
            }
        );
    }


    public static boolean detectIPythonImported(String text) {
        return text.contains("PyDev console: using IPython ");
    }

    public static boolean detectSourcePrinting(String text) {
        return text.contains("Source:");
    }

    public static boolean detectIPythonStart(String text) {
        return text.contains("IPython-->");
    }

    public static boolean detectIPythonEnd(String text) {
        return text.contains("<--IPython");
    }

    public static boolean detectIPythonAutomagicOn(String text) {
        return text.contains("Automagic is ON, % prefix NOT needed for magic functions.");
    }

    public static boolean detectIPythonAutomagicOff(String text) {
        return text.contains("Automagic is OFF, % prefix IS needed for magic functions.");
    }

    public static void markIPython(VirtualFile file) {
        PythonConsoleData consoleData = getOrCreateIPythonData(file);
        consoleData.setIPythonEnabled(true);
    }

    public static PythonConsoleData getOrCreateIPythonData(VirtualFile file) {
        PythonConsoleData consoleData = file.getUserData(PYTHON_CONSOLE_DATA);
        if (consoleData == null) {
            consoleData = new PythonConsoleData();
            file.putUserData(PYTHON_CONSOLE_DATA, consoleData);
        }
        return consoleData;
    }

    public static void setIPythonAutomagic(VirtualFile file, boolean detected) {
        PythonConsoleData consoleData = getOrCreateIPythonData(file);
        consoleData.setIPythonAutomagic(detected);
    }

    /**
     * Returns {@code true} if code completion must not be offered in the console input editor backed by {@code consoleFile}.
     * <p>
     * That is the case while the console is waiting for the input of the running program (the {@link #INPUT_PROMPT} prompt):
     * the typed text is passed to the stdin of the process as is, so it is not Python code and completing it would replace
     * what the user has typed.
     */
    public static boolean isCodeCompletionSuppressed(@Nullable PsiFile consoleFile) {
        if (consoleFile == null) {
            return false;
        }
        ConsoleCommunication communication = consoleFile.getCopyableUserData(PydevConsoleRunner.CONSOLE_COMMUNICATION_KEY);
        return communication != null && communication.isWaitingForInput();
    }

    public static AnAction createTabCompletionAction(PythonConsoleView consoleView) {
        // Consulo: a local class, because an action with update() must implement AnActionWithSyncUpdate
        class TabCompletionAction extends AnAction implements AnActionWithSyncUpdate {
            @Override
            @RequiredUIAccess
            public void actionPerformed(AnActionEvent e) {
                Editor editor = consoleView.getConsoleEditor();
                if (LookupManager.getActiveLookup(editor) != null) {
                    AnAction replace = ActionManager.getInstance().getAction(IdeActions.ACTION_CHOOSE_LOOKUP_ITEM_REPLACE);
                    // TODO consulo: ActionUtil.performAction missing in consulo-ui-ex-api
                    replace.actionPerformed(e);
                    return;
                }
                AnAction completionAction = ActionManager.getInstance().getAction(IdeActions.ACTION_CODE_COMPLETION);
                if (completionAction != null) {
                    // TODO consulo: ActionUtil.performAction missing in consulo-ui-ex-api
                    completionAction.actionPerformed(e);
                }
            }

            @Override
            @RequiredUIAccess
            public void update(AnActionEvent e) {
                e.getPresentation().setVisible(false);
                Editor editor = consoleView.getConsoleEditor();
                if (LookupManager.getActiveLookup(editor) != null) {
                    e.getPresentation().setEnabled(false);
                }
                if (isCodeCompletionSuppressed(consoleView.getFile())) {
                    // let the editor insert the tabulation character instead, the typed text goes to the stdin of the program
                    e.getPresentation().setEnabled(false);
                    return;
                }
                int offset = editor.getCaretModel().getOffset();
                Document document = editor.getDocument();
                int lineStart = document.getLineStartOffset(document.getLineNumber(offset));
                String textToCursor = document.getText(new TextRange(lineStart, offset));
                e.getPresentation().setEnabled(!textToCursor.isBlank());
            }

            @Override
            @SuppressWarnings("deprecation")
            public ActionUpdateThread getActionUpdateThread() {
                return ActionUpdateThread.EDT;
            }
        }
        AnAction runCompletions = new TabCompletionAction();

        runCompletions.registerCustomShortcutSet(KeyEvent.VK_TAB, 0, consoleView.getConsoleEditor().getComponent());
        return runCompletions;
    }

    public static AnAction createInterruptAction(PythonConsoleView consoleView) {
        // Consulo: a local class, because an action with update() must implement AnActionWithSyncUpdate
        class InterruptAction extends AnAction implements AnActionWithSyncUpdate {
            @Override
            @RequiredUIAccess
            public void actionPerformed(AnActionEvent e) {
                ConsoleCommunication consoleCommunication = consoleView.getExecuteActionHandler().getConsoleCommunication();
                if (consoleCommunication.isExecuting() || consoleCommunication.isWaitingForInput()) {
                    consoleView.print("^C", ProcessOutputTypes.SYSTEM);
                    consoleCommunication.interrupt();
                }
                else {
                    Document document = consoleView.getConsoleEditor().getDocument();
                    if (document.getTextLength() != 0) {
                        Application.get().runWriteAction(() ->
                            CommandProcessor.getInstance()
                                .runUndoTransparentAction(() -> document.deleteString(
                                    0,
                                    document.getLineEndOffset(document.getLineCount() - 1)
                                )));
                    }
                }
            }

            @Override
            @RequiredUIAccess
            public void update(AnActionEvent e) {
                e.getPresentation().setVisible(false);
                boolean enabled = false;
                EditorEx consoleEditor = consoleView.getConsoleEditor();
                if (IJSwingUtilities.hasFocus(consoleEditor.getComponent())) {
                    enabled = !consoleEditor.getSelectionModel().hasSelection();
                }
                EditorEx historyViewer = consoleView.getHistoryViewer();
                if (IJSwingUtilities.hasFocus(historyViewer.getComponent())) {
                    enabled = !historyViewer.getSelectionModel().hasSelection();
                }
                e.getPresentation().setEnabled(enabled);
            }

            @Override
            @SuppressWarnings("deprecation")
            public ActionUpdateThread getActionUpdateThread() {
                return ActionUpdateThread.EDT;
            }
        }
        AnAction anAction = new InterruptAction();

        anAction.registerCustomShortcutSet(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK, consoleView.getConsoleEditor().getComponent());
        anAction.registerCustomShortcutSet(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK, consoleView.getHistoryViewer().getComponent());
        return anAction;
    }

    public static boolean isCommandQueueEmpty(Project project, @Nullable ConsoleCommunication communication) {
        if (communication != null) {
            return CommandQueueForPythonConsoleService.getInstance(project).isEmpty(communication);
        }
        return true;
    }
}
