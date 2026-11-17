// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.editorActions.moveUpDown;

import com.jetbrains.python.ast.PyAstCaseClause;
import com.jetbrains.python.ast.PyAstClass;
import com.jetbrains.python.ast.PyAstElement;
import com.jetbrains.python.ast.PyAstFile;
import com.jetbrains.python.ast.PyAstFunction;
import com.jetbrains.python.ast.PyAstMatchStatement;
import com.jetbrains.python.ast.PyAstPassStatement;
import com.jetbrains.python.ast.PyAstStatement;
import com.jetbrains.python.ast.PyAstStatementList;
import com.jetbrains.python.ast.PyAstStatementListContainer;
import com.jetbrains.python.ast.PyAstStatementPart;
import com.jetbrains.python.ast.PyAstStringLiteralExpression;
import com.jetbrains.python.ast.impl.PyPsiUtilsCore;
import com.jetbrains.python.ast.impl.PyUtilCore;
import com.jetbrains.python.psi.PyAstElementGenerator;
import com.jetbrains.python.psi.PyStringLiteralCoreUtil;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.ApplicationManager;
import consulo.codeEditor.CaretModel;
import consulo.codeEditor.Editor;
import consulo.codeEditor.LogicalPosition;
import consulo.codeEditor.SelectionModel;
import consulo.document.Document;
import consulo.document.util.TextRange;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.codeStyle.PostprocessReformattingAspect;
import consulo.language.editor.moveUpDown.LineMover;
import consulo.language.editor.moveUpDown.LineRange;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

/**
 * User : ktisha
 */
@ExtensionImpl(id = "pyStatementMover", order = "before line")
public class PyStatementMover extends LineMover {
    private static @Nullable PyAstCaseClause getCaseClauseIfOnHeader(PsiElement element) {
        PyAstCaseClause caseClause = PsiTreeUtil.getParentOfType(element, PyAstCaseClause.class, false);
        if (caseClause == null) {
            return null;
        }
        PyAstStatementList body = caseClause.getStatementList();
        return PsiTreeUtil.isAncestor(body, element, false) ? null : caseClause;
    }

    @Override
    public boolean checkAvailable(Editor editor, PsiFile file, MoveInfo info, boolean down) {
        if (!(file instanceof PyAstFile)) {
            return false;
        }
        int offset = editor.getCaretModel().getOffset();
        SelectionModel selectionModel = editor.getSelectionModel();
        Document document = editor.getDocument();
        int lineNumber = document.getLineNumber(offset);
        int start = getLineStartSafeOffset(document, lineNumber);
        int lineEndOffset = document.getLineEndOffset(lineNumber);
        int end = lineEndOffset == 0 ? 0 : lineEndOffset - 1;

        if (selectionModel.hasSelection()) {
            start = selectionModel.getSelectionStart();
            int selectionEnd = selectionModel.getSelectionEnd();
            end = selectionEnd == 0 ? 0 : selectionEnd - 1;
        }
        PsiElement elementToMove1 = PyUtilCore.findNonWhitespaceAtOffset(file, start);
        PsiElement elementToMove2 = PyUtilCore.findNonWhitespaceAtOffset(file, end);
        if (elementToMove1 == null || elementToMove2 == null) {
            return false;
        }

        if (ifInsideString(document, lineNumber, elementToMove1, down)) {
            return false;
        }

        PyAstCaseClause caseClause1 = getCaseClauseIfOnHeader(elementToMove1);
        elementToMove1 = caseClause1 != null ? caseClause1 : getCommentOrStatement(document, elementToMove1);
        PyAstCaseClause caseClause2 = getCaseClauseIfOnHeader(elementToMove2);
        elementToMove2 = caseClause2 != null ? caseClause2 : getCommentOrStatement(document, elementToMove2);

        if (PsiTreeUtil.isAncestor(elementToMove1, elementToMove2, false)) {
            elementToMove2 = elementToMove1;
        }
        else if (PsiTreeUtil.isAncestor(elementToMove2, elementToMove1, false)) {
            elementToMove1 = elementToMove2;
        }
        else {
            PsiElement commonParent = PsiTreeUtil.findCommonParent(elementToMove1, elementToMove2);
            if (commonParent == null) {
                return false;
            }
            elementToMove1 = PyPsiUtilsCore.getParentRightBefore(elementToMove1, commonParent);
            elementToMove2 = PyPsiUtilsCore.getParentRightBefore(elementToMove2, commonParent);
            assert elementToMove1 != null && elementToMove2 != null;
        }

        info.toMove = new MyLineRange(elementToMove1, elementToMove2);
        info.toMove2 = getDestinationScope(file, editor, down ? elementToMove2 : elementToMove1, down);

        info.indentTarget = false;
        info.indentSource = false;

        return true;
    }

