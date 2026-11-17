package com.jetbrains.python.ast.impl;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.ast.PyAstAssignmentStatement;
import com.jetbrains.python.ast.PyAstBreakStatement;
import com.jetbrains.python.ast.PyAstContinueStatement;
import com.jetbrains.python.ast.PyAstExpression;
import com.jetbrains.python.ast.PyAstExpressionStatement;
import com.jetbrains.python.ast.PyAstFunction;
import com.jetbrains.python.ast.PyAstListLiteralExpression;
import com.jetbrains.python.ast.PyAstLoopStatement;
import com.jetbrains.python.ast.PyAstParenthesizedExpression;
import com.jetbrains.python.ast.PyAstSequenceExpression;
import com.jetbrains.python.ast.PyAstStarExpression;
import com.jetbrains.python.ast.PyAstStatement;
import com.jetbrains.python.ast.PyAstStatementListContainer;
import com.jetbrains.python.ast.PyAstStatementWithElse;
import com.jetbrains.python.ast.PyAstStringLiteralExpression;
import com.jetbrains.python.ast.PyAstTupleExpression;
import com.jetbrains.python.ast.controlFlow.AstScopeOwner;
import consulo.annotation.access.RequiredReadAction;
import consulo.document.Document;
import consulo.document.util.TextRange;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtilCore.getScopeOwner;

/**
 * Assorted utility methods for Python code insight.
 * <p>
 * These methods don't depend on the Python runtime.
 *
 * @see PyPsiUtilsCore for utilities used in Python PSI API
 */
public final class PyUtilCore {
    private static final Pattern TYPE_COMMENT_PATTERN = Pattern.compile("# *type: *([^#]+) *(#.*)?");

    private PyUtilCore() {
    }

    /**
     * @see PyUtil#flattenedParensAndTuples
     */
    private static List<PyAstExpression> unfoldParentheses(
        @Nullable PyAstExpression[] targets,
        List<PyAstExpression> receiver,
        boolean unfoldListLiterals,
        boolean unfoldStarExpressions
    ) {
        // NOTE: this proliferation of instanceofs is not very beautiful. Maybe rewrite using a visitor.
        for (PyAstExpression exp : targets) {
            if (exp instanceof PyAstParenthesizedExpression parenthesizedExpression) {
                unfoldParentheses(
                    new PyAstExpression[]{parenthesizedExpression.getContainedExpression()},
                    receiver,
                    unfoldListLiterals,
                    unfoldStarExpressions
                );
            }
            else if (exp instanceof PyAstTupleExpression<?> tupleExpression) {
                unfoldParentheses(tupleExpression.getElements(), receiver, unfoldListLiterals, unfoldStarExpressions);
            }
            else if (exp instanceof PyAstListLiteralExpression listLiteralExpression && unfoldListLiterals) {
                unfoldParentheses(listLiteralExpression.getElements(), receiver, true, unfoldStarExpressions);
            }
            else if (exp instanceof PyAstStarExpression starExpression && unfoldStarExpressions) {
                unfoldParentheses(new PyAstExpression[]{starExpression.getExpression()}, receiver, unfoldListLiterals, true);
            }
            else if (exp != null) {
                receiver.add(exp);
            }
        }
        return receiver;
    }

    /**
     * Flattens the representation of every element in targets, and puts all results together.
     * Elements of every tuple nested in target item are brought to the top level: (a, (b, (c, d))) -> (a, b, c, d)
     * Typical usage: {@code flattenedParensAndTuples(some_tuple.getExpressions())}.
     *
     * @param targets target elements.
     * @return the list of flattened expressions.
     */
    @RequiredReadAction
    public static List<PyAstExpression> flattenedParensAndTuples(@Nullable PyAstExpression... targets) {
        return unfoldParentheses(targets, new ArrayList<>(targets.length), false, false);
    }

    @RequiredReadAction
    public static List<PyAstExpression> flattenedParensAndLists(@Nullable PyAstExpression... targets) {
        return unfoldParentheses(targets, new ArrayList<>(targets.length), true, true);
    }

    @RequiredReadAction
    public static List<PyAstExpression> flattenedParensAndStars(@Nullable PyAstExpression... targets) {
        return unfoldParentheses(targets, new ArrayList<>(targets.length), false, true);
    }

    @RequiredReadAction
    public static boolean onSameLine(PsiElement e1, PsiElement e2) {
        PsiFile firstFile = e1.getContainingFile();
        PsiFile secondFile = e2.getContainingFile();
        if (firstFile == null || secondFile == null) {
            return false;
        }
        Document document = firstFile.getFileDocument();
        if (document != secondFile.getFileDocument()) {
            return false;
        }
        return document.getLineNumber(e1.getTextOffset()) == document.getLineNumber(e2.getTextOffset());
    }

    @RequiredReadAction
    public static boolean isTopLevel(PsiElement element) {
        return getScopeOwner(element) instanceof PsiFile;
    }

