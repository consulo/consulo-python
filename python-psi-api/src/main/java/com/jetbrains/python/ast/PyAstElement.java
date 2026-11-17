package com.jetbrains.python.ast;

import com.jetbrains.python.ProtectionLevel;
import com.jetbrains.python.ast.impl.PyPsiUtilsCore;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.ast.ASTNode;
import consulo.language.ast.IElementType;
import consulo.language.ast.TokenSet;
import consulo.language.psi.NavigatablePsiElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

/**
 * Top-level functions of JB {@code PyAstElement.kt} are in {@link PyAstElementKt}.
 */
public interface PyAstElement extends NavigatablePsiElement {
    /**
     * @return protection level (underscore based)
     */
    @RequiredReadAction
    default ProtectionLevel getProtectionLevel() {
        return ProtectionLevel.forName(StringUtil.notNullize(getName()));
    }

    @RequiredReadAction
    default <T extends PyAstElement> T[] childrenToPsi(@Nullable TokenSet filterSet, T[] array) {
        return PyPsiUtilsCore.nodesToPsi(getNode().getChildren(filterSet), array);
    }

    @SuppressWarnings("unchecked")
    @RequiredReadAction
    default <T extends PyAstElement> @Nullable T childToPsi(@Nullable TokenSet filterSet, int index) {
        ASTNode[] nodes = getNode().getChildren(filterSet);
        if (index < 0 || index >= nodes.length) {
            return null;
        }
        return (T)nodes[index].getPsi();
    }

    @SuppressWarnings("unchecked")
    @RequiredReadAction
    default <T extends PyAstElement> @Nullable T childToPsi(IElementType elType) {
        ASTNode node = getNode().findChildByType(elType);
        return node != null ? (T)node.getPsi() : null;
    }

    @SuppressWarnings("unchecked")
    @RequiredReadAction
    default <T extends PyAstElement> @Nullable T childToPsi(TokenSet elTypes) {
        ASTNode node = getNode().findChildByType(elTypes);
        return node != null ? (T)node.getPsi() : null;
    }

    @SuppressWarnings("unchecked")
    @RequiredReadAction
    default <T extends PyAstElement> T childToPsiNotNull(@Nullable TokenSet filterSet, int index) {
        T child = (T)childToPsi(filterSet, index);
        if (child == null) {
            throw new RuntimeException("child must not be null: expression text " + getText());
        }
        return child;
    }

    @SuppressWarnings("unchecked")
    @RequiredReadAction
    default <T extends PyAstElement> T childToPsiNotNull(IElementType elType) {
        T child = (T)childToPsi(elType);
        if (child == null) {
            throw new RuntimeException("child must not be null; expression text " + getText());
        }
        return child;
    }

    @RequiredReadAction
    default <E extends PsiElement> @Nullable E getStubOrPsiParentOfType(Class<E> parentClass) {
        return PsiTreeUtil.getParentOfType(this, parentClass);
    }

    default void acceptPyVisitor(PyAstElementVisitor pyVisitor) {
        pyVisitor.visitPyElement(this);
    }
}