    private static boolean ifInsideString(Document document, int lineNumber, PsiElement elementToMove1, boolean down) {
        int start = document.getLineStartOffset(lineNumber);
        int end = document.getLineEndOffset(lineNumber);
        int nearLine = down ? lineNumber + 1 : lineNumber - 1;
        if (nearLine >= document.getLineCount() || nearLine <= 0) {
            return false;
        }
        PyAstStringLiteralExpression stringLiteralExpression = PsiTreeUtil.getParentOfType(elementToMove1, PyAstStringLiteralExpression.class);
        if (stringLiteralExpression != null) {
            Pair<String,String> quotes = PyStringLiteralCoreUtil.getQuotes(stringLiteralExpression.getText());
            if (quotes != null && (quotes.first.equals("'''") || quotes.first.equals("\"\"\""))) {
                String text1 = document.getText(TextRange.create(start, end)).trim();
                String text2 = document.getText(TextRange.create(document.getLineStartOffset(nearLine), document.getLineEndOffset(nearLine))).trim();
                if (!text1.startsWith(quotes.first) && !text1.endsWith(quotes.second) && !text2.startsWith(quotes.first) && !text2.endsWith(quotes.second))
                    return true;
            }
        }
        return false;
    }

    private static @Nullable LineRange getDestinationScope(PsiFile file, Editor editor,
                                                                     PsiElement elementToMove, boolean down) {
        Document document = file.getViewProvider().getDocument();
        if (document == null) {
            return null;
        }

        int offset = down ? elementToMove.getTextRange().getEndOffset() : elementToMove.getTextRange().getStartOffset();
        int lineNumber = down ? document.getLineNumber(offset) + 1 : document.getLineNumber(offset) - 1;
        if (moveOutsideFile(document, lineNumber)) {
            return null;
        }
        int lineEndOffset = document.getLineEndOffset(lineNumber);
        int startOffset = document.getLineStartOffset(lineNumber);

        PyAstStatementList statementList = getStatementList(elementToMove);

        if (elementToMove instanceof PyAstCaseClause) {
            PsiElement parent = elementToMove.getParent();
            if (!(parent instanceof PyAstMatchStatement)) {
                return null;
            }
            PyAstCaseClause sibling = down
                                            ? PsiTreeUtil.getNextSiblingOfType(elementToMove, PyAstCaseClause.class)
                                            : PsiTreeUtil.getPrevSiblingOfType(elementToMove, PyAstCaseClause.class);
            if (sibling == null) return null; // first clause moving up or last clause moving down
            return new ScopeRange((PyAstElement)parent, sibling, !down, true);
        }

        PsiElement destination = getDestinationElement(elementToMove, document, lineEndOffset, down);

        int start = destination != null ? destination.getTextRange().getStartOffset() : lineNumber;
        int end = destination != null ? destination.getTextRange().getEndOffset() : lineNumber;
        int startLine = document.getLineNumber(start);
        int endLine = document.getLineNumber(end);

        if (elementToMove instanceof PyAstClass || elementToMove instanceof PyAstFunction) {
            PyAstElement scope = statementList == null ? (PyAstElement)elementToMove.getContainingFile() : statementList;
            if (destination != null)
                return new ScopeRange(scope, destination, !down, true);
        }
        String lineText = document.getText(TextRange.create(startOffset, lineEndOffset));
        boolean isEmptyLine = StringUtil.isEmptyOrSpaces(lineText);
        if (isEmptyLine && moveToEmptyLine(elementToMove, down)) {
            return new LineRange(lineNumber, lineNumber + 1);
        }

        LineRange scopeRange = moveOut(elementToMove, editor, down);
        if (scopeRange != null) {
            return scopeRange;
        }
        scopeRange = moveInto(elementToMove, file, editor, down, lineEndOffset);
        if (scopeRange != null) {
            return scopeRange;
        }

        if (elementToMove instanceof PsiComment && ( PsiTreeUtil.isAncestor(destination, elementToMove, true)) ||
                destination instanceof  PsiComment) {
            return new LineRange(lineNumber, lineNumber + 1);
        }

        PyAstElement scope = statementList == null ? (PyAstElement)elementToMove.getContainingFile() : statementList;
        if ((elementToMove instanceof PyAstClass) || (elementToMove instanceof PyAstFunction))
            return new ScopeRange(scope, scope.getFirstChild(), !down, true);
        return new LineRange(startLine, endLine + 1);
    }