    /**
     * Retrieves the document from {@link PsiDocumentManager} using the anchor PSI element and, if it's not null,
     * passes it to the consumer function.
     * <p>
     * The document is first released from pending PSI operations and then committed after the function has been applied
     * in a {@code try/finally} block, so that subsequent operations on PSI could be performed.
     *
     * @see PsiDocumentManager#doPostponedOperationsAndUnblockDocument
     * @see PsiDocumentManager#commitDocument
     * @see #updateDocumentUnblockedAndCommitted
     */
    @RequiredReadAction
    public static void updateDocumentUnblockedAndCommitted(PsiElement anchor, Consumer<? super Document> consumer) {
        PyUtilCore.<Object>updateDocumentUnblockedAndCommitted(anchor, document -> {
            consumer.accept(document);
            return null;
        });
    }

    @RequiredReadAction
    public static <T> @Nullable T updateDocumentUnblockedAndCommitted(PsiElement anchor, Function<? super Document, ? extends T> func) {
        PsiDocumentManager manager = PsiDocumentManager.getInstance(anchor.getProject());
        // manager.getDocument(anchor.getContainingFile()) doesn't work with intention preview
        Document document = anchor.getContainingFile().getViewProvider().getDocument();
        if (document != null) {
            manager.doPostponedOperationsAndUnblockDocument(document);
            try {
                return func.apply(document);
            }
            finally {
                manager.commitDocument(document);
            }
        }
        return null;
    }

    public static boolean isSpecialName(String name) {
        return name.length() > 4 && name.startsWith("__") && name.endsWith("__");
    }

    @RequiredReadAction
    public static @Nullable PyAstLoopStatement getCorrespondingLoop(PsiElement breakOrContinue) {
        return breakOrContinue instanceof PyAstContinueStatement || breakOrContinue instanceof PyAstBreakStatement
            ? getCorrespondingLoopImpl(breakOrContinue)
            : null;
    }

    @SuppressWarnings("unchecked")
    @RequiredReadAction
    private static @Nullable PyAstLoopStatement getCorrespondingLoopImpl(PsiElement element) {
        PyAstLoopStatement loop = PsiTreeUtil.getParentOfType(element, PyAstLoopStatement.class, true, AstScopeOwner.class);

        if (loop instanceof PyAstStatementWithElse statementWithElse
            && PsiTreeUtil.isAncestor(statementWithElse.getElsePart(), element, true)) {
            return getCorrespondingLoopImpl(loop);
        }

        return loop;
    }

    /**
     * @return true if passed {@code element} is a method (this means a function inside a class) named {@code __new__}.
     * @see PyUtil#isInitMethod
     * @see PyUtil#isInitOrNewMethod
     * @see PyUtil#turnConstructorIntoClass
     */
    @RequiredReadAction
    public static boolean isNewMethod(@Nullable PsiElement element) {
        PyAstFunction function = element instanceof PyAstFunction f ? f : null;
        return function != null && PyNames.NEW.equals(function.getName()) && function.getContainingClass() != null;
    }

    /**
     * @return true if passed {@code element} is a method (this means a function inside a class) named {@code __init__} or {@code __new__}.
     * @see PyUtil#isInitMethod
     * @see PyUtil#isNewMethod
     * @see PyUtil#turnConstructorIntoClass
     */
    @RequiredReadAction
    public static boolean isInitOrNewMethod(@Nullable PsiElement element) {
        PyAstFunction function = element instanceof PyAstFunction f ? f : null;
        if (function == null) {
            return false;
        }

        String name = function.getName();
        return (PyNames.INIT.equals(name) ||
            PyNames.NEW.equals(name)) && function.getContainingClass() != null;
    }

    /**
     * @return true if passed {@code element} is a method (this means a function inside a class) named {@code __init__},
     * {@code __init_subclass__}, or {@code __new__}.
     * @see PyUtil#isInitMethod
     * @see PyUtil#isNewMethod
     * @see PyUtil#turnConstructorIntoClass
     */
    @RequiredReadAction
    public static boolean isConstructorLikeMethod(@Nullable PsiElement element) {
        PyAstFunction function = element instanceof PyAstFunction f ? f : null;
        if (function == null) {
            return false;
        }

        String name = function.getName();
        return (PyNames.INIT_SUBCLASS.equals(name) ||
            PyNames.INIT.equals(name) ||
            PyNames.NEW.equals(name)) && function.getContainingClass() != null;
    }

    @RequiredReadAction
    public static boolean isStringLiteral(@Nullable PyAstStatement stmt) {
        if (stmt instanceof PyAstExpressionStatement expressionStatement) {
            PyAstExpression expr = expressionStatement.getExpression();
            if (expr instanceof PyAstStringLiteralExpression) {
                return true;
            }
        }
        return false;
    }

    /**
     * Counts initial underscores of an identifier.
     *
     * @param name identifier
     * @return 0 if null or no initial underscores found, 1 if there's only one underscore, 2 if there's two or more initial underscores.
     */
    public static int getInitialUnderscores(@Nullable String name) {
        return name == null ? 0 : name.startsWith("__") ? 2 : name.startsWith(PyNames.UNDERSCORE) ? 1 : 0;
    }

