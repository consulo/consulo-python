/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.impl.editor;

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.impl.codeInsight.PyCodeInsightSettings;
import com.jetbrains.python.impl.documentation.docstrings.*;
import com.jetbrains.python.impl.psi.PyIndentUtil;
import com.jetbrains.python.impl.psi.impl.PyStringLiteralExpressionImpl;
import com.jetbrains.python.psi.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.util.LineTokenizer;
import consulo.codeEditor.Editor;
import consulo.codeEditor.action.EditorActionHandler;
import consulo.dataContext.DataContext;
import consulo.dataContext.DataManager;
import consulo.document.Document;
import consulo.document.util.TextRange;
import consulo.language.ast.ASTNode;
import consulo.language.ast.TokenType;
import consulo.language.editor.CodeInsightSettings;
import consulo.language.editor.action.EnterHandlerDelegateAdapter;
import consulo.language.editor.inject.EditorWindow;
import consulo.language.impl.ast.TreeUtil;
import consulo.language.inject.InjectedLanguageManager;
import consulo.language.psi.*;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.util.lang.ref.SimpleReference;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.regex.Matcher;

/**
 * @author yole
 */
@ExtensionImpl
public class PythonEnterHandler extends EnterHandlerDelegateAdapter {
    private int myPostprocessShift = 0;

    public static final Class[] IMPLICIT_WRAP_CLASSES = new Class[]{
        PyListLiteralExpression.class,
        PySetLiteralExpression.class,
        PyDictLiteralExpression.class,
        PyDictLiteralExpression.class,
        PyParenthesizedExpression.class,
        PyArgumentList.class,
        PyParameterList.class
    };

    private static final Class[] WRAPPABLE_CLASSES = new Class[]{
        PsiComment.class,
        PyParenthesizedExpression.class,
        PyListCompExpression.class,
        PyDictCompExpression.class,
        PySetCompExpression.class,
        PyDictLiteralExpression.class,
        PySetLiteralExpression.class,
        PyListLiteralExpression.class,
        PyArgumentList.class,
        PyParameterList.class,
        PyDecoratorList.class,
        PySliceExpression.class,
        PySubscriptionExpression.class,
        PyGeneratorExpression.class
    };