    private static boolean moveOutsideFile(Document document, int lineNumber) {
        return lineNumber < 0 || lineNumber >= document.getLineCount();
    }

    private static boolean moveToEmptyLine(PsiElement elementToMove, boolean down) {
        PyAstStatementList statementList = getStatementList(elementToMove);
        if (statementList != null) {
            if (down) {
                PsiElement child = statementList.getLastChild();
                if (elementToMove == child && PsiTreeUtil.getNextSiblingOfType(statementList.getParent(), PyAstStatementPart.class) != null
                        || child != elementToMove) {
                    return true;
                }
            }
            else {
                return true;
            }
        }
        return statementList == null;
    }

    private static PyAstStatementList getStatementList(PsiElement elementToMove) {
        return PsiTreeUtil.getParentOfType(elementToMove, PyAstStatementList.class, true, PyAstStatementListContainer.class);
    }

    private static @Nullable ScopeRange moveOut(PsiElement elementToMove, Editor editor, boolean down) {
        PyAstStatementList statementList = getStatementList(elementToMove);
        if (statementList == null) {
            return null;
        }

        if ((!down || statementList.getLastChild() != elementToMove) && (down || statementList.getFirstChild() != elementToMove)) {
            return null;
        }
        boolean addBefore = !down;
        PsiElement parent = statementList.getParent();
        PyAstStatementPart sibling = down ? PsiTreeUtil.getNextSiblingOfType(parent, PyAstStatementPart.class)
                                                        : PsiTreeUtil.getPrevSiblingOfType(parent, PyAstStatementPart.class);

        if (sibling != null) {
            PyAstStatementList list = sibling.getStatementList();
            return new ScopeRange(list, down ? list.getFirstChild() : list.getLastChild(), !addBefore);
        }
        else {
            PsiElement scope = getScopeForComment(elementToMove, editor, parent, !down);
            PsiElement anchor = PsiTreeUtil.getParentOfType(statementList, PyAstStatement.class);
            return scope == null || anchor == null ? null : new ScopeRange(scope, anchor, addBefore);
        }
    }

    private static PsiElement getScopeForComment(PsiElement elementToMove, Editor editor,
                                                           @Nullable PsiElement parent, boolean down) {
        PsiElement scope = PsiTreeUtil.getParentOfType(parent, PyAstStatementList.class, PyAstFile.class);
        int offset = elementToMove.getTextOffset();
        PsiElement sibling = elementToMove;
        while (scope != null && elementToMove instanceof PsiComment) { // stupid workaround for PY-6408. Related to PSI structure
            PsiElement prevSibling = down ? PsiTreeUtil.getNextSiblingOfType(sibling, PyAstStatement.class) :
                                                        PsiTreeUtil.getPrevSiblingOfType(sibling, PyAstStatement.class);
            if (prevSibling == null) {
                break;
            }
            if (editor.offsetToLogicalPosition(prevSibling.getTextOffset()).column ==
                    editor.offsetToLogicalPosition(offset).column) break;
            sibling = scope;
            scope = PsiTreeUtil.getParentOfType(scope, PyAstStatementList.class, PyAstFile.class);
        }
        return scope;
    }

