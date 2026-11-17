// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.intentions;

import com.jetbrains.python.impl.psi.PyUtil.StringNodeInfo;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyDocStringOwner;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyParenthesizedExpression;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PyTupleExpression;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.editor.intention.PsiElementBaseIntentionAction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * User: catherine
 * Intention to convert triple quoted string to single-quoted
 * For instance:
 * from:
 * a = """This line is ok,
 *      but "this" includes far too much
 *      whitespace at the start"""
 * to:
 * a = ("This line is ok," "\n"
 *      "but \"this\" includes far too much" "\n"
 *      "whitespace at the start")
 */
// TODO consulo: ModCommand API (PsiUpdateModCommandAction) missing, JB logic adapted to PsiElementBaseIntentionAction
@ExtensionImpl
@IntentionMetaData(ignoreId = "python.PyConvertTripleQuotedStringIntention", categories = "Python", fileExtensions = "py")
public final class PyConvertTripleQuotedStringIntention extends PsiElementBaseIntentionAction {
    public PyConvertTripleQuotedStringIntention() {
        setText(PyLocalize.intnTripleQuotedString());
    }

    private static @Nullable PyStringLiteralExpression findElement(PsiElement leaf) {
        PsiElement current = leaf;
        while (current != null) {
            if (current instanceof PyStringLiteralExpression candidate) {
                return candidate;
            }
            if (current instanceof PsiFile) {
                return null;
            }
            current = current.getParent();
        }
        return null;
    }

    @Override
    public boolean isAvailable(Project project, Editor editor, PsiElement leaf) {
        PyStringLiteralExpression element = findElement(leaf);
        if (element == null) {
            return false;
        }
        LocalizeValue text = getPresentation(element, editor.getCaretModel().getOffset());
        if (text == null) {
            return false;
        }
        setText(text);
        return true;
    }

    @Override
    public void invoke(Project project, Editor editor, PsiElement leaf) throws IncorrectOperationException {
        PyStringLiteralExpression element = findElement(leaf);
        if (element == null) {
            return;
        }
        invoke(element, editor);
    }


    private @Nullable LocalizeValue getPresentation(PyStringLiteralExpression element, int offset) {
        PyDocStringOwner docStringOwner = PsiTreeUtil.getParentOfType(element, PyDocStringOwner.class);
        if (docStringOwner != null) {
            if (docStringOwner.getDocStringExpression() == element) {
                return null;
            }
        }
        for (StringNodeInfo info : extractStringNodesInfo(element)) {
            if (info.isTripleQuoted() && info.isTerminated() && info.getNode().getTextRange().contains(offset)) {
                return PyLocalize.intnTripleQuotedString();
            }
        }
        return null;
    }

    private void invoke(PyStringLiteralExpression element, Editor editor) {
        PyElementGenerator elementGenerator = PyElementGenerator.getInstance(element.getProject());
        StringBuilder result = new StringBuilder();
        List<StringNodeInfo> nodeInfos = extractStringNodesInfo(element);
        for (int i = 0; i < nodeInfos.size(); i++) {
            StringNodeInfo info = nodeInfos.get(i);
            List<String> lines = StringUtil.split(info.getContent(), "\n", true, false);
            boolean lastLineExcluded = false;
            if (lines.size() > 1 && lines.get(lines.size() - 1).isEmpty()) {
                lastLineExcluded = true;
                lines = lines.subList(0, lines.size() - 1);
            }

            boolean inLastNode = i == nodeInfos.size() - 1;
            for (int j = 0; j < lines.size(); j++) {
                String line = lines.get(j);
                boolean inLastLine = j == lines.size() - 1;

                if (info.isRaw()) {
                    appendSplittedRawStringLine(result, info, line);
                    if (!inLastLine || lastLineExcluded) {
                        result.append(" ").append(info.getSingleQuote()).append("\\n").append(info.getSingleQuote());
                    }
                }
                else {
                    result.append(info.getPrefix());
                    result.append(info.getSingleQuote());
                    result.append(convertToValidSubString(line, info.getSingleQuote(), info.isTripleQuoted()));
                    if (!inLastLine || lastLineExcluded) {
                        result.append("\\n");
                    }
                    result.append(info.getSingleQuote());
                }
                if (!(inLastNode && inLastLine)) {
                    result.append("\n");
                }
            }
        }
        if (result.indexOf("\n") >= 0) {
            result.insert(0, "(");
            result.append(")");
        }
        PyExpression expression = elementGenerator.createExpressionFromText(LanguageLevel.forElement(element), result.toString());

        PsiElement parent = element.getParent();
        if (expression instanceof PyParenthesizedExpression &&
                (parent instanceof PyParenthesizedExpression ||
                parent instanceof PyTupleExpression ||
                parent instanceof PyArgumentList && ArrayUtil.getFirstElement(((PyArgumentList)parent).getArguments()) == element)) {
            expression = ((PyParenthesizedExpression)expression).getContainedExpression();
        }
        if (expression instanceof PyStringLiteralExpression && ((PyStringLiteralExpression)expression).isDocString()) {
            expression = elementGenerator.createStringLiteralAlreadyEscaped(result.toString());
        }
        if (expression != null) {
            element.replace(expression);
        }
    }

    private static void appendSplittedRawStringLine(StringBuilder result, StringNodeInfo info, String line) {
        boolean singleQuoteUsed = false, doubleQuoteUsed = false;
        int chunkStart = 0;
        boolean firstChunk = true;
        for (int k = 0; k <= line.length(); k++) {
            if (k < line.length()) {
                singleQuoteUsed |= line.charAt(k) == '\'';
                doubleQuoteUsed |= line.charAt(k) == '"';
            }
            char chunkQuote;
            if ((k == line.length() || line.charAt(k) == '\'') && doubleQuoteUsed) {
                chunkQuote = '\'';
                doubleQuoteUsed = false;
            }
            else if ((k == line.length() || line.charAt(k) == '"') && singleQuoteUsed) {
                chunkQuote = '"';
                singleQuoteUsed = false;
            }
            else if (k == line.length()) {
                chunkQuote = info.getSingleQuote();
            }
            else {
                continue;
            }
            if (!firstChunk) {
                result.append(" ");
            }
            result.append(info.getPrefix()).append(chunkQuote).append(line, chunkStart, k).append(chunkQuote);
            firstChunk = false;
            chunkStart = k;
        }
    }

    private static String convertToValidSubString(String content, char newQuote, boolean isMultiline) {
        return isMultiline ? StringUtil.escapeChar(content, newQuote) : content;
    }

    private static List<StringNodeInfo> extractStringNodesInfo(PyStringLiteralExpression expression) {
        return ContainerUtil.map(expression.getStringNodes(), node -> new StringNodeInfo(node));
    }
}

