package com.jetbrains.python.ast;

import consulo.annotation.access.RequiredReadAction;
import consulo.language.ast.ASTNode;
import consulo.language.ast.IElementType;
import consulo.language.ast.TokenSet;
import consulo.language.psi.PsiElement;
import consulo.util.collection.ArrayUtil;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Top-level functions of JB {@code PyAstElement.kt}.
 *
 * @see PyAstElement
 */
public final class PyAstElementKt {
    private PyAstElementKt() {
    }

    @RequiredReadAction
    public static <T> @Nullable T findChildByClass(PsiElement element, Class<T> aClass) {
        PsiElement cur = element.getFirstChild();
        while (cur != null) {
            if (aClass.isInstance(cur)) {
                return aClass.cast(cur);
            }
            cur = cur.getNextSibling();
        }
        return null;
    }

    @RequiredReadAction
    public static <T> T[] findChildrenByClass(PsiElement element, Class<T> aClass) {
        List<T> result = new ArrayList<>();
        PsiElement cur = element.getFirstChild();
        while (cur != null) {
            if (aClass.isInstance(cur)) {
                result.add(aClass.cast(cur));
            }
            cur = cur.getNextSibling();
        }
        return result.toArray(ArrayUtil.newArray(aClass, result.size()));
    }

    @RequiredReadAction
    public static <T> T findNotNullChildByClass(PsiElement element, Class<T> aClass) {
        return nonNullChild(element, findChildByClass(element, aClass));
    }

    @SuppressWarnings("unchecked")
    @RequiredReadAction
    public static <T extends PsiElement> @Nullable T findChildByType(PsiElement element, IElementType elementType) {
        ASTNode node = element.getNode().findChildByType(elementType);
        return node != null ? (T) node.getPsi() : null;
    }

    @RequiredReadAction
    public static <T extends PsiElement> T findChildByTypeNotNull(PsiElement element, IElementType elementType) {
        return nonNullChild(element, findChildByType(element, elementType));
    }

    @RequiredReadAction
    public static <T extends PsiElement> List<T> findChildrenByType(PsiElement element, TokenSet elementType) {
        return findChildrenByType(element, elementType::contains);
    }

    @RequiredReadAction
    public static <T extends PsiElement> List<T> findChildrenByType(PsiElement element, IElementType elementType) {
        return findChildrenByType(element, it -> elementType == it);
    }

    @SuppressWarnings("unchecked")
    @RequiredReadAction
    private static <T extends PsiElement> List<T> findChildrenByType(PsiElement element, Predicate<IElementType> filter) {
        List<T> result = null;
        ASTNode child = element.getNode().getFirstChildNode();
        while (child != null) {
            if (filter.test(child.getElementType())) {
                if (result == null) {
                    result = new ArrayList<>();
                }
                result.add((T) child.getPsi());
            }
            child = child.getTreeNext();
        }
        return result != null ? result : Collections.emptyList();
    }

    @RequiredReadAction
    private static <T> T nonNullChild(PsiElement element, @Nullable T child) {
        return Objects.requireNonNull(child, () -> "child must not be null: expression text " + element.getText());
    }
}