    private static @Nullable LineRange moveInto(PsiElement elementToMove, PsiFile file,
                                                          Editor editor, boolean down, int offset) {

        PsiElement rawElement = PyUtilCore.findNonWhitespaceAtOffset(file, offset);
        if (rawElement == null) {
            return null;
        }

        return down ? moveDownInto(editor.getDocument(), rawElement) : moveUpInto(elementToMove, editor, rawElement, false);
    }

    private static @Nullable LineRange moveUpInto(PsiElement elementToMove, Editor editor,
                                                            PsiElement rawElement, boolean down) {
        Document document = editor.getDocument();
        PsiElement element = getCommentOrStatement(document, rawElement);
        PyAstStatementList statementList = getStatementList(elementToMove);
        PsiElement scopeForComment = statementList == null ? null :
                                                   getScopeForComment(elementToMove, editor, elementToMove, down);
        PyAstStatementList statementList2 = getStatementList(element);
        int start1 = elementToMove.getTextOffset() - document.getLineStartOffset(document.getLineNumber(elementToMove.getTextOffset()));
        int start2 = element.getTextOffset() - document.getLineStartOffset(document.getLineNumber(element.getTextOffset()));
        if (start1 != start2) {
            PyAstStatementList parent2 = PsiTreeUtil.getParentOfType(statementList2, PyAstStatementList.class);
            while (parent2 != scopeForComment && parent2 != null) {
                element = PsiTreeUtil.getParentOfType(statementList2, PyAstStatement.class);
                statementList2 = parent2;
                parent2 = PsiTreeUtil.getParentOfType(parent2, PyAstStatementList.class);
            }
        }

        if (statementList2 != null && scopeForComment != statementList2 &&
                (statementList2.getLastChild() == element || statementList2.getLastChild() == elementToMove) && element != null) {
            return new ScopeRange(statementList2, element, false);
        }
        return null;
    }

    private static @Nullable LineRange moveDownInto(Document document, PsiElement rawElement) {
        PsiElement element = getCommentOrStatement(document, rawElement);
        PyAstStatementList statementList2 = getStatementList(element);
        if (statementList2 != null) {                     // move to one-line conditional/loop statement
            int number = document.getLineNumber(element.getTextOffset());
            int number2 = document.getLineNumber(statementList2.getParent().getTextOffset());
            if (number == number2) {
                return new ScopeRange(statementList2, statementList2.getFirstChild(), true);
            }
        }
        PyAstStatementPart statementPart = PsiTreeUtil.getParentOfType(rawElement, PyAstStatementPart.class, true, PyAstStatement.class,
                                                                                     PyAstStatementList.class);
        PyAstFunction functionDefinition = PsiTreeUtil.getParentOfType(rawElement, PyAstFunction.class, true, PyAstStatement.class,
                                                                                     PyAstStatementList.class);
        PyAstClass classDefinition = PsiTreeUtil.getParentOfType(rawElement, PyAstClass.class, true, PyAstStatement.class,
                                                                               PyAstStatementList.class);
        PyAstStatementList list = null;
        if (statementPart != null) {
            list = statementPart.getStatementList();
        }
        else if (functionDefinition != null) {
            list = functionDefinition.getStatementList();
        }
        else if (classDefinition != null) {
            list = classDefinition.getStatementList();
        }
        if (list != null) {
            return new ScopeRange(list, list.getFirstChild(), true);
        }
        return null;
    }

    private static PsiElement getDestinationElement(PsiElement elementToMove, Document document,
                                                              int lineEndOffset, boolean down) {
        PsiElement destination = PyUtilCore.findPrevAtOffset(elementToMove.getContainingFile(), lineEndOffset, PsiWhiteSpace.class);
        PsiElement sibling = down ? PsiTreeUtil.getNextSiblingOfType(elementToMove, PyAstStatement.class) :
                                     PsiTreeUtil.getPrevSiblingOfType(elementToMove, PyAstStatement.class);
        if (destination == null) {
            if (elementToMove instanceof PyAstClass) {
                destination = sibling;
            }
            else if (elementToMove instanceof PyAstFunction) {
                if (!(sibling instanceof PyAstClass))
                    destination = sibling;
                else {
                    destination = null;
                }
            }
            else {
                return null;
            }
        }
        if (destination instanceof PsiComment) {
            return destination;
        }
        if (elementToMove instanceof PyAstClass) {
            destination = sibling;
        }
        else if (elementToMove instanceof PyAstFunction) {
            if (!(sibling instanceof PyAstClass))
                destination = sibling;
            else {
                destination = null;
            }
        }
        else {
            destination = getCommentOrStatement(document, sibling == null ? destination : sibling);
        }
        return destination;
    }

