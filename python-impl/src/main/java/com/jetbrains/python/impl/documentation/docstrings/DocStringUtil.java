// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.documentation.docstrings;

import com.jetbrains.python.ast.docstring.DocStringUtilCore;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.documentation.docstrings.DocStringFormat;
import com.jetbrains.python.documentation.docstrings.DocStringParser;
import com.jetbrains.python.documentation.docstrings.PlainDocString;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyDocStringOwner;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyExpressionStatement;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.StructuredDocString;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.toolbox.Substring;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.ast.ASTNode;
import consulo.language.inject.InjectedLanguageManager;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import org.jspecify.annotations.Nullable;

public final class DocStringUtil {
    private DocStringUtil() {
    }

    /**
     * @deprecated Use {@link DocStringUtilCore#getDocStringValue(com.jetbrains.python.ast.PyAstDocStringOwner)}
     */
    @Deprecated
    public static @Nullable String getDocStringValue(PyDocStringOwner owner) {
        return DocStringUtilCore.getDocStringValue(owner);
    }

    /**
     * Attempts to detect docstring format from given text and parses it into corresponding structured docstring.
     * It's recommended to use more reliable {@link #parse(String, PsiElement)} that fallbacks to format specified in settings.
     *
     * @param text docstring text <i>with both quotes and string prefix stripped</i>
     * @return structured docstring for one of supported formats or instance of {@link PlainDocString} if none was recognized.
     * @see #parse(String, PsiElement)
     */
    public static StructuredDocString parse(String text) {
        return parse(text, null);
    }

    /**
     * Attempts to detects docstring format first from given text, next from settings and parses text into corresponding structured docstring.
     *
     * @param text   docstring text <i>with both quotes and string prefix stripped</i>
     * @param anchor PSI element that will be used to retrieve docstring format from the containing file or the project module
     * @return structured docstring for one of supported formats or instance of {@link PlainDocString} if none was recognized.
     * @see DocStringFormat#ALL_NAMES_BUT_PLAIN
     * @see DocStringParser#guessDocStringFormat(String, PsiElement)
     */
    public static StructuredDocString parse(String text, @Nullable PsiElement anchor) {
        DocStringFormat format = DocStringParser.guessDocStringFormat(text, anchor);
        return parseDocStringContent(format, text);
    }

    /**
     * Attempts to detects docstring format first from the text of given string node, next from settings using given expression as an anchor
     * and parses text into corresponding structured docstring.
     *
     * @param stringLiteral supposedly result of {@link PyDocStringOwner#getDocStringExpression()}
     * @return structured docstring for one of supported formats or instance of {@link PlainDocString} if none was recognized.
     */
    @RequiredReadAction
    public static StructuredDocString parseDocString(PyStringLiteralExpression stringLiteral) {
        return parseDocString(DocStringParser.guessDocStringFormat(stringLiteral.getStringValue(), stringLiteral), stringLiteral);
    }

    @RequiredReadAction
    public static StructuredDocString parseDocString(DocStringFormat format, PyStringLiteralExpression stringLiteral) {
        return parseDocString(format, stringLiteral.getStringNodes().get(0));
    }

    public static StructuredDocString parseDocString(DocStringFormat format, ASTNode node) {
        //Preconditions.checkArgument(node.getElementType() == PyTokenTypes.DOCSTRING);
        return DocStringParser.parseDocString(format, node.getText());
    }

    /**
     * @param stringContent docstring text without string prefix and quotes, but not escaped, otherwise ranges of {@link Substring} returned
     *                      from {@link StructuredDocString} may be invalid
     */
    public static StructuredDocString parseDocStringContent(DocStringFormat format, String stringContent) {
        return DocStringParser.parseDocString(format, new Substring(stringContent));
    }

    /**
     * Looks for a doc string under given parent.
     *
     * @param parent where to look. For classes and functions, this would be PyStatementList, for modules, PyFile.
     * @return the defining expression, or null.
     */
    public static @Nullable PyStringLiteralExpression findDocStringExpression(@Nullable PyElement parent) {
        return (PyStringLiteralExpression) DocStringUtilCore.findDocStringExpression(parent);
    }

    public static @Nullable StructuredDocString getStructuredDocString(PyDocStringOwner owner) {
        String value = owner.getDocStringValue();
        return value == null ? null : parse(value, owner);
    }

    /**
     * Returns containing docstring expression of class definition, function definition or module.
     * Useful to test whether particular PSI element is or belongs to such docstring.
     */
    public static @Nullable PyStringLiteralExpression getParentDefinitionDocString(PsiElement element) {
        return (PyStringLiteralExpression) DocStringUtilCore.getParentDefinitionDocString(element);
    }

    @RequiredReadAction
    public static boolean isDocStringExpression(PyExpression expression) {
        if (getParentDefinitionDocString(expression) == expression) {
            return true;
        }
        if (expression instanceof PyStringLiteralExpression stringLiteralExpression) {
            return isVariableDocString(stringLiteralExpression);
        }
        return false;
    }

    @RequiredReadAction
    public static @Nullable String getAttributeDocComment(PyTargetExpression attr) {
        if (attr.getParent() instanceof PyAssignmentStatement) {
            PsiElement prevSibling = PyPsiUtils.getPrevNonWhitespaceSibling(attr.getParent());
            if (prevSibling instanceof PsiComment && prevSibling.getText().startsWith("#:")) {
                return prevSibling.getText().substring(2);
            }
        }
        return null;
    }

    /**
     * Returns the host docstring literal when {@code file} is Python code injected from that docstring:
     * a doctest fragment or a fenced or Sphinx code block. Returns null otherwise.
     */
    public static @Nullable PyStringLiteralExpression getDocstringInjectionHost(PsiFile file) {
        InjectedLanguageManager manager = InjectedLanguageManager.getInstance(file.getProject());
        if (manager == null) {
            return null;
        }
        if (!(manager.getInjectionHost(file) instanceof PyStringLiteralExpression host)) {
            return null;
        }
        return getParentDefinitionDocString(host) == host ? host : null;
    }

    @RequiredReadAction
    public static boolean isVariableDocString(PyStringLiteralExpression expr) {
        PsiElement parent = expr.getParent();
        if (!(parent instanceof PyExpressionStatement)) {
            return false;
        }
        PsiElement prevElement = PyPsiUtils.getPrevNonCommentSibling(parent, true);
        if (prevElement instanceof PyAssignmentStatement assignmentStatement) {
            if (expr.getText().contains("type:")) {
                return true;
            }

            ScopeOwner scope = PsiTreeUtil.getParentOfType(prevElement, ScopeOwner.class);
            if (scope instanceof PyClass || scope instanceof PyFile) {
                return true;
            }
            if (scope instanceof PyFunction) {
                for (PyExpression target : assignmentStatement.getTargets()) {
                    if (PyUtil.isInstanceAttribute(target)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
