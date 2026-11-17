// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.impl.editor;

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.ast.PyAstDocStringOwner;
import com.jetbrains.python.ast.PyAstFile;
import com.jetbrains.python.ast.PyAstFromImportStatement;
import com.jetbrains.python.ast.PyAstGeneratorExpression;
import com.jetbrains.python.ast.PyAstParenthesizedExpression;
import com.jetbrains.python.ast.PyAstStringElement;
import com.jetbrains.python.ast.PyAstStringLiteralExpression;
import com.jetbrains.python.ast.PyAstWithStatement;
import com.jetbrains.python.ast.docstring.DocStringUtilCore;
import com.jetbrains.python.codeInsight.PyCodeInsightSettings;
import com.jetbrains.python.documentation.docstrings.DocStringFormat;
import com.jetbrains.python.documentation.docstrings.DocStringParser;
import com.jetbrains.python.documentation.docstrings.GoogleCodeStyleDocString;
import com.jetbrains.python.documentation.docstrings.GoogleCodeStyleDocStringBuilder;
import com.jetbrains.python.documentation.docstrings.SectionBasedDocString;
import com.jetbrains.python.editor.PyEditorHandlerConfig;
import com.jetbrains.python.formatter.PyWhiteSpaceFormattingStrategy;
import com.jetbrains.python.impl.documentation.docstrings.PyDocstringGenerator;
import com.jetbrains.python.psi.PyIndentUtil;
import com.jetbrains.python.psi.PyStringLiteralUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.util.LineTokenizer;
import consulo.codeEditor.Editor;
import consulo.codeEditor.action.EditorActionHandler;
import consulo.codeEditor.action.SplitLineAction;
import consulo.dataContext.DataContext;
import consulo.dataContext.DataManager;
import consulo.document.Document;
import consulo.document.util.TextRange;
import consulo.ide.impl.idea.codeInsight.editorActions.AutoHardWrapHandler;
import consulo.language.ast.ASTNode;
import consulo.language.ast.IElementType;
import consulo.language.editor.CodeInsightSettings;
import consulo.language.editor.action.EnterHandlerDelegateAdapter;
import consulo.language.editor.inject.EditorWindow;
import consulo.language.inject.InjectedLanguageManager;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiErrorElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.util.collection.ArrayUtil;
import consulo.util.lang.ref.SimpleReference;
import org.jspecify.annotations.Nullable;

import java.util.regex.Matcher;

@ExtensionImpl
public final class PythonEnterHandler extends EnterHandlerDelegateAdapter {
    private int myPostprocessShift = 0;

