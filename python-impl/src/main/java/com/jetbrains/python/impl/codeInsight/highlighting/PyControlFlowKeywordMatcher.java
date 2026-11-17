// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.highlighting;

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.PyBreakStatement;
import com.jetbrains.python.psi.PyContinueStatement;
import com.jetbrains.python.psi.PyForStatement;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyLambdaExpression;
import com.jetbrains.python.psi.PyLoopStatement;
import com.jetbrains.python.psi.PyMatchStatement;
import com.jetbrains.python.psi.PyRaiseStatement;
import com.jetbrains.python.psi.PyRecursiveElementVisitor;
import com.jetbrains.python.psi.PyReturnStatement;
import com.jetbrains.python.psi.PyStatementList;
import com.jetbrains.python.psi.PyStatementPart;
import com.jetbrains.python.psi.PyWhileStatement;
import com.jetbrains.python.psi.PyYieldExpression;
import consulo.document.util.TextRange;
import consulo.language.ast.ASTNode;
import consulo.language.ast.IElementType;
import consulo.language.ast.TokenSet;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiUtilCore;
import consulo.language.psi.util.PsiTreeUtil;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Matches a control-flow keyword with the other related keywords of the same construct so that they
 * can be highlighted together, previewed when scrolled off screen and navigated between. Two kinds
 * of relations are recognized:
 * <ul>
 *  <li>parts of a multipart compound statement &mdash; `if`/`elif`/`else`, `for`/`else`,
 *    `while`/`else`, `try`/`except`/`else`/`finally`, `match`/`case`;</li>
 *  <li>a function header and the exit keywords inside its body &mdash; `def` with its `return`,
 *    `yield` and `raise` keywords;</li>
 *  <li>a loop header and the jump keywords inside its body &mdash; `for`/`while` with the `break` and
 *    `continue` keywords bound to it.</li>
 * </ul>
 * Shared by `PyControlFlowKeywordBraceHighlighter` (off-screen preview of the construct header) and
 * `PyControlFlowKeywordCodeBlockSupportHandler` (matching-keyword highlighting and "Move Caret to
 * Matching Brace" navigation between the parts of a compound statement).
 */
// @ApiStatus.Internal
public final class PyControlFlowKeywordMatcher {
    private PyControlFlowKeywordMatcher() {
    }

    /**
     * Leading keyword tokens of the multipart compound statements we navigate between.
     */
    private static final TokenSet COMPOUND_PART_KEYWORDS = TokenSet.create(
        PyTokenTypes.IF_KEYWORD,
        PyTokenTypes.ELIF_KEYWORD,
        PyTokenTypes.ELSE_KEYWORD,
        PyTokenTypes.FOR_KEYWORD,
        PyTokenTypes.WHILE_KEYWORD,
        PyTokenTypes.TRY_KEYWORD,
        PyTokenTypes.EXCEPT_KEYWORD,
        PyTokenTypes.FINALLY_KEYWORD,
        PyTokenTypes.MATCH_KEYWORD,
        PyTokenTypes.CASE_KEYWORD
    );

    /**
     * The function header keyword and the exit keywords matched with it.
     */
    private static final TokenSet FUNCTION_EXIT_KEYWORDS = TokenSet.create(
        PyTokenTypes.DEF_KEYWORD,
        PyTokenTypes.RETURN_KEYWORD,
        PyTokenTypes.YIELD_KEYWORD,
        PyTokenTypes.RAISE_KEYWORD
    );

    /**
     * The `break`/`continue` keywords matched with the header of the loop they belong to.
     */
    private static final TokenSet LOOP_EXIT_KEYWORDS = TokenSet.create(
        PyTokenTypes.BREAK_KEYWORD,
        PyTokenTypes.CONTINUE_KEYWORD
    );

    /**
     * Data holder: {@code keywords} are related keyword elements of the construct, in document order (the construct
     * header &mdash; `if`, `try`, `def`, &hellip; &mdash; comes first); {@code caretIndex} is the index in
     * {@code keywords} of the keyword the caret is on.
     */
    public static final class KeywordContext {
        public final List<PsiElement> keywords;
        public final int caretIndex;

        public KeywordContext(List<PsiElement> keywords, int caretIndex) {
            this.keywords = keywords;
            this.caretIndex = caretIndex;
        }

        public List<PsiElement> getKeywords() {
            return keywords;
        }

        public int getCaretIndex() {
            return caretIndex;
        }

        @Override
        public boolean equals(@Nullable Object o) {
            return this == o || o instanceof KeywordContext that && caretIndex == that.caretIndex && keywords.equals(that.keywords);
        }

        @Override
        public int hashCode() {
            return Objects.hash(keywords, caretIndex);
        }

        @Override
        public String toString() {
            return "KeywordContext(keywords=" + keywords + ", caretIndex=" + caretIndex + ")";
        }
    }

    /**
     * Returns the keyword context if {@code offset} (or the position just before it) is on a keyword that
     * participates in one of the recognized relations, or `null` otherwise. A ternary `if`/`else`, a
     * comprehension `for`/`if`, a `return`/`yield` outside a function and the like are intentionally
     * ignored.
     */
    public static @Nullable KeywordContext findKeywordContext(PsiFile file, int offset) {
        KeywordContext context = contextAtOffset(file, offset);
        if (context != null) {
            return context;
        }
        return offset > 0 ? contextAtOffset(file, offset - 1) : null;
    }

    private static @Nullable KeywordContext contextAtOffset(PsiFile file, int offset) {
        PsiElement leaf = file.findElementAt(offset);
        if (leaf == null) {
            return null;
        }
        List<PsiElement> keywords = keywordGroup(leaf);
        if (keywords == null) {
            return null;
        }
        int caretIndex = -1;
        for (int i = 0; i < keywords.size(); i++) {
            if (keywords.get(i).getTextRange().equals(leaf.getTextRange())) {
                caretIndex = i;
                break;
            }
        }
        if (caretIndex < 0) {
            return null;
        }
        return new KeywordContext(keywords, caretIndex);
    }

    private static @Nullable List<PsiElement> keywordGroup(PsiElement leaf) {
        IElementType type = PsiUtilCore.getElementType(leaf);
        if (COMPOUND_PART_KEYWORDS.contains(type)) {
            return compoundStatementKeywords(leaf);
        }
        else if (FUNCTION_EXIT_KEYWORDS.contains(type)) {
            return functionExitKeywords(leaf);
        }
        else if (LOOP_EXIT_KEYWORDS.contains(type)) {
            return loopExitKeywords(leaf);
        }
        else {
            return null;
        }
    }

    // region multipart compound statements

    /**
     * Text ranges of the keywords of the multipart compound statement that {@code keyword} opens a part of
     * (`if`/`elif`/`else`, `for`/`else`, `while`/`else`, `try`/`except`/`else`/`finally`,
     * `match`/`case`), in document order, or `null` if {@code keyword} is not such a keyword. Powers the
     * platform `codeBlockSupportHandler` (matching-keyword highlighting and "Move Caret to Matching
     * Brace").
     */
    public static @Nullable List<TextRange> compoundStatementKeywordRanges(PsiElement keyword) {
        if (!COMPOUND_PART_KEYWORDS.contains(PsiUtilCore.getElementType(keyword))) {
            return null;
        }
        List<PsiElement> keywords = compoundStatementKeywords(keyword);
        if (keywords == null) {
            return null;
        }
        List<TextRange> result = new ArrayList<>();
        for (PsiElement it : keywords) {
            result.add(it.getTextRange());
        }
        return result;
    }

    /**
     * Text range of the multipart compound statement {@code keyword} belongs to, or `null`.
     */
    public static @Nullable TextRange compoundStatementRange(PsiElement keyword) {
        if (!COMPOUND_PART_KEYWORDS.contains(PsiUtilCore.getElementType(keyword))) {
            return null;
        }
        PsiElement statement = enclosingCompoundStatement(keyword);
        return statement != null ? statement.getTextRange() : null;
    }

    private static @Nullable PsiElement enclosingCompoundStatement(PsiElement keyword) {
        PsiElement parent = keyword.getParent();
        // `if`/`elif`/`else`/`for`/`while`/`try`/`except`/`finally`/`case` keywords open a statement part
        if (parent instanceof PyStatementPart) {
            return parent.getParent();
        }
        // the bare `match` keyword is a direct token child of the match statement
        else if (parent instanceof PyMatchStatement) {
            return parent;
        }
        else {
            return null;
        }
    }

    private static @Nullable List<PsiElement> compoundStatementKeywords(PsiElement keyword) {
        PsiElement compoundStatement = enclosingCompoundStatement(keyword);
        if (compoundStatement == null) {
            return null;
        }
        List<PsiElement> result = new ArrayList<>();
        PsiElement child = compoundStatement.getFirstChild();
        while (child != null) {
            // direct keyword token child, e.g. the `match` keyword of a match statement
            if (COMPOUND_PART_KEYWORDS.contains(PsiUtilCore.getElementType(child))) {
                result.add(child);
            }
            else if (child instanceof PyStatementPart statementPart) {
                PsiElement leading = leadingCompoundKeyword(statementPart);
                if (leading != null) {
                    result.add(leading);
                }
            }
            child = child.getNextSibling();
        }
        return result;
    }

    // endregion

    // region function header and its exit statements

    private static @Nullable List<PsiElement> functionExitKeywords(PsiElement keyword) {
        PyFunction function;
        if (PsiUtilCore.getElementType(keyword) == PyTokenTypes.DEF_KEYWORD) {
            function = keyword.getParent() instanceof PyFunction pyFunction ? pyFunction : null;
        }
        else {
            // a `return`/`yield`/`raise` keyword belongs to its nearest enclosing function
            function = PsiTreeUtil.getParentOfType(keyword, PyFunction.class);
        }
        if (function == null) {
            return null;
        }

        PsiElement def = keywordChild(function, PyTokenTypes.DEF_KEYWORD);
        if (def == null) {
            return null;
        }
        List<PsiElement> result = new ArrayList<>();
        result.add(def);
        function.getStatementList().accept(new PyRecursiveElementVisitor() {
            // do not descend into nested functions/lambdas: their exits belong to their own `def`
            @Override
            public void visitPyFunction(PyFunction node) {
            }

            @Override
            public void visitPyLambdaExpression(PyLambdaExpression node) {
            }

            @Override
            public void visitPyReturnStatement(PyReturnStatement node) {
                addIfNotNull(result, keywordChild(node, PyTokenTypes.RETURN_KEYWORD));
                super.visitPyReturnStatement(node);
            }

            @Override
            public void visitPyYieldExpression(PyYieldExpression node) {
                addIfNotNull(result, keywordChild(node, PyTokenTypes.YIELD_KEYWORD));
                super.visitPyYieldExpression(node);
            }

            @Override
            public void visitPyRaiseStatement(PyRaiseStatement node) {
                addIfNotNull(result, keywordChild(node, PyTokenTypes.RAISE_KEYWORD));
                super.visitPyRaiseStatement(node);
            }
        });
        return result;
    }

    // endregion

    // region loop header and its break/continue statements

    private static @Nullable List<PsiElement> loopExitKeywords(PsiElement keyword) {
        PsiElement statement = keyword.getParent();
        PyLoopStatement loop;
        if (statement instanceof PyBreakStatement breakStatement) {
            loop = breakStatement.getLoopStatement();
        }
        else if (statement instanceof PyContinueStatement continueStatement) {
            loop = continueStatement.getLoopStatement();
        }
        else {
            loop = null;
        }
        if (loop == null) {
            return null;
        }

        PsiElement header = loopHeaderKeyword(loop);
        if (header == null) {
            return null;
        }
        PyStatementList body = loopBody(loop);
        if (body == null) {
            return null;
        }
        List<PsiElement> result = new ArrayList<>();
        result.add(header);
        body.accept(new PyRecursiveElementVisitor() {
            // a `break`/`continue` binds to its nearest enclosing loop; stay out of nested loops and functions
            @Override
            public void visitPyForStatement(PyForStatement node) {
            }

            @Override
            public void visitPyWhileStatement(PyWhileStatement node) {
            }

            @Override
            public void visitPyFunction(PyFunction node) {
            }

            @Override
            public void visitPyLambdaExpression(PyLambdaExpression node) {
            }

            @Override
            public void visitPyBreakStatement(PyBreakStatement node) {
                addIfNotNull(result, keywordChild(node, PyTokenTypes.BREAK_KEYWORD));
            }

            @Override
            public void visitPyContinueStatement(PyContinueStatement node) {
                addIfNotNull(result, keywordChild(node, PyTokenTypes.CONTINUE_KEYWORD));
            }
        });
        return result;
    }

    private static @Nullable PsiElement loopHeaderKeyword(PyLoopStatement loop) {
        if (loop instanceof PyForStatement forStatement) {
            return keywordChild(forStatement.getForPart(), PyTokenTypes.FOR_KEYWORD);
        }
        else if (loop instanceof PyWhileStatement whileStatement) {
            return keywordChild(whileStatement.getWhilePart(), PyTokenTypes.WHILE_KEYWORD);
        }
        else {
            return null;
        }
    }

    private static @Nullable PyStatementList loopBody(PyLoopStatement loop) {
        if (loop instanceof PyForStatement forStatement) {
            return forStatement.getForPart().getStatementList();
        }
        else if (loop instanceof PyWhileStatement whileStatement) {
            return whileStatement.getWhilePart().getStatementList();
        }
        else {
            return null;
        }
    }

    // endregion

    private static @Nullable PsiElement leadingCompoundKeyword(PyStatementPart part) {
        PsiElement child = part.getFirstChild();
        while (child != null) {
            if (COMPOUND_PART_KEYWORDS.contains(PsiUtilCore.getElementType(child))) {
                return child;
            }
            child = child.getNextSibling();
        }
        return null;
    }

    private static @Nullable PsiElement keywordChild(PsiElement element, IElementType tokenType) {
        ASTNode node = element.getNode().findChildByType(tokenType);
        return node != null ? node.getPsi() : null;
    }

    private static void addIfNotNull(List<PsiElement> list, @Nullable PsiElement element) {
        if (element != null) {
            list.add(element);
        }
    }
}
