/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.PyStatementListContainer;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PyStringLiteralUtil;
import com.jetbrains.python.psi.PyTryPart;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import consulo.annotation.access.RequiredReadAction;
import consulo.codeEditor.EditorEx;
import consulo.codeEditor.LogicalPosition;
import consulo.codeEditor.action.EditorActionHandler;
import consulo.codeEditor.action.EditorActionManager;
import consulo.codeEditor.util.EditorUtil;
import consulo.document.Document;
import consulo.document.util.DocumentUtil;
import consulo.document.util.TextRange;
import consulo.language.editor.WriteCommandAction;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.IdeActions;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

public class PyConsoleEnterHandler {
    @RequiredUIAccess
    public boolean handleEnterPressed(EditorEx editor) {
        Project project = editor.getProject();
        if (project == null) {
            throw new IllegalArgumentException();
        }
        int lineCount = editor.getDocument().getLineCount();
        if (lineCount > 0) { // move to end of line
            editor.getSelectionModel().removeSelection();
            LogicalPosition caretPosition = editor.getCaretModel().getLogicalPosition();
            if (caretPosition.line == lineCount - 1) {
                // we can move caret if only it's on the last line of command
                int lineEndOffset = editor.getDocument().getLineEndOffset(caretPosition.line);
                editor.getCaretModel().moveToOffset(lineEndOffset);
            }
            else {
                // otherwise just process enter action
                executeEnterHandler(project, editor);
                return false;
            }
        }
        else {
            return true;
        }
        PsiDocumentManager psiMgr = PsiDocumentManager.getInstance(project);
        psiMgr.commitDocument(editor.getDocument());

        int caretOffset = editor.getExpectedCaretOffset();
        PsiElement atElement = findFirstNoneSpaceElement(Objects.requireNonNull(psiMgr.getPsiFile(editor.getDocument())), caretOffset);
        if (atElement == null) {
            executeEnterHandler(project, editor);
            return false;
        }

        String firstLine = getLineAtOffset(editor.getDocument(), DocumentUtil.getFirstNonSpaceCharOffset(editor.getDocument(), 0));
        boolean isCellMagic = firstLine.trim().startsWith("%%") && !firstLine.stripTrailing().endsWith("?");
        String prevLine = getLineAtOffset(editor.getDocument(), caretOffset);

        boolean isLineContinuation = prevLine.trim().endsWith("\\");
        boolean insideDocString = isElementInsideDocString(atElement, caretOffset);
        boolean isMultiLineCommand = PsiTreeUtil.getParentOfType(atElement, PyStatementListContainer.class) != null || isCellMagic;
        boolean isAtTheEndOfCommand = editor.getDocument().getLineNumber(caretOffset) == editor.getDocument().getLineCount() - 1;

        boolean hasCompleteStatement = !insideDocString && !isLineContinuation && checkComplete(atElement);

        executeEnterHandler(project, editor);

        return isAtTheEndOfCommand && hasCompleteStatement && ((isMultiLineCommand && prevLine.isBlank()) || (!isMultiLineCommand));
    }

    private void executeEnterHandler(Project project, EditorEx editor) {
        EditorActionHandler enterHandler = EditorActionManager.getInstance().getActionHandler(IdeActions.ACTION_EDITOR_ENTER);
        WriteCommandAction.runWriteCommandAction(project, () -> {
            // The enter handler takes its project from the data context, and the console editor's Swing ancestors may
            // report another project when several projects share one remote development backend (PY-90740).
            enterHandler.execute(editor, null, EditorUtil.getEditorDataContext(editor));
        });
    }

    @RequiredReadAction
    private boolean isElementInsideDocString(PsiElement atElement, int caretOffset) {
        return atElement.getContext() instanceof PyStringLiteralExpression &&
            (PyTokenTypes.TRIPLE_NODES.contains(atElement.getNode().getElementType()) ||
                atElement.getNode().getElementType() == PyTokenTypes.DOCSTRING) &&
            isMultilineString(atElement.getText()) &&
            (atElement.getTextRange().getEndOffset() > caretOffset || !isCompleteDocString(atElement.getText()));
    }

    @RequiredReadAction
    private boolean checkComplete(PsiElement el) {
        PyStatementListContainer compoundStatement = PsiTreeUtil.getParentOfType(el, PyStatementListContainer.class);
        if (compoundStatement != null && !(compoundStatement instanceof PyTryPart)) {
            return compoundStatement.getStatementList().getStatements().length != 0;
        }
        PsiElement topLevel = PyPsiUtils.getParentRightBefore(el, el.getContainingFile());
        return topLevel != null && !PsiTreeUtil.hasErrorElements(topLevel);
    }

    @RequiredReadAction
    private @Nullable PsiElement findFirstNoneSpaceElement(PsiFile psiFile, int offset) {
        for (int i = offset; i >= 0; i--) {
            PsiElement el = psiFile.findElementAt(i);
            if (el != null && !(el instanceof PsiWhiteSpace)) {
                return el;
            }
        }
        return null;
    }

    private String getLineAtOffset(Document doc, int offset) {
        int line = doc.getLineNumber(offset);
        int start = doc.getLineStartOffset(line);
        int end = doc.getLineEndOffset(line);
        return doc.getText(new TextRange(start, end));
    }

    private boolean isMultilineString(String str) {
        String text = str.substring(PyStringLiteralUtil.getPrefixLength(str));
        return text.startsWith("\"\"\"") || text.startsWith("'''");
    }

    private boolean isCompleteDocString(String str) {
        int prefixLen = PyStringLiteralUtil.getPrefixLength(str);
        String text = str.substring(prefixLen);
        for (String token : new String[]{"\"\"\"", "'''"}) {
            if (text.length() >= 2 * token.length() && text.startsWith(token) && text.endsWith(token)) {
                return true;
            }
        }

        return false;
    }
}