    @Override
    @RequiredReadAction
    public Result preprocessEnter(
        @Nonnull PsiFile file,
        @Nonnull Editor editor,
        @Nonnull SimpleReference<Integer> caretOffset,
        @Nonnull SimpleReference<Integer> caretAdvance,
        @Nonnull DataContext dataContext,
        EditorActionHandler originalHandler
    ) {
        int offset = caretOffset.get();
        if (editor instanceof EditorWindow) {
            file = InjectedLanguageManager.getInstance(file.getProject()).getTopLevelFile(file);
            editor = EditorWindow.getTopLevelEditor(editor);
            offset = editor.getCaretModel().getOffset();
        }
        if (!(file instanceof PyFile)) {
            return Result.Continue;
        }
        Boolean isSplitLine = DataManager.getInstance().loadFromDataContext(
            dataContext,
            consulo.ide.impl.idea.openapi.editor.actions.SplitLineAction.SPLIT_LINE_KEY
        );
        if (isSplitLine != null) {
            return Result.Continue;
        }
        Document doc = editor.getDocument();
        PsiDocumentManager.getInstance(file.getProject()).commitDocument(doc);
        PsiElement element = file.findElementAt(offset);
        CodeInsightSettings codeInsightSettings = CodeInsightSettings.getInstance();
        if (codeInsightSettings.JAVADOC_STUB_ON_ENTER) {
            PsiElement comment = element;
            if (comment == null && offset != 0) {
                comment = file.findElementAt(offset - 1);
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

        PsiElement elementParent = element.getParent();
        if (element.getNode().getElementType() == PyTokenTypes.LPAR) {
            elementParent = elementParent.getParent();
        }
        if (elementParent instanceof PyParenthesizedExpression || elementParent instanceof PyGeneratorExpression) {
            return Result.Continue;
        }

        if (offset > 0 && !(PyTokenTypes.STRING_NODES.contains(element.getNode().getElementType()))) {
            PsiElement prevElement = file.findElementAt(offset - 1);
            if (prevElement == element) {
                return Result.Continue;
            }
        }

        if (PyTokenTypes.TRIPLE_NODES.contains(element.getNode().getElementType())
            || element.getNode().getElementType() == PyTokenTypes.DOCSTRING) {
            return Result.Continue;
        }

        PsiElement prevElement = file.findElementAt(offset - 1);
        PyStringLiteralExpression string =
            PsiTreeUtil.findElementOfClassAtOffset(file, offset, PyStringLiteralExpression.class, false);

        if (string != null && prevElement != null
            && PyTokenTypes.STRING_NODES.contains(prevElement.getNode().getElementType())
            && string.getTextOffset() < offset && !(element.getNode() instanceof PsiWhiteSpace)) {
            String stringText = element.getText();
            int prefixLength = PyStringLiteralExpressionImpl.getPrefixLength(stringText);
            if (string.getTextOffset() + prefixLength >= offset) {
                return Result.Continue;
            }
            String pref = element.getText().substring(0, prefixLength);
            String quote = element.getText().substring(prefixLength, prefixLength + 1);
            boolean nextIsBackslash = "\\".equals(doc.getText(TextRange.create(offset - 1, offset)));
            boolean isEscapedQuote = quote.equals(doc.getText(TextRange.create(offset, offset + 1))) && nextIsBackslash;
            boolean isEscapedBackslash = "\\".equals(doc.getText(TextRange.create(offset - 2, offset - 1))) && nextIsBackslash;
            if (nextIsBackslash && !isEscapedQuote && !isEscapedBackslash) {
                return Result.Continue;
            }

            StringBuilder replacementString = new StringBuilder();
            myPostprocessShift = prefixLength + quote.length();

            //noinspection unchecked
            if (PsiTreeUtil.getParentOfType(string, IMPLICIT_WRAP_CLASSES) != null) {
                replacementString.append(quote).append(pref).append(quote);
                doc.insertString(offset, replacementString);
                caretOffset.set(caretOffset.get() + 1);
                return Result.Continue;
            }
            else {
                if (isEscapedQuote) {
                    replacementString.append(quote);
                    caretOffset.set(caretOffset.get() + 1);
                }
                replacementString.append(quote).append(" \\").append(pref);
                if (!isEscapedQuote) {
                    replacementString.append(quote);
                }
                doc.insertString(offset, replacementString.toString());
                caretOffset.set(caretOffset.get() + 3);
                return Result.Continue;
            }
        }

        if (!PyCodeInsightSettings.getInstance().INSERT_BACKSLASH_ON_WRAP) {
            return Result.Continue;
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
        boolean autoWrapInProgress = DataManager.getInstance().loadFromDataContext(
            dataContext,
            consulo.ide.impl.idea.codeInsight.editorActions.AutoHardWrapHandler.AUTO_WRAP_LINE_IN_PROGRESS_KEY
        ) != null;
        if (needInsertBackslash(file, offset, autoWrapInProgress)) {
            doc.insertString(offset, "\\");
            caretOffset.set(caretOffset.get() + 1);
        }
        return Result.Continue;
    }

    @RequiredReadAction
    public static boolean needInsertBackslash(PsiFile file, int offset, boolean autoWrapInProgress) {
        if (offset > 0) {
            PsiElement beforeCaret = file.findElementAt(offset - 1);
            if (beforeCaret instanceof PsiWhiteSpace && beforeCaret.getText().indexOf('\\') >= 0) {
                // we've got a backslash at EOL already, don't need another one
                return false;
            }
        }
        PsiElement atCaret = file.findElementAt(offset);
        if (atCaret == null) {
            return false;
        }
        ASTNode nodeAtCaret = atCaret.getNode();
        return needInsertBackslash(nodeAtCaret, autoWrapInProgress);
    }

    @RequiredReadAction
    public static boolean needInsertBackslash(ASTNode nodeAtCaret, boolean autoWrapInProgress) {
        PsiElement statementBefore = findStatementBeforeCaret(nodeAtCaret);
        PsiElement statementAfter = findStatementAfterCaret(nodeAtCaret);
        if (statementBefore != statementAfter) {  // Enter pressed at statement break
            return false;
        }
        if (statementBefore == null) {  // empty file
            return false;
        }

        if (PsiTreeUtil.hasErrorElements(statementBefore)) {
            if (!autoWrapInProgress) {
                // code is already bad, don't mess it up even further
                return false;
            }
            // if we're in middle of typing, it's expected that we will have error elements
        }

        if (inFromImportParentheses(statementBefore, nodeAtCaret.getTextRange().getStartOffset())) {
            return false;
        }

        PsiElement wrappableBefore = findWrappable(nodeAtCaret, true);
        PsiElement wrappableAfter = findWrappable(nodeAtCaret, false);
        if (!(wrappableBefore instanceof PsiComment)) {
            while (wrappableBefore != null) {
                @SuppressWarnings("unchecked")
                PsiElement next = PsiTreeUtil.getParentOfType(wrappableBefore, WRAPPABLE_CLASSES);
                if (next == null) {
                    break;
                }
                wrappableBefore = next;
            }
        }
        if (!(wrappableAfter instanceof PsiComment)) {
            while (wrappableAfter != null) {
                @SuppressWarnings("unchecked")
                PsiElement next = PsiTreeUtil.getParentOfType(wrappableAfter, WRAPPABLE_CLASSES);
                if (next == null) {
                    break;
                }
                wrappableAfter = next;
            }
        }
        if (wrappableBefore instanceof PsiComment || wrappableAfter instanceof PsiComment) {
            return false;
        }
        if (wrappableAfter == null) {
            return !(wrappableBefore instanceof PyDecoratorList);
        }
        return wrappableBefore != wrappableAfter;
    }

    private static void insertDocStringStub(Editor editor, PsiElement element, DocstringState state) {
        PyDocStringOwner docOwner = PsiTreeUtil.getParentOfType(element, PyDocStringOwner.class);
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

    @Nullable
    @SuppressWarnings("unchecked")
    private static PsiElement findWrappable(ASTNode nodeAtCaret, boolean before) {
        PsiElement wrappable = before ? findBeforeCaret(nodeAtCaret, WRAPPABLE_CLASSES) : findAfterCaret(nodeAtCaret, WRAPPABLE_CLASSES);
        if (wrappable == null) {
            PsiElement emptyTuple = before
                ? findBeforeCaret(nodeAtCaret, PyTupleExpression.class)
                : findAfterCaret(nodeAtCaret, PyTupleExpression.class);
            if (emptyTuple != null && emptyTuple.getNode().getFirstChildNode().getElementType() == PyTokenTypes.LPAR) {
                wrappable = emptyTuple;
            }
        }
        return wrappable;
    }

    @Nullable
    @SuppressWarnings("unchecked")
    private static PsiElement findStatementBeforeCaret(ASTNode node) {
        return findBeforeCaret(node, PyStatement.class);
    }

    @Nullable
    @SuppressWarnings("unchecked")
    private static PsiElement findStatementAfterCaret(ASTNode node) {
        return findAfterCaret(node, PyStatement.class);
    }

    @SafeVarargs
    private static PsiElement findBeforeCaret(ASTNode atCaret, Class<? extends PsiElement>... classes) {
        while (atCaret != null) {
            atCaret = TreeUtil.prevLeaf(atCaret);
            if (atCaret != null && atCaret.getElementType() != TokenType.WHITE_SPACE) {
                return getNonStrictParentOfType(atCaret.getPsi(), classes);
            }
        }
        return null;
    }

    @SafeVarargs
    private static PsiElement findAfterCaret(ASTNode atCaret, Class<? extends PsiElement>... classes) {
        while (atCaret != null) {
            if (atCaret.getElementType() != TokenType.WHITE_SPACE) {
                return getNonStrictParentOfType(atCaret.getPsi(), classes);
            }
            atCaret = TreeUtil.nextLeaf(atCaret);
        }
        return null;
    }

    @Nullable
    @SafeVarargs
    @SuppressWarnings("unchecked")
    private static <T extends PsiElement> T getNonStrictParentOfType(@Nonnull PsiElement element, @Nonnull Class<? extends T>... classes) {
        PsiElement run = element;
        while (run != null) {
            for (Class<? extends T> aClass : classes) {
                if (aClass.isInstance(run)) {
                    return (T)run;
                }
            }
            if (run instanceof PsiFile || run instanceof PyStatementList) {
                break;
            }
            run = run.getParent();
        }

        return null;
    }

    @RequiredReadAction
    private static boolean inFromImportParentheses(PsiElement statement, int offset) {
        if (!(statement instanceof PyFromImportStatement fromImportStatement)) {
            return false;
        }
        PsiElement leftParen = fromImportStatement.getLeftParen();
        return leftParen != null && offset >= leftParen.getTextRange().getEndOffset();
    }

    @Override
    @RequiredUIAccess
    public Result postProcessEnter(@Nonnull PsiFile file, @Nonnull Editor editor, @Nonnull DataContext dataContext) {
        if (!(file instanceof PyFile)) {
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
    private static void addGoogleDocStringSectionIndent(@Nonnull PsiFile file, @Nonnull Editor editor, int offset) {
        Document document = editor.getDocument();
        PsiDocumentManager.getInstance(file.getProject()).commitDocument(document);
        PsiElement element = file.findElementAt(offset);
        if (element != null) {
            // Insert additional indentation after section header in Google code style docstrings
            PyStringLiteralExpression pyString = DocStringUtil.getParentDefinitionDocString(element);
            if (pyString != null) {
                String docStringText = pyString.getText();
                DocStringFormat format = DocStringUtil.guessDocStringFormat(docStringText, pyString);
                if (format == DocStringFormat.GOOGLE && offset + 1 < document.getTextLength()) {
                    int lineNum = document.getLineNumber(offset);
                    TextRange lineRange =
                        TextRange.create(document.getLineStartOffset(lineNum - 1), document.getLineEndOffset(lineNum - 1));
                    Matcher matcher = GoogleCodeStyleDocString.SECTION_HEADER.matcher(document.getText(lineRange));
                    if (matcher.matches() && SectionBasedDocString.isValidSectionTitle(matcher.group(1))) {
                        document.insertString(offset, GoogleCodeStyleDocStringBuilder.getDefaultSectionIndent(file.getProject()));
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

    @Nonnull
    @RequiredReadAction
    public static DocstringState canGenerateDocstring(@Nonnull PsiElement element, int firstQuoteOffset, @Nonnull Document document) {
        if (firstQuoteOffset < 0 || firstQuoteOffset > document.getTextLength() - 3) {
            return DocstringState.NONE;
        }
        String quotes = document.getText(TextRange.from(firstQuoteOffset, 3));
        if (!quotes.equals("\"\"\"") && !quotes.equals("'''")) {
            return DocstringState.NONE;
        }
        PyStringLiteralExpression pyString = DocStringUtil.getParentDefinitionDocString(element);
        if (pyString != null) {

            String nodeText = element.getText();
            int prefixLength = PyStringLiteralExpressionImpl.getPrefixLength(nodeText);
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
