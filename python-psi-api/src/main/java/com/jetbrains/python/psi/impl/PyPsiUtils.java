// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl;

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.ast.impl.PyPsiUtilsCore;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyElementType;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyExpressionCodeFragment;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFromImportStatement;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.PyKeywordArgument;
import com.jetbrains.python.psi.PyPassStatement;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PySequenceExpression;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.PyStatementList;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.document.util.TextRange;
import consulo.language.ast.ASTNode;
import consulo.language.ast.TokenSet;
import consulo.language.impl.psi.stub.StubBasedPsiElementBase;
import consulo.language.inject.InjectedLanguageManager;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiFileSystemItem;
import consulo.language.psi.PsiNamedElement;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.psi.util.QualifiedName;
import consulo.language.psi.stub.IStubElementType;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.util.collection.ContainerUtil;
import consulo.util.io.FileUtil;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PyPsiUtils {
    private static final Logger LOG = Logger.getInstance(PyPsiUtils.class.getName());

    private PyPsiUtils() {
    }

    /**
     * Finds the closest comma after the element skipping any whitespaces in-between.
     */
    @RequiredReadAction
    public static @Nullable PsiElement getPrevComma(PsiElement element) {
        PsiElement prevNode = getPrevNonWhitespaceSibling(element);
        return prevNode != null && prevNode.getNode().getElementType() == PyTokenTypes.COMMA ? prevNode : null;
    }

    /**
     * Finds first non-whitespace sibling before given PSI element.
     */
    public static @Nullable PsiElement getPrevNonWhitespaceSibling(@Nullable PsiElement element) {
        return PsiTreeUtil.skipWhitespacesBackward(element);
    }

    /**
     * Finds first non-whitespace sibling before given AST node.
     */
    public static @Nullable ASTNode getPrevNonWhitespaceSibling(ASTNode node) {
        return skipSiblingsBackward(node, TokenSet.WHITE_SPACE);
    }

    /**
     * Finds first sibling that is neither comment, nor whitespace before given element.
     *
     * @param strict prohibit returning element itself
     */
    public static @Nullable PsiElement getPrevNonCommentSibling(@Nullable PsiElement start, boolean strict) {
        if (!strict && !(start instanceof PsiWhiteSpace || start instanceof PsiComment)) {
            return start;
        }
        return PsiTreeUtil.skipWhitespacesAndCommentsBackward(start);
    }

    /**
     * Finds the closest comma after the element skipping any whitespaces in-between.
     */
    @RequiredReadAction
    public static @Nullable PsiElement getNextComma(PsiElement element) {
        PsiElement nextNode = getNextNonWhitespaceSibling(element);
        return nextNode != null && nextNode.getNode().getElementType() == PyTokenTypes.COMMA ? nextNode : null;
    }

    /**
     * Finds first non-whitespace sibling after given PSI element.
     */
    public static @Nullable PsiElement getNextNonWhitespaceSibling(@Nullable PsiElement element) {
        return PsiTreeUtil.skipWhitespacesForward(element);
    }

    /**
     * Returns the first non-whitespace sibling preceding the given element but within its line boundaries.
     */
    @RequiredReadAction
    public static @Nullable PsiElement getPrevNonWhitespaceSiblingOnSameLine(PsiElement element) {
        PsiElement cur = element.getPrevSibling();
        while (cur != null) {
            if (!(cur instanceof PsiWhiteSpace)) {
                return cur;
            }
            else if (cur.textContains('\n')) {
                break;
            }
            cur = cur.getPrevSibling();
        }
        return null;
    }

    /**
     * Finds first non-whitespace sibling after given AST node.
     */
    public static @Nullable ASTNode getNextNonWhitespaceSibling(ASTNode after) {
        return skipSiblingsForward(after, TokenSet.WHITE_SPACE);
    }

    /**
     * Finds first sibling that is neither comment, nor whitespace after given element.
     *
     * @param strict prohibit returning element itself
     */
    public static @Nullable PsiElement getNextNonCommentSibling(@Nullable PsiElement start, boolean strict) {
        return PyPsiUtilsCore.getNextNonCommentSibling(start, strict);
    }

    /**
     * Finds first token after given element that doesn't consist solely of spaces and is not empty (e.g. error marker).
     *
     * @param ignoreComments ignore commentaries as well
     */
    @RequiredReadAction
    public static @Nullable PsiElement getNextSignificantLeaf(@Nullable PsiElement element, boolean ignoreComments) {
        PsiElement current = element;
        while (current != null && StringUtil.isEmptyOrSpaces(current.getText()) || ignoreComments && current instanceof PsiComment) {
            current = PsiTreeUtil.nextLeaf(current);
        }
        return current;
    }

    /**
     * Finds first token before given element that doesn't consist solely of spaces and is not empty (e.g. error marker).
     *
     * @param ignoreComments ignore commentaries as well
     */
    @RequiredReadAction
    public static @Nullable PsiElement getPrevSignificantLeaf(@Nullable PsiElement element, boolean ignoreComments) {
        PsiElement current = element;
        while (current != null && StringUtil.isEmptyOrSpaces(current.getText()) || ignoreComments && current instanceof PsiComment) {
            current = PsiTreeUtil.prevLeaf(current);
        }
        return current;
    }

    /**
     * Finds the closest comma looking for the next comma first and then for the preceding one.
     */
    @RequiredReadAction
    public static @Nullable PsiElement getAdjacentComma(PsiElement element) {
        PsiElement nextComma = getNextComma(element);
        return nextComma != null ? nextComma : getPrevComma(element);
    }

    /**
     * Works similarly to {@link PsiTreeUtil#skipSiblingsForward}, but for AST nodes.
     */
    public static @Nullable ASTNode skipSiblingsForward(@Nullable ASTNode node, TokenSet types) {
        if (node == null) {
            return null;
        }
        ASTNode next = node.getTreeNext();
        while (next != null) {
            if (!types.contains(next.getElementType())) {
                return next;
            }
            next = next.getTreeNext();
        }
        return null;
    }

    /**
     * Works similarly to {@link PsiTreeUtil#skipSiblingsBackward}, but for AST nodes.
     */
    public static @Nullable ASTNode skipSiblingsBackward(@Nullable ASTNode node, TokenSet types) {
        if (node == null) {
            return null;
        }
        ASTNode prev = node.getTreePrev();
        while (prev != null) {
            if (!types.contains(prev.getElementType())) {
                return prev;
            }
            prev = prev.getTreePrev();
        }
        return null;
    }

    /**
     * Returns first child psi element with specified element type or {@code null} if no such element exists.
     * Semantically it's the same as {@code getChildByFilter(element, TokenSet.create(type), 0)}.
     *
     * @param element tree parent node
     * @param type    element type expected
     * @return child element described
     */
    public static @Nullable PsiElement getFirstChildOfType(PsiElement element, PyElementType type) {
        return PyPsiUtilsCore.getFirstChildOfType(element, type);
    }

    /**
     * Returns child element in the psi tree
     *
     * @param filter  Types of expected child
     * @param number  number
     * @param element tree parent node
     * @return PsiElement - child psiElement
     */
    public static @Nullable PsiElement getChildByFilter(PsiElement element, TokenSet filter, int number) {
        return PyPsiUtilsCore.getChildByFilter(element, filter, number);
    }

    public static void removeElements(PsiElement... elements) {
        ASTNode parentNode = elements[0].getParent().getNode();
        LOG.assertTrue(parentNode != null);
        for (PsiElement element : elements) {
            parentNode.removeChild(element.getNode());
        }
    }

    public static @Nullable PsiElement getStatement(PsiElement element) {
        PyElement compStatement = getStatementList(element);
        if (compStatement == null) {
            return null;
        }
        return getParentRightBefore(element, compStatement);
    }

    public static @Nullable PyElement getStatementList(@Nullable PsiElement element) {
        return element instanceof PyFile || element instanceof PyStatementList
            ? (PyElement)element
            : PsiTreeUtil.getParentOfType(element, PyFile.class, PyStatementList.class);
    }

    /**
     * Returns ancestor of the element that is also direct child of the given super parent.
     *
     * @param element     element to start search from
     * @param superParent direct parent of the desired ancestor
     * @return described element or {@code null} if it doesn't exist
     */
    public static @Nullable PsiElement getParentRightBefore(PsiElement element, PsiElement superParent) {
        return PyPsiUtilsCore.getParentRightBefore(element, superParent);
    }

    @RequiredReadAction
    public static int getElementIndentation(PsiElement element) {
        PsiElement compStatement = getStatementList(element);
        assert compStatement != null;
        PsiElement statement = getParentRightBefore(element, compStatement);
        if (statement == null) {
            return 0;
        }
        PsiElement sibling = statement.getPrevSibling();
        if (sibling == null) {
            sibling = compStatement.getPrevSibling();
        }
        String whitespace = sibling instanceof PsiWhiteSpace ? sibling.getText() : "";
        int i = whitespace.lastIndexOf("\n");
        return i != -1 ? whitespace.length() - i - 1 : 0;
    }

    @RequiredWriteAction
    public static void removeRedundantPass(PyStatementList statementList) {
        PyStatement[] statements = statementList.getStatements();
        if (statements.length > 1) {
            for (PyStatement statement : statements) {
                if (statement instanceof PyPassStatement) {
                    statement.delete();
                }
            }
        }
    }

    public static boolean isMethodContext(@Nullable PsiElement element) {
        PsiNamedElement parent = PsiTreeUtil.getParentOfType(element, PyFile.class, PyFunction.class, PyClass.class);
        // In case if element is inside method which is inside class
        return parent instanceof PyFunction &&
            PsiTreeUtil.getParentOfType(parent, PyFile.class, PyFunction.class, PyClass.class) instanceof PyClass;
    }


    @RequiredReadAction
    public static PsiElement getRealContext(PsiElement element) {
        assertValid(element);
        PsiFile file = element.getContainingFile();
        if (file instanceof PyExpressionCodeFragment codeFragment) {
            PsiElement context = codeFragment.getRealContext();
            return context != null ? context : element;
        }
        else {
            return element;
        }
    }

    /**
     * Removes comma closest to the given child node along with any whitespaces around. First following comma is checked and only
     * then, if it doesn't exists, preceding one.
     *
     * @param element parent node
     * @param child   child node comma should be adjacent to
     * @see #getAdjacentComma
     */
    @RequiredWriteAction
    public static void deleteAdjacentCommaWithWhitespaces(PsiElement element, PsiElement child) {
        PsiElement commaNode = getAdjacentComma(child);
        if (commaNode != null) {
            PsiElement nextNonWhitespace = getNextNonWhitespaceSibling(commaNode);
            PsiElement last = nextNonWhitespace == null ? element.getLastChild() : nextNonWhitespace.getPrevSibling();
            PsiElement prevNonWhitespace = getPrevNonWhitespaceSibling(commaNode);
            PsiElement first = prevNonWhitespace == null ? element.getFirstChild() : prevNonWhitespace.getNextSibling();
            element.deleteChildRange(first, last);
        }
    }

    /**
     * Returns comments preceding given elements as pair of the first and the last such comments. Comments should not be
     * separated by any empty line.
     *
     * @param element element comments should be adjacent to
     * @return described range or {@code null} if there are no such comments
     */
    @RequiredReadAction
    public static List<PsiComment> getPrecedingComments(PsiElement element) {
        return getPrecedingComments(element, true);
    }

    @RequiredReadAction
    public static List<PsiComment> getPrecedingComments(PsiElement element, boolean stopAtBlankLine) {
        return getPrecedingCommentsAndAnchor(element, stopAtBlankLine, true).getFirst();
    }

    @RequiredReadAction
    private static Pair<List<PsiComment>, @Nullable PsiElement> getPrecedingCommentsAndAnchor(
        PsiElement element,
        boolean stopAtBlankLine,
        boolean strict
    ) {
        List<PsiComment> result = new ArrayList<>();
        PsiElement cursor = element instanceof PsiComment && !strict ? element : element.getPrevSibling();
        while (true) {
            int newLinesCount = 0;
            while (cursor instanceof PsiWhiteSpace) {
                newLinesCount += StringUtil.getLineBreakCount(cursor.getText());
                cursor = cursor.getPrevSibling();
            }
            if ((stopAtBlankLine && newLinesCount > 1) || !(cursor instanceof PsiComment comment)) {
                break;
            }
            else {
                result.add(comment);
            }
            cursor = cursor.getPrevSibling();
        }
        Collections.reverse(result);
        return Pair.create(result, cursor);
    }

    /**
     * Return blank-line-separated blocks of consecutive comments preceding the given element.
     * <p>
     * <p>
     * For instance, for the following fragment, it will return two blocks of one and two comments.
     *
     * <pre>
     * # comment
     *
     * # comment
     * # comment
     * def func():
     *      pass
     * </pre>
     * <p>
     * <p>
     * Note that in the following case it will additionally return an empty list of comments as the last element
     * to distinguish between the cases when there is a blank line above the provided element and when there is not.
     *
     * <pre>
     * # comment
     *
     * def func():
     *     pass
     * </pre>
     */
    @RequiredReadAction
    public static List<List<PsiComment>> getPrecedingCommentBlocks(PsiElement element) {
        List<List<PsiComment>> blocks = new ArrayList<>();
        PsiElement anchor = element;
        do {
            Pair<List<PsiComment>, @Nullable PsiElement> blockAndAnchor = getPrecedingCommentsAndAnchor(anchor, true, false);
            anchor = blockAndAnchor.getSecond();
            List<PsiComment> block = blockAndAnchor.getFirst();
            if (!block.isEmpty() || anchor instanceof PsiComment) {
                blocks.add(block);
            }
        }
        while (anchor instanceof PsiComment);
        Collections.reverse(blocks);
        return blocks;
    }

    public static int findArgumentIndex(PyCallExpression call, @Nullable PsiElement argument) {
        PyExpression[] args = call.getArguments();
        for (int i = 0; i < args.length; i++) {
            PyExpression expression = args[i];
            if (expression instanceof PyKeywordArgument keywordArgument) {
                expression = keywordArgument.getValueExpression();
            }
            expression = flattenParens(expression);
            if (expression == argument) {
                return i;
            }
        }
        return -1;
    }

    @RequiredReadAction
    public static @Nullable PyTargetExpression getAttribute(PyFile file, String name) {
        PyTargetExpression attr = file.findTopLevelAttribute(name);
        if (attr == null) {
            for (PyFromImportStatement element : file.getFromImports()) {
                PyReferenceExpression expression = element.getImportSource();
                if (expression == null) {
                    continue;
                }
                PsiElement resolved = expression.getReference().resolve();
                if (resolved instanceof PyFile resolvedFile && resolved != file) {
                    return resolvedFile.findTopLevelAttribute(name);
                }
            }
        }
        return attr;
    }

    @RequiredReadAction
    public static List<@Nullable PyExpression> getAttributeValuesFromFile(PyFile file, String name) {
        List<@Nullable PyExpression> result = new ArrayList<>();
        PyTargetExpression attr = file.findTopLevelAttribute(name);
        if (attr != null) {
            sequenceToList(result, attr.findAssignedValue());
        }
        return result;
    }

    public static void sequenceToList(List<? super @Nullable PyExpression> result, @Nullable PyExpression value) {
        PyExpression flattened = flattenParens(value);
        if (flattened instanceof PySequenceExpression sequenceExpression) {
            ContainerUtil.addAll(result, sequenceExpression.getElements());
        }
        else {
            result.add(flattened);
        }
    }

    public static List<@Nullable String> getStringValues(@Nullable PyExpression[] elements) {
        List<@Nullable String> results = new ArrayList<>();
        for (PyExpression element : elements) {
            if (element instanceof PyStringLiteralExpression stringLiteralExpression) {
                results.add(stringLiteralExpression.getStringValue());
            }
        }
        return results;
    }

    public static @Nullable PyExpression flattenParens(@Nullable PyExpression expr) {
        return (PyExpression)PyPsiUtilsCore.flattenParens(expr);
    }

    public static @Nullable String strValue(@Nullable PyExpression expression) {
        return PyPsiUtilsCore.strValue(expression);
    }

    @RequiredReadAction
    public static boolean isBefore(PsiElement element, PsiElement element2) {
        // TODO: From RubyPsiUtil, should be moved to PsiTreeUtil
        return element.getTextOffset() <= element2.getTextOffset();
    }

    public static @Nullable QualifiedName asQualifiedName(@Nullable PyExpression expr) {
        return PyPsiUtilsCore.asQualifiedName(expr);
    }

    public static PyExpression getFirstQualifier(PyQualifiedExpression expr) {
        PyExpression qualifier = expr.getQualifier();
        if (qualifier instanceof PyQualifiedExpression qualifiedExpression) {
            return getFirstQualifier(qualifiedExpression);
        }
        return expr;
    }

    public static String toPath(@Nullable PyQualifiedExpression expr) {
        if (expr != null) {
            QualifiedName qName = expr.asQualifiedName();
            if (qName != null) {
                return qName.toString();
            }
            String name = expr.getName();
            if (name != null) {
                return name;
            }
        }
        return "";
    }

    public static @Nullable QualifiedName asQualifiedName(PyQualifiedExpression expr) {
        return PyPsiUtilsCore.asQualifiedName(expr);
    }

    /**
     * Wrapper for {@link consulo.language.psi.PsiUtilCore#ensureValid(PsiElement)} that skips nulls
     */
    @RequiredReadAction
    public static void assertValid(@Nullable PsiElement element) {
        PyPsiUtilsCore.assertValid(element);
    }

    public static void assertValid(Module module) {
        LOG.assertTrue(!module.isDisposed(), String.format("Module %s is disposed", module));
    }

    public static @Nullable PsiFileSystemItem getFileSystemItem(PsiElement element) {
        if (element instanceof PsiFileSystemItem fileSystemItem) {
            return fileSystemItem;
        }
        return element.getContainingFile();
    }

    public static @Nullable String getContainingFilePath(PsiElement element) {
        VirtualFile file;
        if (element instanceof PsiFileSystemItem fileSystemItem) {
            file = fileSystemItem.getVirtualFile();
        }
        else {
            file = element.getContainingFile().getVirtualFile();
        }
        if (file != null) {
            return FileUtil.toSystemDependentName(file.getPath());
        }
        return null;
    }

    /**
     * Checks if specified file contains passed source in top-level import in stub-safe way.
     * Does not process scopes inside the file.
     *
     * @param file   file whose imports should be visited
     * @param source qualified name separated by dots that is looking for in imports
     * @return true if specified file contains passed source in top-level import.
     */
    public static boolean containsImport(PyFile file, String source) {
        QualifiedName sourceQName = QualifiedName.fromDottedString(source);

        List<@Nullable QualifiedName> names = new ArrayList<>();
        for (PyFromImportStatement fromImport : file.getFromImports()) {
            names.add(fromImport.getImportSourceQName());
        }
        for (PyImportElement importTarget : file.getImportTargets()) {
            names.add(importTarget.getImportedQName());
        }
        for (QualifiedName name : names) {
            if (name != null && name.matchesPrefix(sourceQName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns text of the given PSI element. Unlike obvious {@link PsiElement#getText()} this method unescapes text of the element if latter
     * belongs to injected code fragment using {@link InjectedLanguageManager#getUnescapedText(PsiElement)}.
     *
     * @param element PSI element which text is needed
     * @return text of the element with any host escaping removed
     */
    @RequiredReadAction
    public static String getElementTextWithoutHostEscaping(PsiElement element) {
        InjectedLanguageManager manager = InjectedLanguageManager.getInstance(element.getProject());
        if (manager.isInjectedFragment(element.getContainingFile())) {
            return manager.getUnescapedText(element);
        }
        else {
            return element.getText();
        }
    }

    @RequiredReadAction
    public static @Nullable String getStringValue(@Nullable PsiElement o) {
        if (o == null) {
            return null;
        }
        if (o instanceof PyStringLiteralExpression stringLiteralExpression) {
            return stringLiteralExpression.getStringValue();
        }
        else {
            return o.getText();
        }
    }

    @RequiredReadAction
    public static TextRange getStringValueTextRange(PsiElement element) {
        if (element instanceof PyStringLiteralExpression stringLiteralExpression) {
            return stringLiteralExpression.getStringValueTextRanges().get(0);
        }
        else {
            return new TextRange(0, element.getTextLength());
        }
    }

    @RequiredReadAction
    public static @Nullable PsiComment findSameLineComment(PsiElement elem) {
        // If `elem` is a compound multi-line element, stick to its first line nonetheless
        PsiElement next = PsiTreeUtil.getDeepestFirst(elem);
        do {
            if (next instanceof PsiComment comment) {
                return comment;
            }
            if (next != elem && next.textContains('\n')) {
                break;
            }
            next = PsiTreeUtil.nextLeaf(next);
        }
        while (next != null);
        return null;
    }

    /**
     * Consulo: JB checks {@code element instanceof StubBasedPsiElement} to tell stub-backed PSI (classes, functions, targets...) from
     * plain AST PSI. In Consulo every {@code StubBasedPsiElementBase} implements {@code StubBasedPsiElement}, and all Python PSI
     * extends it, so that check is always true here. An element is stub-backed when it has a stub, or when its element type is a
     * stub element type.
     */
    public static boolean isStubBasedElement(@Nullable PsiElement element) {
        if (!(element instanceof StubBasedPsiElementBase<?> stubBased)) {
            return false;
        }
        if (stubBased.getStub() != null) {
            return true;
        }
        return stubBased.getNode().getElementType() instanceof IStubElementType;
    }
}
