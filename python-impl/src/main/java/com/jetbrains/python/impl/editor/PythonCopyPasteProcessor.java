// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.editor;

import com.google.common.collect.ImmutableSet;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.PyExpressionCodeFragmentImpl;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyIndentUtil;
import com.jetbrains.python.psi.PyStatementList;
import com.jetbrains.python.psi.PyStatementListContainer;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.ApplicationManager;
import consulo.codeEditor.CaretModel;
import consulo.codeEditor.Editor;
import consulo.codeEditor.RawText;
import consulo.codeEditor.SelectionModel;
import consulo.document.Document;
import consulo.document.util.TextRange;
import consulo.language.ast.IElementType;
import consulo.language.editor.CodeInsightSettings;
import consulo.language.editor.action.CopyPastePreProcessor;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiErrorElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.ObjectUtil;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

import java.util.Set;

import static com.jetbrains.python.impl.psi.PyUtil.as;

/**
 * User : catherine
 */
@ExtensionImpl
public final class PythonCopyPasteProcessor implements CopyPastePreProcessor {

    /**
     * Keywords that start multiline block statements
     */
    private static final Set<String> START_KEYWORDS = ImmutableSet.of("async",
        "def",
        "class",
        "with",
        "if", "elif", "else",
        "while", "for",
        "try", "except", "finally",
        "match", "case");

    @Override
    public @Nullable String preprocessOnCopy(PsiFile file, int[] startOffsets, int[] endOffsets, String text) {
        if (!CodeInsightSettings.getInstance().INDENT_TO_CARET_ON_PASTE || !isSupportedFile(file)) {
            return null;
        }
        // Expand copied text if it can cause indentation ambiguity

        // Text was selected with a single caret and might begin with a block statement or a comment
        if (startOffsets.length == 1 && endOffsets.length == 1) {
            int start = startOffsets[0];
            int end = endOffsets[0];

            Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
            if (document != null) {
                int startLine = document.getLineNumber(start);
                int startLineOffset = getLineStartSafeOffset(document, startLine);
                if (start != startLineOffset && startLine != document.getLineNumber(end)) {
                    String linePrefix = document.getText(TextRange.create(startLineOffset, start));
                    if (StringUtil.isEmptyOrSpaces(linePrefix) &&
                            (selectionBeginsWithComment(file, start) || selectionBeginsWithBlockStatementAndItsBody(file, start, end, text))) {
                        return linePrefix + text;
                    }
                }
            }
        }

        return null;
    }

    /**
     * The first line of the copied text loses its indent. The lines after a comment keep their indent,
     * so the paste cannot find the common indent without the indent of the comment.
     */
    private static boolean selectionBeginsWithComment(PsiFile file, int start) {
        return file.findElementAt(start) instanceof PsiComment comment && comment.getTextOffset() == start;
    }

    private static boolean selectionBeginsWithBlockStatementAndItsBody(PsiFile file, int start, int end, String text) {
        if (!fragmentBeginsWithBlockStatement(text)) {
            return false;
        }
        PsiElement keyword = file.findElementAt(start);
        if (keyword == null || !START_KEYWORDS.contains(keyword.getText())) {
            return false;
        }
        PyStatementListContainer block = PsiTreeUtil.getParentOfType(keyword, PyStatementListContainer.class);
        return block != null && end > block.getStatementList().getTextOffset();
    }