    private static PsiElement getCommentOrStatement(Document document, PsiElement destination) {
        PsiElement statement = PsiTreeUtil.getParentOfType(destination, PyAstStatement.class, false);
        if (statement == null) {
            return destination;
        }
        if (destination instanceof PsiComment) {
            if (document.getLineNumber(destination.getTextOffset()) == document.getLineNumber(statement.getTextOffset()))
                destination = statement;
        }
        else
            destination = statement;
        return destination;
    }

    @Override
    public void beforeMove(Editor editor, MoveInfo info, boolean down) {
        LineRange toMove = info.toMove;
        LineRange toMove2 = info.toMove2;

        if (toMove instanceof MyLineRange && toMove2 instanceof ScopeRange) {

            PostprocessReformattingAspect.getInstance(editor.getProject()).disablePostprocessFormattingInside(() -> {
                PsiElement startToMove = ((MyLineRange)toMove).myStartElement;
                PsiElement endToMove = ((MyLineRange)toMove).myEndElement;
                PsiFile file = startToMove.getContainingFile();
                SelectionModel selectionModel = editor.getSelectionModel();
                CaretModel caretModel = editor.getCaretModel();

                int selectionStart = selectionModel.getSelectionStart();
                LogicalPosition selectionBeforeMoveStart = editor.offsetToLogicalPosition(selectionStart);
                LogicalPosition selectionBeforeMoveEnd = editor.offsetToLogicalPosition(selectionModel.getSelectionEnd());
                boolean isSelectionStartAtCaret = caretModel.getOffset() == selectionStart;
                SelectionContainer selectionLen = getSelectionLenContainer(editor, ((MyLineRange)toMove));

                int shift = getCaretShift(startToMove, endToMove, caretModel, isSelectionStartAtCaret);

                boolean hasSelection = selectionModel.hasSelection();
                int offset;
                if (((ScopeRange)toMove2).isTheSameLevel()) {
                    offset = moveTheSameLevel((ScopeRange)toMove2, (MyLineRange)toMove);
                }
                else {
                    offset = moveInOut(((MyLineRange)toMove), editor, info);
                }
                LogicalPosition positionOffsetAfterMove = editor.offsetToLogicalPosition(offset);
                restoreCaretAndSelection(file, editor, isSelectionStartAtCaret, hasSelection, selectionLen,
                                             shift, offset, (MyLineRange)toMove,
                                             selectionBeforeMoveStart,
                                             selectionBeforeMoveEnd,
                                             positionOffsetAfterMove);
                info.toMove2 = info.toMove;   //do not move further
            });
        }

    }

    private static SelectionContainer getSelectionLenContainer(Editor editor, MyLineRange toMove) {
        SelectionModel selectionModel = editor.getSelectionModel();
        PsiElement startToMove = toMove.myStartElement;
        PsiElement endToMove = toMove.myEndElement;
        int selectionStart = selectionModel.getSelectionStart();
        int selectionEnd = selectionModel.getSelectionEnd();

        TextRange range = startToMove.getTextRange();
        int column = editor.offsetToLogicalPosition(selectionStart).column;
        int additionalSelection = range.getStartOffset() > selectionStart ? range.getStartOffset() - selectionStart : 0;
        if (startToMove == endToMove) {
            return new SelectionContainer(selectionEnd - range.getStartOffset(), additionalSelection, column == 0);
        }
        int len = range.getStartOffset() <= selectionStart ? range.getEndOffset() - selectionStart : startToMove.getTextLength();

        PsiElement tmp = startToMove.getNextSibling();
        while (tmp != endToMove && tmp != null) {
            if (!(tmp instanceof PsiWhiteSpace))
                len += tmp.getTextLength();
            tmp = tmp.getNextSibling();
        }
        len = len + selectionEnd - endToMove.getTextOffset();

        return new SelectionContainer(len, additionalSelection, column == 0);
    }

