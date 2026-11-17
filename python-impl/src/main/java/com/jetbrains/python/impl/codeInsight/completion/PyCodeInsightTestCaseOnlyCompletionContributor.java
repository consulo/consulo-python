// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.completion;

import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.PythonLanguage;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.document.Document;
import consulo.language.Language;
import consulo.language.editor.completion.CompletionContributor;
import consulo.language.editor.completion.CompletionParameters;
import consulo.language.editor.completion.CompletionProvider;
import consulo.language.editor.completion.CompletionResultSet;
import consulo.language.editor.completion.CompletionType;
import consulo.language.editor.completion.lookup.LookupElementBuilder;
import consulo.language.pattern.PlatformPatterns;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiUtilCore;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.ProcessingContext;
import org.jspecify.annotations.Nullable;

import static com.jetbrains.python.impl.codeInsight.completion.PyTestAssertionParserSymbols.FIXME_KEYWORD;
import static com.jetbrains.python.impl.codeInsight.completion.PyTestAssertionParserSymbols.MARKER_CORNER;
import static com.jetbrains.python.impl.codeInsight.completion.PyTestAssertionParserSymbols.MARKER_LEFT;
import static com.jetbrains.python.impl.codeInsight.completion.PyTestAssertionParserUtils.getDefaultSeverityNames;
import static com.jetbrains.python.impl.codeInsight.completion.PyTestAssertionParserUtils.isAssertionMarker;
import static com.jetbrains.python.impl.codeInsight.completion.PyTestAssertionParserUtils.scanTokenEnd;
import static com.jetbrains.python.impl.codeInsight.completion.PyTestAssertionParserUtils.skipWhitespace;
import static com.jetbrains.python.impl.codeInsight.completion.PyTestAssertionParserUtils.skipWhitespaceAndGuides;


@ExtensionImpl(id = "pyPyCodeInsightTestCaseOnlyCompletionContributor")
public class PyCodeInsightTestCaseOnlyCompletionContributor extends CompletionContributor {

