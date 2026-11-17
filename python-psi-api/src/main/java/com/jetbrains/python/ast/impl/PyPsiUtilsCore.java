// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ast.impl;

import com.jetbrains.python.ast.PyAstElement;
import com.jetbrains.python.ast.PyAstExpression;
import com.jetbrains.python.ast.PyAstParenthesizedExpression;
import com.jetbrains.python.ast.PyAstQualifiedExpression;
import com.jetbrains.python.ast.PyAstReferenceExpression;
import com.jetbrains.python.ast.PyAstStringLiteralExpression;
import com.jetbrains.python.psi.PyElementType;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.ast.ASTNode;
import consulo.language.ast.TokenSet;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiUtilCore;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.psi.util.QualifiedName;
import consulo.util.collection.ArrayUtil;
import org.jspecify.annotations.Nullable;

import java.util.LinkedList;
import java.util.List;

public final class PyPsiUtilsCore {
    private PyPsiUtilsCore() {
    }

    @SuppressWarnings("unchecked")
    @RequiredReadAction
    public static <T extends PyAstElement> T[] nodesToPsi(ASTNode[] nodes, T[] array) {
        T[] psiElements = ArrayUtil.newArray(ArrayUtil.getComponentType(array), nodes.length);
        for (int i = 0; i < nodes.length; i++) {
            psiElements[i] = (T)nodes[i].getPsi();
        }
        return psiElements;
    }

    /**
     * Finds first sibling that is neither comment, nor whitespace after given element.
     *
     * @param strict prohibit returning element itself
     */
    @RequiredReadAction
    public static @Nullable PsiElement getNextNonCommentSibling(@Nullable PsiElement start, boolean strict) {
        if (!strict && !(start instanceof PsiWhiteSpace || start instanceof PsiComment)) {
            return start;
        }
        return PsiTreeUtil.skipWhitespacesAndCommentsForward(start);
    }

    /**
     * Returns first child psi element with specified element type or {@code null} if no such element exists.
     * Semantically it's the same as {@code getChildByFilter(element, TokenSet.create(type), 0)}.
     *
     * @param element tree parent node
     * @param type    element type expected
     * @return child element described
     */
    @RequiredReadAction
    public static @Nullable PsiElement getFirstChildOfType(PsiElement element, PyElementType type) {
        ASTNode child = element.getNode().findChildByType(type);
        return child != null ? child.getPsi() : null;
    }

    /**
     * Returns child element in the psi tree
     *
     * @param filter  Types of expected child
     * @param number  number
     * @param element tree parent node
     * @return PsiElement - child psiElement
     */
    @RequiredReadAction
    public static @Nullable PsiElement getChildByFilter(PsiElement element, TokenSet filter, int number) {
        ASTNode node = element.getNode();
        if (node != null) {
            ASTNode[] children = node.getChildren(filter);
            return 0 <= number && number < children.length ? children[number].getPsi() : null;
        }
        return null;
    }

    /**
     * Returns the first non-whitespace sibling following the given element but within its line boundaries.
     */
    @RequiredReadAction
    public static @Nullable PsiElement getNextNonWhitespaceSiblingOnSameLine(PsiElement element) {
        PsiElement cur = element.getNextSibling();
        while (cur != null) {
            if (!(cur instanceof PsiWhiteSpace)) {
                return cur;
            }
            else if (cur.textContains('\n')) {
                break;
            }
            cur = cur.getNextSibling();
        }
        return null;
    }

    @RequiredReadAction
    public static @Nullable String strValue(@Nullable PyAstExpression expression) {
        return expression instanceof PyAstStringLiteralExpression stringLiteral ? stringLiteral.getStringValue() : null;
    }

    @RequiredReadAction
    public static @Nullable QualifiedName asQualifiedName(@Nullable PyAstExpression expr) {
        return expr instanceof PyAstQualifiedExpression qualifiedExpression ? qualifiedExpression.asQualifiedName() : null;
    }

    @RequiredReadAction
    public static @Nullable QualifiedName asQualifiedName(PyAstQualifiedExpression expr) {
        List<String> path = new LinkedList<>();
        String firstName = expr.getReferencedName();
        if (firstName == null) {
            return null;
        }
        path.add(firstName);
        PyAstExpression qualifier = expr.getQualifier();
        while (qualifier != null) {
            PyAstReferenceExpression qualifierReference = qualifier instanceof PyAstReferenceExpression referenceExpression
                ? referenceExpression
                : null;
            if (qualifierReference == null) {
                return null;
            }
            String qualifierName = qualifierReference.getReferencedName();
            if (qualifierName == null) {
                return null;
            }
            path.add(0, qualifierName);
            qualifier = qualifierReference.getQualifier();
        }
        return QualifiedName.fromComponents(path);
    }

    /**
     * Wrapper for {@link PsiUtilCore#ensureValid} that skips nulls
     */
    @RequiredReadAction
    public static void assertValid(@Nullable PsiElement element) {
        if (element == null) {
            return;
        }
        PsiUtilCore.ensureValid(element);
    }

    @RequiredReadAction
    public static @Nullable PyAstExpression flattenParens(@Nullable PyAstExpression expr) {
        PyAstExpression result = expr;
        while (result instanceof PyAstParenthesizedExpression parenthesizedExpression) {
            result = parenthesizedExpression.getContainedExpression();
        }
        return result;
    }

    /**
     * Returns ancestor of the element that is also direct child of the given super parent.
     *
     * @param element     element to start search from
     * @param superParent direct parent of the desired ancestor
     * @return described element or {@code null} if it doesn't exist
     */
    @RequiredReadAction
    public static @Nullable PsiElement getParentRightBefore(PsiElement element, PsiElement superParent) {
        return PsiTreeUtil.findFirstParent(element, false, element1 -> element1.getParent() == superParent);
    }
}