    @RequiredReadAction
    public static @Nullable List<String> strListValue(@Nullable PyAstExpression value) {
        PyAstExpression current = value;
        while (current instanceof PyAstParenthesizedExpression parenthesizedExpression) {
            current = parenthesizedExpression.getContainedExpression();
        }
        if (current instanceof PyAstSequenceExpression sequenceExpression) {
            PyAstExpression[] elements = sequenceExpression.getElements();
            List<String> result = new ArrayList<>(elements.length);
            for (PyAstExpression element : elements) {
                if (!(element instanceof PyAstStringLiteralExpression stringLiteralExpression)) {
                    return null;
                }
                result.add(stringLiteralExpression.getStringValue());
            }
            return result;
        }
        return null;
    }

    @RequiredReadAction
    public static boolean isAssignmentToModuleLevelDunderName(@Nullable PsiElement element) {
        if (element instanceof PyAstAssignmentStatement assignmentStatement && isTopLevel(element)) {
            PyAstExpression[] targets = assignmentStatement.getTargets();
            if (targets.length == 1) {
                String name = Objects.requireNonNull(targets[0]).getName();
                return name != null && isSpecialName(name);
            }
        }
        return false;
    }

    /**
     * Returns the line comment that immediately precedes statement list of the given compound statement. Python parser ensures
     * that it follows the statement header, i.e. it's directly after the colon, not on its own line.
     */
    @RequiredReadAction
    public static @Nullable PsiComment getCommentOnHeaderLine(PyAstStatementListContainer container) {
        return getHeaderEndAnchor(container) instanceof PsiComment comment ? comment : null;
    }

    @RequiredReadAction
    public static PsiElement getHeaderEndAnchor(PyAstStatementListContainer container) {
        PsiElement statementList = container.getStatementList();
        return Objects.requireNonNull(PsiTreeUtil.skipWhitespacesBackward(statementList));
    }

    /**
     * Checks that text of a comment starts with "# type:" prefix and returns trimmed type hint after it.
     * The trailing part is supposed to contain type annotation in PEP 484 compatible format and an optional
     * plain text comment separated from it with another "#".
     * <p>
     * For instance, for {@code # type: List[int]  # comment} it returns {@code List[int]}.
     * <p>
     * This method cannot return an empty string.
     *
     * @see #getTypeCommentValueRange
     */
    public static @Nullable String getTypeCommentValue(String text) {
        Matcher m = TYPE_COMMENT_PATTERN.matcher(text);
        if (m.matches()) {
            return StringUtil.nullize(m.group(1).trim());
        }
        return null;
    }

    /**
     * Returns the corresponding text range for a type hint as returned by {@link #getTypeCommentValue}.
     *
     * @see #getTypeCommentValue
     */
    public static @Nullable TextRange getTypeCommentValueRange(String text) {
        Matcher m = TYPE_COMMENT_PATTERN.matcher(text);
        if (m.matches()) {
            String hint = getTypeCommentValue(text);
            if (hint != null) {
                return TextRange.from(m.start(1), hint.length());
            }
        }
        return null;
    }

    @SafeVarargs
    @RequiredReadAction
    public static @Nullable PsiElement findPrevAtOffset(PsiFile psiFile, int caretOffset, Class<? extends PsiElement>... toSkip) {
        int offset = caretOffset;
        PsiElement element;
        if (offset < 0) {
            return null;
        }
        int lineStartOffset = 0;
        Document document = PsiDocumentManager.getInstance(psiFile.getProject()).getDocument(psiFile);
        if (document != null) {
            int lineNumber = document.getLineNumber(offset);
            lineStartOffset = document.getLineStartOffset(lineNumber);
        }
        do {
            offset--;
            element = psiFile.findElementAt(offset);
        }
        while (offset >= lineStartOffset && PsiTreeUtil.instanceOf(element, toSkip));
        return PsiTreeUtil.instanceOf(element, toSkip) ? null : element;
    }

    @RequiredReadAction
    public static @Nullable PsiElement findNonWhitespaceAtOffset(PsiFile psiFile, int caretOffset) {
        PsiElement element = findNextAtOffset(psiFile, caretOffset, PsiWhiteSpace.class);
        if (element == null) {
            element = findPrevAtOffset(psiFile, caretOffset - 1, PsiWhiteSpace.class);
        }
        return element;
    }

    @SafeVarargs
    @RequiredReadAction
    public static @Nullable PsiElement findNextAtOffset(PsiFile psiFile, int caretOffset, Class<? extends PsiElement>... toSkip) {
        int offset = caretOffset;
        PsiElement element = psiFile.findElementAt(offset);
        if (element == null) {
            return null;
        }

        Document document = PsiDocumentManager.getInstance(psiFile.getProject()).getDocument(psiFile);
        int lineEndOffset = 0;
        if (document != null) {
            int lineNumber = document.getLineNumber(offset);
            lineEndOffset = document.getLineEndOffset(lineNumber);
        }
        while (offset < lineEndOffset && PsiTreeUtil.instanceOf(element, toSkip)) {
            offset++;
            element = psiFile.findElementAt(offset);
        }
        return PsiTreeUtil.instanceOf(element, toSkip) ? null : element;
    }
}