    public PyCodeInsightTestCaseOnlyCompletionContributor() {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement(),
            Provider.INSTANCE
        );
    }

    @Override
    public Language getLanguage() {
        return PythonLanguage.INSTANCE;
    }

    private enum CompletionContext {
        COMMENT_ONLY_ASSERTION_START,
        COMMENT_ONLY_ASSERTION_START_WHITESPACE,
        INLINE_ASSERTION_START,
        COMMENT_ONLY_ASSERTION_AFTER_MARKER,
        ANY_ASSERTION_AFTER_TYPE,
    }

    private static final class Provider implements CompletionProvider {
        private static final Provider INSTANCE = new Provider();

        private static final String PY_CODE_INSIGHT_TEST_CASE = "PyCodeInsightTestCase";

        @Override
        @RequiredReadAction
        public void addCompletions(CompletionParameters parameters, ProcessingContext context, CompletionResultSet result) {
            PsiElement position = parameters.getPosition();
            if (!isInPyCodeInsightTestCaseContext(position)) {
                return;
            }

            PsiFile containingFile = position.getContainingFile();
            if (containingFile == null || containingFile.getFileType() != PythonFileType.INSTANCE) {
                return;
            }

            if (!isInsidePythonComment(position)) {
                return;
            }

            CompletionContext completionContext = getCompletionContext(parameters);
            if (completionContext == null) {
                return;
            }
            CompletionResultSet resultWithoutPrefix = result.withPrefixMatcher("");
            CompletionResultSet resultWithCurrentTokenPrefix = result.withPrefixMatcher(getCurrentTokenPrefix(parameters));

            switch (completionContext) {
                case COMMENT_ONLY_ASSERTION_START -> addMarkerElements(resultWithoutPrefix, true);
                case COMMENT_ONLY_ASSERTION_START_WHITESPACE -> addMarkerElements(resultWithoutPrefix, false);
                case INLINE_ASSERTION_START, COMMENT_ONLY_ASSERTION_AFTER_MARKER -> addAssertionTypeElements(resultWithCurrentTokenPrefix);
                case ANY_ASSERTION_AFTER_TYPE -> addFixmeElement(resultWithCurrentTokenPrefix);
            }
        }

        private static @Nullable CompletionContext getCompletionContext(CompletionParameters parameters) {
            Document document = parameters.getEditor().getDocument();
            int offset = parameters.getOffset();
            if (offset < 0 || offset > document.getTextLength()) {
                return null;
            }

            int lineNumber = document.getLineNumber(offset);
            int lineStartOffset = document.getLineStartOffset(lineNumber);
            String prefix = document.getCharsSequence().subSequence(lineStartOffset, offset).toString();
            int hashIndex = prefix.indexOf(PyTestAssertionParserSymbols.COMMENT_CHAR);
            if (hashIndex < 0) {
                return null;
            }

            String beforeHash = prefix.substring(0, hashIndex);
            String afterHashBeforeCaret = prefix.substring(hashIndex + 1);
            boolean isCommentOnlyLine = beforeHash.isBlank();

            if (isCommentOnlyLine) {
                return getCommentOnlyCompletionContext(afterHashBeforeCaret);
            }
            else {
                return getInlineCompletionContext(afterHashBeforeCaret);
            }
        }

        private static @Nullable CompletionContext getCommentOnlyCompletionContext(String afterHashBeforeCaret) {
            int cursor = skipWhitespaceAndGuides(afterHashBeforeCaret);

            if (cursor >= afterHashBeforeCaret.length()) {
                if (afterHashBeforeCaret.isEmpty()) {
                    return CompletionContext.COMMENT_ONLY_ASSERTION_START;
                }
                else {
                    return CompletionContext.COMMENT_ONLY_ASSERTION_START_WHITESPACE;
                }
            }

            char marker = afterHashBeforeCaret.charAt(cursor);
            if (!isAssertionMarker(marker)) {
                return null;
            }

            String afterMarker = afterHashBeforeCaret.substring(cursor + 1);
            String afterMarkerTrimmed = trimStart(afterMarker);

            if (afterMarkerTrimmed.isEmpty()) {
                return CompletionContext.COMMENT_ONLY_ASSERTION_AFTER_MARKER;
            }

            if (hasCompletedTypeToken(afterMarkerTrimmed)) {
                return CompletionContext.ANY_ASSERTION_AFTER_TYPE;
            }
            else {
                return CompletionContext.COMMENT_ONLY_ASSERTION_AFTER_MARKER;
            }
        }

        private static @Nullable CompletionContext getInlineCompletionContext(String afterHashBeforeCaret) {
            if (afterHashBeforeCaret.isEmpty()) {
                return CompletionContext.INLINE_ASSERTION_START;
            }

            int cursor = skipWhitespace(afterHashBeforeCaret, 0);
            if (cursor >= afterHashBeforeCaret.length()) {
                return CompletionContext.COMMENT_ONLY_ASSERTION_START_WHITESPACE;
            }

            char firstNonWhitespace = afterHashBeforeCaret.charAt(cursor);
            if (isAssertionMarker(firstNonWhitespace)) {
                String afterMarker = trimStart(afterHashBeforeCaret.substring(cursor + 1));

                if (afterMarker.isEmpty()) {
                    return CompletionContext.COMMENT_ONLY_ASSERTION_AFTER_MARKER;
                }

                if (hasCompletedTypeToken(afterMarker)) {
                    return CompletionContext.ANY_ASSERTION_AFTER_TYPE;
                }
                else {
                    return CompletionContext.COMMENT_ONLY_ASSERTION_AFTER_MARKER;
                }
            }

            if (hasCompletedTypeToken(trimStart(afterHashBeforeCaret))) {
                return CompletionContext.ANY_ASSERTION_AFTER_TYPE;
            }
            else {
                return null;
            }
        }

        private static String getCurrentTokenPrefix(CompletionParameters parameters) {
            Document document = parameters.getEditor().getDocument();
            int offset = parameters.getOffset();
            if (offset <= 0 || offset > document.getTextLength()) {
                return "";
            }

            int lineNumber = document.getLineNumber(offset);
            int lineStartOffset = document.getLineStartOffset(lineNumber);
            String prefix = document.getCharsSequence().subSequence(lineStartOffset, offset).toString();

            int lastWhitespace = -1;
            for (int i = prefix.length() - 1; i >= 0; i--) {
                if (Character.isWhitespace(prefix.charAt(i))) {
                    lastWhitespace = i;
                    break;
                }
            }
            int tokenStart = lastWhitespace + 1;
            return prefix.substring(tokenStart);
        }

        private static boolean hasCompletedTypeToken(String text) {
            int tokenEnd = scanTokenEnd(text, 0);
            if (tokenEnd <= 0) {
                return false;
            }

            return tokenEnd < text.length() && Character.isWhitespace(text.charAt(tokenEnd));
        }

        // Kotlin String.trimStart()
        private static String trimStart(String text) {
            int i = 0;
            while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
                i++;
            }
            return text.substring(i);
        }

        @RequiredReadAction
        private static boolean isInsidePythonComment(PsiElement position) {
            if (PsiTreeUtil.getParentOfType(position, PsiComment.class, false) != null) {
                return true;
            }

            PsiFile file = position.getContainingFile();
            if (file == null) {
                return false;
            }
            int offsetBeforeCaret = position.getTextRange().getStartOffset() - 1;
            if (offsetBeforeCaret < 0) {
                return false;
            }

            return PsiTreeUtil.getParentOfType(PsiUtilCore.getElementAtOffset(file, offsetBeforeCaret), PsiComment.class, false) != null;
        }

        private static void addAssertionTypeElements(CompletionResultSet result) {
            for (PyTestAssertionType assertionType : PyTestAssertionType.values()) {
                result.addElement(
                    LookupElementBuilder
                        .create(assertionType.name())
                        .withTypeText("PyTestAssertion", true)
                );
            }

            for (String severityName : getDefaultSeverityNames()) {
                result.addElement(
                    LookupElementBuilder
                        .create(severityName)
                        .withTypeText("PyTestAssertion", true)
                );
            }
        }

        private static void addMarkerElements(CompletionResultSet result, boolean includeLeftMarker) {
            result.addElement(
                LookupElementBuilder
                    .create(String.valueOf(MARKER_CORNER))
                    .withTypeText("PyTestAssertion", true)
                    .withTailText(" indicates the symbol in the line above")
            );

            if (includeLeftMarker) {
                result.addElement(
                    LookupElementBuilder
                        .create(String.valueOf(MARKER_LEFT))
                        .withTypeText("PyTestAssertion", true)
                        .withTailText(" indicates the very first symbol in the line above")
                );
            }
        }

        private static void addFixmeElement(CompletionResultSet result) {
            result.addElement(
                LookupElementBuilder
                    .create(FIXME_KEYWORD)
                    .withTypeText("PyTestAssertion", true)
                    .withTailText(" followed by the fixed value")
            );
        }

        private static boolean isInPyCodeInsightTestCaseContext(PsiElement element) {
            PsiFile containingFile = element.getContainingFile();
            PsiElement context = containingFile != null ? containingFile.getContext() : null;
            if (context == null) {
                return false;
            }
            return isInsideJavaPyCodeInsightTestCase(context) ||
                isInsideKotlinPyCodeInsightTestCase(context);
        }

        private static boolean isInsideJavaPyCodeInsightTestCase(PsiElement context) {
            PsiElement psiClass = findParentByPsiClassName(context, "PsiClass");
            if (psiClass == null) {
                return false;
            }
            return containsElementWithText(psiClass, PY_CODE_INSIGHT_TEST_CASE);
        }

        private static boolean isInsideKotlinPyCodeInsightTestCase(PsiElement context) {
            PsiElement ktClass = findParentByPsiClassName(context, "KtClass");
            if (ktClass == null) {
                return false;
            }
            return containsElementWithText(ktClass, PY_CODE_INSIGHT_TEST_CASE);
        }

        private static @Nullable PsiElement findParentByPsiClassName(PsiElement element, String className) {
            // We deliberately compare only based on names to avoid dependencies to Java/Kotlin modules
            PsiElement current = element;
            while (current != null) {
                if (current.getClass().getSimpleName().equals(className)) {
                    return current;
                }
                current = current.getParent();
            }
            return null;
        }

        private static boolean containsElementWithText(PsiElement element, String text) {
            if (element.getText().equals(text)) {
                return true;
            }
            for (PsiElement it : element.getChildren()) {
                if (containsElementWithText(it, text)) {
                    return true;
                }
            }
            return false;
        }
    }
}
