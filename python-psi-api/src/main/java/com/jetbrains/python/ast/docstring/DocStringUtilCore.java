package com.jetbrains.python.ast.docstring;

import com.jetbrains.python.ast.PyAstDocStringOwner;
import com.jetbrains.python.ast.PyAstElement;
import com.jetbrains.python.ast.PyAstExpressionStatement;
import com.jetbrains.python.ast.PyAstStringLiteralExpression;
import com.jetbrains.python.ast.impl.PyPsiUtilsCore;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import org.jspecify.annotations.Nullable;

public final class DocStringUtilCore {
    private DocStringUtilCore() {
    }

    @RequiredReadAction
    public static @Nullable String getDocStringValue(PyAstDocStringOwner owner) {
        return PyPsiUtilsCore.strValue(owner.getDocStringExpression());
    }

    /**
     * Looks for a doc string under given parent.
     *
     * @param parent where to look. For classes and functions, this would be PyStatementList, for modules, PyFile.
     * @return the defining expression, or null.
     */
    @RequiredReadAction
    public static @Nullable PyAstStringLiteralExpression findDocStringExpression(@Nullable PyAstElement parent) {
        if (parent != null) {
            PsiElement seeker = PyPsiUtilsCore.getNextNonCommentSibling(parent.getFirstChild(), false);
            if (seeker instanceof PyAstExpressionStatement) {
                seeker = PyPsiUtilsCore.getNextNonCommentSibling(seeker.getFirstChild(), false);
            }
            if (seeker instanceof PyAstStringLiteralExpression) {
                return (PyAstStringLiteralExpression)seeker;
            }
        }
        return null;
    }

    /**
     * Returns containing docstring expression of class definition, function definition or module.
     * Useful to test whether particular PSI element is or belongs to such docstring.
     */
    @RequiredReadAction
    public static @Nullable PyAstStringLiteralExpression getParentDefinitionDocString(PsiElement element) {
        PyAstDocStringOwner docStringOwner = PsiTreeUtil.getParentOfType(element, PyAstDocStringOwner.class);
        if (docStringOwner != null) {
            PyAstStringLiteralExpression docString = docStringOwner.getDocStringExpression();
            if (PsiTreeUtil.isAncestor(docString, element, false)) {
                return docString;
            }
        }
        return null;
    }
}