    private static void restoreCaretAndSelection(PsiFile file, Editor editor, boolean selectionStartAtCaret,
                                                           boolean hasSelection, SelectionContainer selectionContainer, int shift,
                                                           int offset, MyLineRange toMove,
                                                           LogicalPosition selectionBeforeMoveStart,
                                                           LogicalPosition selectionBeforeMoveEnd,
                                                           LogicalPosition positionOffsetAfterMove) {
        Document document = editor.getDocument();
        SelectionModel selectionModel = editor.getSelectionModel();
        CaretModel caretModel = editor.getCaretModel();
        int selectionLen = selectionContainer.myLen;
        PsiElement at = file.findElementAt(offset);
        if (at != null) {
            PsiElement added = getCommentOrStatement(document, at);
            int size = toMove.size;
            if (size > 1) {
                PsiElement tmp = added.getNextSibling();
                while (size > 1 && tmp != null) {
                    if (tmp instanceof PsiWhiteSpace) {
                        if (!selectionStartAtCaret)
                          shift += tmp.getTextLength();
                        selectionLen += tmp.getTextLength();
                    }
                    tmp = tmp.getNextSibling();
                    size -= 1;
                }
            }
            if (shift < 0) {
                shift = 0;
            }
            int column = editor.offsetToLogicalPosition(added.getTextRange().getStartOffset()).column;
            if (selectionContainer.myAtTheBeginning || column < selectionContainer.myAdditional) {
                selectionLen += column;
            }
            else {
                selectionLen += selectionContainer.myAdditional;
            }
            if (selectionContainer.myAtTheBeginning && selectionStartAtCaret)
                shift = -column;
        }

        int documentLength = document.getTextLength();
        int newCaretOffset = offset + shift;
        if (newCaretOffset >= documentLength) {
            newCaretOffset = documentLength;
        }
        caretModel.moveToOffset(newCaretOffset);

        if (hasSelection) {
            int selectionLinesDiff = selectionBeforeMoveEnd.line - selectionBeforeMoveStart.line;
            if (selectionLinesDiff > 1) {
                int endOffsetColumn = selectionBeforeMoveEnd.column;
                if (endOffsetColumn > 0) {
                    endOffsetColumn += positionOffsetAfterMove.column - selectionBeforeMoveStart.column;
                }
                int startOffsetColumn = positionOffsetAfterMove.column;
                if (selectionBeforeMoveStart.column == 0) {
                    startOffsetColumn = 0;
                }
                int startOffset = editor.logicalPositionToOffset(new LogicalPosition(positionOffsetAfterMove.line, startOffsetColumn));
                int endOffset = editor.logicalPositionToOffset(new LogicalPosition(positionOffsetAfterMove.line + selectionLinesDiff, endOffsetColumn));
                selectionModel.setSelection(startOffset, endOffset);
                if (selectionStartAtCaret) {
                    caretModel.moveToOffset(startOffset);
                }
                else {
                    caretModel.moveToOffset(endOffset);
                }
            }
            else {
                if (selectionStartAtCaret) {
                    int newSelectionEnd = newCaretOffset + selectionLen;
                    selectionModel.setSelection(newCaretOffset, newSelectionEnd);
                }
                else {
                    int newSelectionStart = newCaretOffset - selectionLen;
                    selectionModel.setSelection(newSelectionStart, newCaretOffset);
                }
            }
        }
    }

    private static int getCaretShift(PsiElement startToMove, PsiElement endToMove, CaretModel caretModel, boolean selectionStartAtCaret) {
        int shift;
        if (selectionStartAtCaret) {
            shift = caretModel.getOffset() - startToMove.getTextRange().getStartOffset();
        }
        else {
            shift = caretModel.getOffset();
            if (startToMove != endToMove) {
                shift += startToMove.getTextLength();

                PsiElement tmp = startToMove.getNextSibling();
                while (tmp != endToMove && tmp != null) {
                    if (!(tmp instanceof PsiWhiteSpace))
                        shift += tmp.getTextLength();
                    tmp = tmp.getNextSibling();
                }
            }

            shift -= endToMove.getTextOffset();
        }
        return shift;
    }

