// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.console;

import com.jetbrains.python.console.pydev.ConsoleCommunicationListener;
import consulo.codeEditor.FoldRegion;
import consulo.codeEditor.FoldingModel;
import consulo.codeEditor.event.FoldingListener;
import consulo.document.Document;
import consulo.document.event.DocumentEvent;
import consulo.document.event.DocumentListener;
import consulo.document.util.DocumentUtil;
import consulo.python.impl.localize.PyLocalize;

import java.util.List;

class PyConsoleStartFolding implements ConsoleCommunicationListener, FoldingListener, DocumentListener {
    private final PythonConsoleView myConsoleView;
    private int myNumberOfCommandExecuted = 0;
    private int myNumberOfCommandToStop = 2;
    private boolean doNotAddFoldingAgain = false;
    private FoldRegion myStartFoldRegion;
    private final boolean myAddOnce;
    private static final String PYTHON_PREFIX = "Python";
    private int myStartLineOffset = 0;
    private final List<String> firstLinePrefix = List.of("Python", "PyDev console");
    private final List<String> lastLinePrefix = List.of("IPython", "[", "PyDev console");

    public PyConsoleStartFolding(PythonConsoleView consoleView, boolean addOnce) {
        super();
        myConsoleView = consoleView;
        myAddOnce = addOnce;
    }

    public void setStartLineOffset(int startLineOffset) {
        myStartLineOffset = startLineOffset;
    }

    public void setNumberOfCommandToStop(int numberOfCommandToStop) {
        myNumberOfCommandToStop = numberOfCommandToStop;
    }

    private int getStartDefaultValue() {
        return myStartLineOffset;
    }

    @Override
    public void documentChanged(DocumentEvent event) {
        addFolding();
    }

    private void addFolding() {
        Document document = myConsoleView.getEditor().getDocument();
        if (doNotAddFoldingAgain || document.getTextLength() == 0) {
            return;
        }
        if (myNumberOfCommandExecuted >= myNumberOfCommandToStop) {
            document.removeDocumentListener(this);
            return;
        }
        FoldingModel foldingModel = myConsoleView.getEditor().getFoldingModel();
        foldingModel.runBatchFoldingOperation(() -> {
            int start = getStartDefaultValue();
            int startLine = 0;
            int finish = start;
            int finishLine = 0;
            String placeholderText = PyLocalize.pythonConsole().get();
            int firstLine = document.getLineNumber(myStartLineOffset);
            for (int line = firstLine; line < document.getLineCount(); line++) {
                String lineText = document.getText(DocumentUtil.getLineTextRange(document, line));
                String prevLineText = null;
                if (line > 0) {
                    prevLineText = document.getText(DocumentUtil.getLineTextRange(document, line - 1));
                }
                if (start == getStartDefaultValue()) {
                    for (String prefix : firstLinePrefix) {
                        if (lineText.startsWith(prefix)) {
                            start = document.getLineStartOffset(line);
                            startLine = line;
                            if (prefix.equals(PYTHON_PREFIX)) {
                                placeholderText = lineText;
                            }
                            break;
                        }
                    }
                }

                if (!doNotAddFoldingAgain) {
                    for (String prefix : lastLinePrefix) {
                        if (lineText.startsWith(prefix) && (!prefix.equals("[")) ||
                            (prefix.equals("[") && prevLineText != null && prevLineText.startsWith(PYTHON_PREFIX))) {
                            finish = document.getLineEndOffset(line);
                            finishLine = line;
                            doNotAddFoldingAgain = myAddOnce;
                            break;
                        }
                    }
                }
            }

            if (myStartFoldRegion != null) {
                foldingModel.removeFoldRegion(myStartFoldRegion);
            }
            if ((start >= finish) || (startLine == finishLine)) {
                return;
            }
            FoldRegion foldRegion = foldingModel.addFoldRegion(start, finish, placeholderText);
            if (foldRegion != null) {
                foldRegion.setExpanded(false);
                myStartFoldRegion = foldRegion;
            }
        });
    }

    @Override
    public void commandExecuted(boolean more) {
        myNumberOfCommandExecuted++;
    }

    @Override
    public void inputRequested() {

    }

    @Override
    public void onFoldRegionStateChange(FoldRegion region) {
        if (region.equals(myStartFoldRegion) && region.isExpanded()) {
            myConsoleView.getEditor().getComponent().updateUI();
            doNotAddFoldingAgain = true;
        }
    }
}