    @Override
    @RequiredReadAction
    public Result preprocessEnter(
        PsiFile file,
        Editor editor,
        SimpleReference<Integer> caretOffset,
        SimpleReference<Integer> caretAdvance,
        DataContext dataContext,
        EditorActionHandler originalHandler
    ) {
        int offset = caretOffset.get();
        if (editor instanceof EditorWindow) {
            file = InjectedLanguageManager.getInstance(file.getProject()).getTopLevelFile(file);
            editor = EditorWindow.getTopLevelEditor(editor);
            offset = editor.getCaretModel().getOffset();
        }
        if (!(file instanceof PyAstFile)) {
            return Result.Continue;
        }
        Boolean isSplitLine = DataManager.getInstance().loadFromDataContext(dataContext, SplitLineAction.SPLIT_LINE_KEY);
        if (isSplitLine != null) {
            return Result.Continue;
        }
        Document doc = editor.getDocument();
        PsiDocumentManager.getInstance(file.getProject()).commitDocument(doc);
        PsiElement element = file.findElementAt(offset);
        PsiElement prevElement = offset > 0 ? file.findElementAt(offset - 1) : null;
        CodeInsightSettings codeInsightSettings = CodeInsightSettings.getInstance();
        if (codeInsightSettings.JAVADOC_STUB_ON_ENTER) {
            PsiElement comment = element;
            if (comment == null && offset != 0) {
                comment = prevElement;
            }
            int expectedStringStart = editor.getCaretModel().getOffset() - 3; // """ or '''
            if (comment != null) {
                DocstringState state = canGenerateDocstring(comment, expectedStringStart, doc);
                if (state != DocstringState.NONE) {
                    insertDocStringStub(editor, comment, state);
                    return Result.Continue;
                }
            }
        }

        if (element == null) {
            return Result.Continue;
        }

        ASTNode node = element.getNode();
        PsiElement elementParent = element.getParent();
        IElementType nodeType = node.getElementType();
        if (nodeType == PyTokenTypes.LPAR) {
            elementParent = elementParent.getParent();
        }
        if (elementParent instanceof PyAstParenthesizedExpression || elementParent instanceof PyAstGeneratorExpression) {
            return Result.Continue;
        }

        PyAstStringElement stringElement = PsiTreeUtil.getParentOfType(element, PyAstStringElement.class, false);
        if (stringElement == null && prevElement == element) {
            return Result.Continue;
        }

        if (stringElement != null && (!stringElement.isFormatted() ||
            nodeType == PyTokenTypes.FSTRING_TEXT ||
            // Caret should be right before the opening brace of an f-string fragment
            nodeType == PyTokenTypes.FSTRING_FRAGMENT_START ||
            nodeType == PyTokenTypes.FSTRING_END)) {
            if (stringElement.isTripleQuoted() || nodeType == PyTokenTypes.DOCSTRING) {
                return Result.Continue;
            }
            if (prevElement != null && PsiTreeUtil.isAncestor(stringElement, prevElement, false)) {

                if (stringElement.getTextOffset() + stringElement.getPrefixLength() >= offset) {
                    return Result.Continue;
                }

                String pref = stringElement.getPrefix();
                String quote = stringElement.getQuote();

                // Don't split in the middle of an escape sequence
                boolean afterBackslash = "\\".equals(doc.getText(TextRange.from(offset - 1, 1)));
                boolean isEscapedQuote = quote.equals(doc.getText(TextRange.from(offset, 1))) && afterBackslash;
                boolean isEscapedBackslash = "\\".equals(doc.getText(TextRange.from(offset - 2, 1))) && afterBackslash;
                if (afterBackslash && !isEscapedQuote && !isEscapedBackslash) {
                    return Result.Continue;
                }

                myPostprocessShift = pref.length() + quote.length();

                boolean parenthesiseOnEnter = PyCodeInsightSettings.getInstance().PARENTHESISE_ON_ENTER;
                String delimiter = parenthesiseOnEnter ? "" : " \\";

                // TODO check for a non-parenthesized sequence pattern here
                if (PsiTreeUtil.getParentOfType(stringElement, PyEditorHandlerConfig.IMPLICIT_WRAP_CLASSES) != null) {
                    doc.insertString(offset, quote + pref + quote);
                    caretOffset.set(caretOffset.get() + 1);
                }
                else {
                    int insertionOffset = offset;
                    if (isEscapedQuote) {
                        // Preserve the escaped quote, split after it
                        caretOffset.set(caretOffset.get() + 1);
                        insertionOffset++;
                    }
                    if (parenthesiseOnEnter) {
                        parenthesise(stringElement, doc);
                        caretOffset.set(caretOffset.get() + 1);
                        insertionOffset++;
                    }
                    doc.insertString(insertionOffset, quote + delimiter + pref + quote);
                    caretOffset.set(caretOffset.get() + delimiter.length() + 1);
                }
                return Result.Continue;
            }
        }

        return checkInsertBackslash(file, caretOffset, dataContext, offset, doc);
    }