    private static int moveTheSameLevel(ScopeRange toMove2, MyLineRange toMove) {
        PsiElement anchor = toMove2.getAnchor();
        PsiElement anchorCopy = anchor.copy();
        PsiElement startToMove = toMove.myStartElement;
        PsiElement endToMove = toMove.myEndElement;

        PsiElement parent = anchor.getParent();
        PsiElement tmp = startToMove.getNextSibling();

        if (startToMove != endToMove && tmp != null) {
            parent.addRangeAfter(tmp, endToMove, anchor);
        }

        PsiElement startCopy = startToMove.copy();
        startToMove.replace(anchorCopy);
        PsiElement addedElement = anchor.replace(startCopy);

        if (startToMove != endToMove && tmp != null) {
            parent.deleteChildRange(tmp, endToMove);
        }

        return addedElement.getTextRange().getStartOffset();
    }

    private static int moveInOut(MyLineRange toMove, Editor editor, MoveInfo info) {
        boolean removePass = false;
        ScopeRange toMove2 = (ScopeRange)info.toMove2;
        PsiElement scope = toMove2.getScope();
        PsiElement anchor = toMove2.getAnchor();
        Project project = scope.getProject();

        PsiElement startElement = toMove.myStartElement;
        PsiElement endElement = toMove.myEndElement;
        PsiElement parent = startElement.getParent();

        if (scope instanceof PyAstStatementList && !(startElement == endElement && startElement instanceof PsiComment)) {
            PyAstStatement[] statements = ((PyAstStatementList)scope).getStatements();
            if (statements.length == 1 && statements[0] == anchor && statements[0] instanceof PyAstPassStatement) {
                removePass = true;
            }
        }

        PsiElement addedElement;
        PsiElement nextSibling = startElement.getNextSibling();
        if (toMove2.isAddBefore()) {
            PsiElement tmp = endElement.getPrevSibling();
            if (startElement != endElement && tmp != null) {
                addedElement = scope.addRangeBefore(startElement, tmp, anchor);
                scope.addBefore(endElement, anchor);
            }
            else {
                addedElement = scope.addBefore(endElement, anchor);
            }
        }
        else {
            if (startElement != endElement && nextSibling != null) {
                scope.addRangeAfter(nextSibling, endElement, anchor);
            }
            addedElement = scope.addAfter(startElement, anchor);
        }
        addPassStatement(toMove, project);

        if (startElement != endElement && nextSibling != null) {
            parent.deleteChildRange(nextSibling, endElement);
        }
        startElement.delete();

        int addedElementLine = editor.getDocument().getLineNumber(addedElement.getTextOffset());
        PsiFile file = scope.getContainingFile();

        adjustLineIndents(editor, scope, project, addedElement, toMove.size);

        if (removePass) {
            ApplicationManager.getApplication().runWriteAction(() -> {
                Document document = editor.getDocument();
                int lineNumber = document.getLineNumber(anchor.getTextOffset());
                int endOffset = document.getLineCount() <= lineNumber + 1 ? document.getLineEndOffset(lineNumber)
                                                                                    : document.getLineStartOffset(lineNumber + 1);
                document.deleteString(document.getLineStartOffset(lineNumber), endOffset);
                PsiDocumentManager.getInstance(startElement.getProject()).commitAllDocuments();
            });
        }

        int offset = addedElement.getTextRange().getStartOffset();
        int newLine = editor.getDocument().getLineNumber(offset);
        if (newLine != addedElementLine && !removePass) {  // PsiComment gets broken after adjust indent
            PsiElement psiElement = PyUtilCore.findNonWhitespaceAtOffset(file, editor.getDocument().getLineEndOffset(addedElementLine) - 1);
            if (psiElement != null) {
                psiElement = getCommentOrStatement(editor.getDocument(), psiElement);
                offset = psiElement.getTextRange().getStartOffset();
            }
        }
        return offset;
    }

