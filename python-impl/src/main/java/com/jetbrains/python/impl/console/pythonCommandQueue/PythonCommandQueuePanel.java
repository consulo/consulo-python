// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.console.pythonCommandQueue;

import com.jetbrains.python.console.pydev.ConsoleCommunication;
import com.jetbrains.python.impl.console.PythonConsoleView;
import com.jetbrains.python.impl.console.PythonDebugConsoleCommunication;
import com.jetbrains.python.impl.console.actions.CommandQueueForPythonConsoleService;
import com.jetbrains.python.impl.console.actions.ShowCommandQueueAction;
import consulo.application.Application;
import consulo.codeEditor.Editor;
import consulo.codeEditor.EditorEx;
import consulo.codeEditor.EditorFactory;
import consulo.document.Document;
import consulo.document.FileDocumentManager;
import consulo.language.Language;
import consulo.language.editor.WriteCommandAction;
import consulo.language.editor.highlight.LexerEditorHighlighter;
import consulo.language.editor.highlight.SyntaxHighlighter;
import consulo.language.editor.highlight.SyntaxHighlighterFactory;
import consulo.language.file.light.LightVirtualFile;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiFileFactory;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.ui.ex.JBColor;
import consulo.ui.ex.action.ActionButton;
import consulo.ui.ex.awt.JBScrollPane;
import consulo.ui.ex.awt.JBSplitter;
import consulo.ui.ex.awt.JBUI;
import consulo.ui.ex.awt.UIUtil;
import consulo.ui.ex.awt.VerticalLayout;
import consulo.ui.image.Image;
import consulo.undoRedo.util.UndoUtil;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;

import javax.swing.*;
import java.awt.*;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Main panel for PopupWindow (CommandQueue)
 */
public final class PythonCommandQueuePanel extends JPanel {
    private final JPanel myPanel = new JPanel();
    private final JBSplitter mySplitter;
    private final PythonConsoleView myConsole;
    private final EditorEx myQueueEditor;
    private final Map<ConsoleCommunication.ConsoleCodeFragment, QueueElementPanel> myQueueElementPanelMap = new ConcurrentHashMap<>();
    private final CommandQueueForPythonConsoleService myService;


    private QueueElementPanel selectedCommand;
    private ConsoleCommunication communication;

    private static final int PREFERED_WIDTH = 500;
    private static final int PREFERED_HEIGHT = 150;
    private static final int BORDER_OFFSETS = 7;


    public PythonCommandQueuePanel(PythonConsoleView console) {
        setLayout(new GridLayout());
        setBorder(JBUI.Borders.empty());
        setPreferredSize(new Dimension(PREFERED_WIDTH, PREFERED_HEIGHT));
        myConsole = console;
        myService = CommandQueueForPythonConsoleService.getInstance(console.getProject());
        // TODO consulo: GotItTooltip missing (python.console.command.queue.got.it.tooltip.*)
        mySplitter = new JBSplitter(true);
        mySplitter.setSplitterProportionKey(PyLocalize.pythonConsoleCommandQueueAddTitle(getClass().getName()).get());
        mySplitter.setOrientation(false);
        mySplitter.setBackground(JBColor.background());
        JBScrollPane scrollPane = new JBScrollPane(myPanel);
        //scrollPane.setMinimumSize(new Dimension(QUEUE_MINIMUM_WIDTH, QUEUE_MINIMUM_HEIGHT));
        mySplitter.setFirstComponent(scrollPane);
        myQueueEditor = createEmptyEditor();
        mySplitter.setSecondComponent(myQueueEditor.getComponent());
        add(mySplitter);

        myPanel.setLayout(new VerticalLayout(0));
        myPanel.setBorder(JBUI.Borders.empty(BORDER_OFFSETS));
        myPanel.setBackground(JBColor.background());

        repaintAll();
    }

    public void addCommand(ConsoleCommunication.ConsoleCodeFragment command) {
        // TODO consulo: AnimatedIcon.Default missing in the API, a static process step icon is used
        QueueElementPanel elementPanel = new QueueElementPanel(
            command,
            myService.isOneElement(communication)
                ? PlatformIconGroup.processStep_1()
                : Image.empty(Image.DEFAULT_ICON_SIZE)
        );
        myQueueElementPanelMap.put(command, elementPanel);
        myPanel.add(elementPanel.getQueuePanel());

        if (myService.isOneElement(communication)) {
            elementPanel.unsetCancelButton();
            commandSelected(elementPanel);
        }
        if (myService.isTwoElement(communication)) {
            if (communication instanceof PythonDebugConsoleCommunication) {
                repaintAll();
                return;
            }
            if (!Application.get().isUnitTestMode()) {
                // TODO consulo: GotItTooltip missing, tooltip.show(getActionButton(), GotItTooltip.BOTTOM_MIDDLE)
            }
        }
        repaintAll();
    }