    @Override
    public String preprocessOnPaste(Project project,
                                    PsiFile file,
                                    Editor editor,
                                    String text,
                                    RawText rawText) {
        if (!CodeInsightSettings.getInstance().INDENT_TO_CARET_ON_PASTE || !isSupportedFile(file)) {
            return text;
        }

        CaretModel caretModel = editor.getCaretModel();
        SelectionModel selectionModel = editor.getSelectionModel();
        Document document = editor.getDocument();
        int caretOffset = selectionModel.getSelectionStart() != selectionModel.getSelectionEnd() ?
                                                        selectionModel.getSelectionStart() : caretModel.getOffset();
        int lineNumber = document.getLineNumber(caretOffset);
        int lineStartOffset = getLineStartSafeOffset(document, lineNumber);
        int lineEndOffset = document.getLineEndOffset(lineNumber);

        String line = document.getText(TextRange.create(lineStartOffset, lineEndOffset));

        String linePrefix = document.getText(TextRange.create(lineStartOffset, caretOffset));
        if (!StringUtil.isEmptyOrSpaces(linePrefix)) {
            return text;
        }

        PsiElement element = file.findElementAt(caretOffset);
        if (PsiTreeUtil.getParentOfType(element, PyStringLiteralExpression.class) != null) {
            return text;
        }

        text = addLeadingSpacesToNormalizeSelection(file, text);
        String fragmentIndent = PyIndentUtil.findCommonIndent(text, false);
        String newIndent = inferBestIndent(file, document, caretOffset, lineNumber, fragmentIndent);
        String newText = PyIndentUtil.changeIndent(text, false, newIndent);

        if (!selectionModel.hasSelection() && shouldPasteOnPreviousLine(file, text, caretOffset)) {
            caretModel.moveToOffset(lineStartOffset);
            if (StringUtil.isEmptyOrSpaces(line)) {
                ApplicationManager.getApplication().runWriteAction(() -> document.deleteString(lineStartOffset, lineEndOffset));
            }
        }
        else {
            // Don't duplicate the existing whitespace prefix of the line
            newText = StringUtil.trimStart(newText, linePrefix);
        }

        boolean useTabs = PyIndentUtil.areTabsUsedForIndentation(file);
        // TODO Combine shouldPasteOnPreviousLine() check with addLineBreak() as they really complement each other
        if (addLinebreak(text, line, useTabs) && selectionModel.getSelectionStart() == selectionModel.getSelectionEnd()) {
            newText += "\n";
        }
        return newText;
    }

    private static boolean isSupportedFile(PsiFile file) {
        return file instanceof PyFile;
    }

    private static String addLeadingSpacesToNormalizeSelection(PsiFile file, String text) {
        if (!fragmentBeginsWithBlockStatement(text)) {
            return text;
        }

        PyExpressionCodeFragmentImpl fragment = new PyExpressionCodeFragmentImpl(file.getProject(), "dummy.py", text, false);
        //fragment.setContext(file);
        PyStatementListContainer statement = as(fragment.getFirstChild(), PyStatementListContainer.class);
        if (statement == null) {
            return text;
        }

        String statementIndent = PyIndentUtil.getElementIndent(statement);
        if (!statementIndent.isEmpty()) {
            return text;
        }

        String indentStep = PyIndentUtil.getIndentFromSettings(file);
        String bodyIndent = PyIndentUtil.getElementIndent(statement.getStatementList());
        String expectedBodyIndent = statementIndent + indentStep;
        if (bodyIndent.startsWith(expectedBodyIndent)) {
            return bodyIndent.substring(0, bodyIndent.length() - indentStep.length()) + text;
        }
        return text;
    }

    private static boolean fragmentBeginsWithBlockStatement(String text) {
        return ContainerUtil.exists(START_KEYWORDS, keyword -> text.startsWith(keyword + " ") || text.startsWith(keyword + ":"));
    }

    private static String inferBestIndent(PsiFile file,
                                          Document document,
                                          int caretOffset,
                                          int lineNumber,
                                          String fragmentIndent) {

        PsiElement nonWS = PyUtil.findNextAtOffset(file, caretOffset, PsiWhiteSpace.class);
        if (nonWS != null) {
            IElementType nonWSType = nonWS.getNode().getElementType();
            if (nonWSType == PyTokenTypes.ELSE_KEYWORD || nonWSType == PyTokenTypes.ELIF_KEYWORD ||
                    nonWSType == PyTokenTypes.EXCEPT_KEYWORD || nonWSType == PyTokenTypes.FINALLY_KEYWORD) {
                lineNumber -= 1;
                nonWS = PyUtil.findNextAtOffset(file, getLineStartSafeOffset(document, lineNumber), PsiWhiteSpace.class);
            }
        }

        if (nonWS != null && document.getLineNumber(nonWS.getTextOffset()) == lineNumber) {
            return PyIndentUtil.getLineIndent(document, lineNumber);
        }

        int lineStartOffset = getLineStartSafeOffset(document, lineNumber);
        PsiElement ws = file.findElementAt(lineStartOffset);
        String userIndent = document.getText(TextRange.create(lineStartOffset, caretOffset));

        PyStatementList statementList = findEmptyStatementListNearby(file, lineStartOffset);
        if (statementList != null) {
            return PyIndentUtil.getElementIndent(statementList);
        }

        String smallestIndent = ws == null ? "" : PyIndentUtil.getElementIndent(ws);
        PyStatementListContainer parentBlock = PsiTreeUtil.getParentOfType(ws, PyStatementListContainer.class);
        PyStatementListContainer deepestBlock = getDeepestPossibleParentBlock(file, caretOffset);
        String greatestIndent;
        if (deepestBlock != null && (parentBlock == null || PsiTreeUtil.isAncestor(parentBlock, deepestBlock, true))) {
            greatestIndent = PyIndentUtil.getElementIndent(deepestBlock.getStatementList());
        }
        else {
            greatestIndent = smallestIndent;
        }
        if (caretOffset == lineStartOffset && fragmentIndent.startsWith(smallestIndent) && greatestIndent.startsWith(fragmentIndent)) {
            return fragmentIndent;
        }
        if (smallestIndent.startsWith(userIndent)) {
            return smallestIndent;
        }
        if (userIndent.startsWith(greatestIndent)) {
            return greatestIndent;
        }
        return userIndent;
    }