    @RequiredReadAction
    private static Result checkInsertBackslash(
        PsiFile file,
        SimpleReference<Integer> caretOffset,
        DataContext dataContext,
        int offset,
        Document doc
    ) {
        boolean autoWrapInProgress =
            DataManager.getInstance().loadFromDataContext(dataContext, AutoHardWrapHandler.AUTO_WRAP_LINE_IN_PROGRESS_KEY) != null;
        if (PyWhiteSpaceFormattingStrategy.needInsertBackslash(file, offset, autoWrapInProgress)) {
            PsiElement atCaret = file.findElementAt(offset);
            if (atCaret != null) {
                // Set of statements which can be parenthesised is a subset of statements which can be split by backslash
                if (PyCodeInsightSettings.getInstance().PARENTHESISE_ON_ENTER) {
                    PsiElement elementToWrap = findElementToParenthesise(atCaret);
                    if (elementToWrap != null) {
                        parenthesise(elementToWrap, doc);
                        caretOffset.set(caretOffset.get() + 1);
                        return Result.Continue;
                    }
                }
                doc.insertString(offset, "\\");
                caretOffset.set(caretOffset.get() + 1);
            }
        }
        return Result.Continue;
    }

    private static @Nullable PsiElement findElementToParenthesise(PsiElement nodeAtCaret) {
        PsiElement wrappable = nodeAtCaret;

        while (true) {
            PsiElement next = PsiTreeUtil.getParentOfType(wrappable, PyEditorHandlerConfig.CLASSES_TO_PARENTHESISE_ON_ENTER);
            if (next == null) {
                break;
            }
            wrappable = next;
        }

        return wrappable != nodeAtCaret ? wrappable : null;
    }

    private static void parenthesise(PsiElement wrappable, Document doc) {
        TextRange rangeToParenthesise;
        if (wrappable instanceof PyAstFromImportStatement fromImportStatement) {
            rangeToParenthesise = getRangeForPsiElementArray(fromImportStatement.getImportElements());
        }
        else if (wrappable instanceof PyAstWithStatement withStatement) {
            rangeToParenthesise = getRangeForPsiElementArray(withStatement.getWithItems());
        }
        else {
            rangeToParenthesise = wrappable.getTextRange();
        }
        if (rangeToParenthesise != null) {
            doc.insertString(rangeToParenthesise.getEndOffset(), ")");
            doc.insertString(rangeToParenthesise.getStartOffset(), "(");
        }
    }

    private static @Nullable TextRange getRangeForPsiElementArray(PsiElement[] items) {
        if (!ArrayUtil.isEmpty(items)) {
            PsiElement first = ArrayUtil.getFirstElement(items);
            PsiElement last = ArrayUtil.getLastElement(items);
            if (first != null && last != null) {
                return first.getTextRange().union(last.getTextRange());
            }
        }
        return null;
    }

    @RequiredReadAction
    private static void insertDocStringStub(Editor editor, PsiElement element, DocstringState state) {
        PyAstDocStringOwner docOwner = PsiTreeUtil.getParentOfType(element, PyAstDocStringOwner.class);
        if (docOwner != null) {
            int caretOffset = editor.getCaretModel().getOffset();
            Document document = editor.getDocument();
            String quotes = document.getText(TextRange.from(caretOffset - 3, 3));
            String docString = PyDocstringGenerator.forDocStringOwner(docOwner)
                .withInferredParameters(true)
                .withQuotes(quotes)
                .forceNewMode()
                .buildDocString();
            if (state == DocstringState.INCOMPLETE) {
                document.insertString(caretOffset, docString.substring(3));
            }
            else if (state == DocstringState.EMPTY) {
                document.replaceString(caretOffset, caretOffset + 3, docString.substring(3));
            }
        }
    }

    @Override
    @RequiredUIAccess
    public Result postProcessEnter(PsiFile file, Editor editor, DataContext dataContext) {
        if (!(file instanceof PyAstFile)) {
            return Result.Continue;
        }
        if (myPostprocessShift > 0) {
            editor.getCaretModel().moveCaretRelatively(myPostprocessShift, 0, false, false, false);
            myPostprocessShift = 0;
            return Result.Continue;
        }
        addGoogleDocStringSectionIndent(file, editor, editor.getCaretModel().getOffset());
        return super.postProcessEnter(file, editor, dataContext);
    }