    private void helperForRemove(ConsoleCommunication.ConsoleCodeFragment command) {
        var removedPanel = myQueueElementPanelMap.remove(command);

        if (removedPanel != null) {
            myPanel.remove(removedPanel.getQueuePanel());
        }

        if (!myQueueElementPanelMap.isEmpty()) {
            var firstCommand = myService.getFirstCommand(communication);
            if (firstCommand != null) {
                QueueElementPanel elementPanel = myQueueElementPanelMap.get(firstCommand);
                if (elementPanel != null) {
                    elementPanel.setIcon(PlatformIconGroup.processStep_1());
                    elementPanel.unsetCancelButton();

                    if (selectedCommand == removedPanel) {
                        commandSelected(elementPanel);
                    }
                }
            }
        }
        else {
            setTextToEditor("");
            selectedCommand = null;
        }
    }

    public void removeCommand(ConsoleCommunication.ConsoleCodeFragment command) {
        helperForRemove(command);
        repaintAll();
    }

    public void removeCommandByButton(ConsoleCommunication.ConsoleCodeFragment command) {
        myService.removeCommand(communication, command);
        helperForRemove(command);
        repaintAll();
    }

    public void removeAllCommands() {
        myQueueElementPanelMap.clear();
        myPanel.removeAll();
        if (selectedCommand != null) {
            setTextToEditor("");
            selectedCommand = null;
        }
        repaintAll();
    }

    private void repaintAll() {
        revalidate();
        repaint();
    }

    public void setCommunication(ConsoleCommunication communication) {
        this.communication = communication;
    }

    void commandSelected(QueueElementPanel elementPanel) {
        if (selectedCommand != null) {
            selectedCommand.getQueuePanel().setBackground(new JBColor(() -> UIUtil.getListBackground()));
            selectedCommand.setTextColor();
            selectedCommand.setButtonColor();
        }
        selectedCommand = elementPanel;
        selectedCommand.selectPanel();
        setTextToEditor(Objects.requireNonNull(elementPanel.getText()));
        mySplitter.setSecondComponent(myQueueEditor.getComponent());
    }

    public EditorEx getQueueEditor() {
        return myQueueEditor;
    }

    private ActionButton getActionButton() {
        return UIUtil.uiTraverser(myConsole.getToolbar().getComponent())
            .filter(ActionButton.class)
            .filter((button) -> {
                return button.getPresentation().getIcon() != null
                    && ShowCommandQueueAction.isCommandQueueIcon(button.getPresentation().getIcon());
            })
            .first();
    }

    private EditorEx createEmptyEditor() {
        EditorFactory editorFactory = EditorFactory.getInstance();
        // TODO consulo: EditorFactory.createDocument(boolean allowUpdatesWithoutWriteAction) missing
        Document document = editorFactory.createDocument("");
        UndoUtil.disableUndoFor(document);
        return (EditorEx) createEditor();
    }

    private Editor createEditor() {
        PsiFile consoleFile = myConsole.getFile();
        Language language = consoleFile.getLanguage();
        Project project = consoleFile.getProject();

        PsiFile psiFile = PsiFileFactory.getInstance(project).createFileFromText(
            "a." + consoleFile.getFileType().getDefaultExtension(),
            language,
            StringUtil.convertLineSeparators(StringUtil.trimEnd("", "\n")), false, true);
        VirtualFile virtualFile = psiFile.getViewProvider().getVirtualFile();
        if (virtualFile instanceof LightVirtualFile) {
            ((LightVirtualFile) virtualFile).setWritable(true);
        }
        Document document = Objects.requireNonNull(FileDocumentManager.getInstance().getDocument(virtualFile));
        EditorFactory editorFactory = EditorFactory.getInstance();
        EditorEx editor = (EditorEx) editorFactory.createViewer(document, project);
        editor.getSettings().setFoldingOutlineShown(false);
        editor.getSettings().setLineMarkerAreaShown(false);
        editor.getSettings().setIndentGuidesShown(false);

        SyntaxHighlighter highlighter =
            SyntaxHighlighterFactory.getSyntaxHighlighter(language, project, psiFile.getViewProvider().getVirtualFile());
        editor.setHighlighter(new LexerEditorHighlighter(highlighter, editor.getColorsScheme()));
        return editor;
    }

    private void setTextToEditor(String text) {
        Application.get().executeOnPooledThread(() ->
        {
            WriteCommandAction
                .writeCommandAction(myConsole.getProject())
                .run(() ->
                    myQueueEditor.getDocument()
                        .setText(text));
        });
    }
}