    private static @Nullable PyStatementList findEmptyStatementListNearby(PsiFile file, int offset) {
        PsiWhiteSpace whitespace = findWhitespaceAtCaret(file, offset);
        if (whitespace == null) {
            return null;
        }

        PyStatementList statementList = ObjectUtil.chooseNotNull(as(whitespace.getNextSibling(), PyStatementList.class),
                                                                                                                            as(whitespace.getPrevSibling(), PyStatementList.class));
        if (statementList == null) {
            PsiElement prevLeaf = getPrevNonCommentLeaf(whitespace);
            if (prevLeaf instanceof PsiErrorElement) {
                statementList = as(prevLeaf.getParent(), PyStatementList.class);
            }
        }
        return statementList != null && statementList.getStatements().length == 0 ? statementList : null;
    }

    private static @Nullable PsiWhiteSpace findWhitespaceAtCaret(PsiFile file, int offset) {
        return as(file.findElementAt(offset == file.getTextLength() && offset > 0 ? offset - 1 : offset), PsiWhiteSpace.class);
    }

    private static @Nullable PyStatementListContainer getDeepestPossibleParentBlock(PsiFile file, int offset) {
        PsiWhiteSpace whitespace = findWhitespaceAtCaret(file, offset);
        if (whitespace == null) {
            return null;
        }
        PsiElement prevLeaf = getPrevNonCommentLeaf(whitespace);
        return PsiTreeUtil.getParentOfType(prevLeaf, PyStatementListContainer.class);
    }

    private static boolean shouldPasteOnPreviousLine(PsiFile file, String text, int caretOffset) {
        boolean useTabs = PyIndentUtil.areTabsUsedForIndentation(file);
        PsiElement nonWS = PyUtil.findNextAtOffset(file, caretOffset, PsiWhiteSpace.class);
        if (nonWS == null || text.endsWith("\n")) {
            return true;
        }
        if (inStatementList(file, caretOffset) && (text.startsWith(useTabs ? "\t" : " ") || StringUtil.split(text, "\n").size() > 1)) {
            return true;
        }
        return false;
    }

    private static @Nullable PsiElement getPrevNonCommentLeaf(PsiElement element) {
        PsiElement anchor = PsiTreeUtil.prevLeaf(element);
        while (anchor instanceof PsiComment || anchor instanceof PsiWhiteSpace) {
            anchor = PsiTreeUtil.prevLeaf(anchor, false);
        }
        return anchor;
    }

    private static boolean inStatementList(PsiFile file, int caretOffset) {
        PsiElement element = file.findElementAt(caretOffset);
        return PsiTreeUtil.getParentOfType(element, PyStatementListContainer.class) != null;
    }

    private static boolean addLinebreak(String text, String line, boolean useTabs) {
        return (text.startsWith(useTabs ? "\t" : " ") || StringUtil.split(text, "\n").size() > 1)
                      && !text.endsWith("\n") && !StringUtil.isEmptyOrSpaces(line);
    }

    private static int getLineStartSafeOffset(Document document, int line) {
        if (line >= document.getLineCount()) {
            return document.getTextLength();
        }
        if (line < 0) {
            return 0;
        }
        return document.getLineStartOffset(line);
    }

}