    private static void adjustLineIndents(Editor editor, PsiElement scope, Project project,
                                                    PsiElement addedElement, int size) {
        CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
        Document document = editor.getDocument();

        if (!(scope instanceof PsiFile)) {
            int line1 = editor.offsetToLogicalPosition(scope.getTextRange().getStartOffset()).line;
            int line2 = editor.offsetToLogicalPosition(scope.getTextRange().getEndOffset()).line;
            codeStyleManager.adjustLineIndent(scope.getContainingFile(),
                                                    new TextRange(document.getLineStartOffset(line1), document.getLineEndOffset(line2)));
        }
        else {
            int line1 = editor.offsetToLogicalPosition(addedElement.getTextRange().getStartOffset()).line;
            PsiElement end = addedElement;
            while (size > 0) {
                PsiElement tmp = end.getNextSibling();
                if (tmp == null) {
                    break;
                }
                size -= 1;
                end = tmp;
            }
            int endOffset = end.getTextRange().getEndOffset();
            int line2 = editor.offsetToLogicalPosition(endOffset).line;
            codeStyleManager.adjustLineIndent(scope.getContainingFile(),
                                                    new TextRange(document.getLineStartOffset(line1), document.getLineEndOffset(line2)));
        }
    }

    private static void addPassStatement(MyLineRange toMove, Project project) {
        PsiElement startElement = toMove.myStartElement;
        PsiElement endElement = toMove.myEndElement;
        PyAstStatementList initialScope = getStatementList(startElement);

        if (initialScope != null && !(startElement == endElement && startElement instanceof PsiComment)) {
            if (initialScope.getStatements().length == toMove.statementsSize) {
                PyAstPassStatement passStatement = PyAstElementGenerator.getInstance(project).createPassStatement();
                initialScope.addAfter(passStatement, initialScope.getStatements()[initialScope.getStatements().length - 1]);
            }
        }
    }

    // use to keep elements
    public static class MyLineRange extends LineRange {
        private final PsiElement myStartElement;
        private final PsiElement myEndElement;
        int size = 0;
        int statementsSize = 0;

        MyLineRange(PsiElement start, PsiElement end) {
            super(start, end);
            myStartElement = start;
            myEndElement = end;

            if (myStartElement == myEndElement) {
                size = 1;
                statementsSize = 1;
            }
            else {
                PsiElement counter = myStartElement;
                while (counter != myEndElement && counter != null) {
                    size += 1;
                    if (!(counter instanceof PsiWhiteSpace) && !(counter instanceof PsiComment)) {
                        statementsSize += 1;
                    }
                    counter = counter.getNextSibling();
                }
                size += 1;
                if (!(counter instanceof PsiWhiteSpace) && !(counter instanceof PsiComment)) {
                    statementsSize += 1;
                }
            }
        }

        public PsiElement getStartElement() {
            return myStartElement;
        }

        public PsiElement getEndElement() {
            return myEndElement;
        }
    }

    static class SelectionContainer {
        private final int myLen;
        private final int myAdditional;
        private final boolean myAtTheBeginning;

        SelectionContainer(int len, int additional, boolean atTheBeginning) {
            myLen = len;
            myAdditional = additional;
            myAtTheBeginning = atTheBeginning;
        }
    }

    // Use when element scope changed
    public static class ScopeRange extends LineRange {
        private final PsiElement myScope;
        private final PsiElement myAnchor;
        private final boolean addBefore;
        private boolean theSameLevel;

        ScopeRange(PsiElement scope, PsiElement anchor, boolean before) {
            super(scope);
            myScope = scope;
            myAnchor = anchor;
            addBefore = before;
        }

        ScopeRange(PyAstElement scope, PsiElement anchor, boolean before, boolean b) {
            super(scope);
            myScope = scope;
            myAnchor = anchor;
            addBefore = before;
            theSameLevel = b;
        }

        public PsiElement getAnchor() {
            return myAnchor;
        }

        public PsiElement getScope() {
            return myScope;
        }

        public boolean isAddBefore() {
            return addBefore;
        }

        public boolean isTheSameLevel() {
            return theSameLevel;
        }
    }
}