    @RequiredUIAccess
    private static void addGoogleDocStringSectionIndent(PsiFile file, Editor editor, int offset) {
        Document document = editor.getDocument();
        PsiDocumentManager.getInstance(file.getProject()).commitDocument(document);
        PsiElement element = file.findElementAt(offset);
        if (element != null) {
            // Insert additional indentation after section header in Google code style docstrings
            PyAstStringLiteralExpression pyString = DocStringUtilCore.getParentDefinitionDocString(element);
            if (pyString != null) {
                String docStringText = pyString.getText();
                DocStringFormat format = DocStringParser.guessDocStringFormat(docStringText, pyString);
                if (format == DocStringFormat.GOOGLE && offset + 1 < document.getTextLength()) {
                    int lineNum = document.getLineNumber(offset);
                    TextRange lineRange = TextRange.create(document.getLineStartOffset(lineNum - 1), document.getLineEndOffset(lineNum - 1));
                    Matcher matcher = GoogleCodeStyleDocString.SECTION_HEADER.matcher(document.getText(lineRange));
                    if (matcher.matches() && SectionBasedDocString.isValidSectionTitle(matcher.group(1))) {
                        document.insertString(offset, GoogleCodeStyleDocStringBuilder.getDefaultSectionIndent(file));
                        editor.getCaretModel().moveCaretRelatively(2, 0, false, false, false);
                    }
                }
            }
        }
    }

    enum DocstringState {
        NONE,
        INCOMPLETE,
        EMPTY
    }

    @RequiredReadAction
    public static DocstringState canGenerateDocstring(PsiElement element, int firstQuoteOffset, Document document) {
        if (firstQuoteOffset < 0 || firstQuoteOffset > document.getTextLength() - 3) {
            return DocstringState.NONE;
        }
        String quotes = document.getText(TextRange.from(firstQuoteOffset, 3));
        if (!quotes.equals("\"\"\"") && !quotes.equals("'''")) {
            return DocstringState.NONE;
        }
        PyAstStringLiteralExpression pyString = DocStringUtilCore.getParentDefinitionDocString(element);
        if (pyString != null) {

            String nodeText = element.getText();
            int prefixLength = PyStringLiteralUtil.getPrefixLength(nodeText);
            nodeText = nodeText.substring(prefixLength);

            String literalText = pyString.getText();
            if (literalText.endsWith(nodeText) && nodeText.startsWith(quotes)) {
                if (firstQuoteOffset == pyString.getTextOffset() + prefixLength) {
                    PsiErrorElement error = PsiTreeUtil.getNextSiblingOfType(pyString, PsiErrorElement.class);
                    if (error == null) {
                        error = PsiTreeUtil.getNextSiblingOfType(pyString.getParent(), PsiErrorElement.class);
                    }
                    if (error != null) {
                        return DocstringState.INCOMPLETE;
                    }

                    if (nodeText.equals(quotes + quotes)) {
                        return DocstringState.EMPTY;
                    }

                    if (nodeText.length() < 6 || !nodeText.endsWith(quotes)) {
                        return DocstringState.INCOMPLETE;
                    }
                    // Sometimes if incomplete docstring is followed by another declaration with a docstring, it might be treated
                    // as complete docstring, because we can't understand that closing quotes actually belong to another docstring.
                    String docstringIndent = PyIndentUtil.getLineIndent(document, document.getLineNumber(firstQuoteOffset));
                    for (String line : LineTokenizer.tokenizeIntoList(nodeText, false)) {
                        String lineIndent = PyIndentUtil.getLineIndent(line);
                        String lineContent = line.substring(lineIndent.length());
                        if ((lineContent.startsWith("def ") || lineContent.startsWith("class ")) &&
                            docstringIndent.length() > lineIndent.length() && docstringIndent.startsWith(lineIndent)) {
                            return DocstringState.INCOMPLETE;
                        }
                    }
                }
            }
        }
        return DocstringState.NONE;
    }
}
