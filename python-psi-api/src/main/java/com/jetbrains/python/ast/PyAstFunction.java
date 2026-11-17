// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ast;

import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.ast.controlFlow.AstScopeOwner;
import com.jetbrains.python.ast.docstring.DocStringUtilCore;
import com.jetbrains.python.ast.impl.PyUtilCore;
import com.jetbrains.python.psi.LanguageLevel;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.ast.ASTNode;
import consulo.language.ast.TokenType;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiNameIdentifierOwner;
import consulo.language.psi.StubBasedPsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Map;

/**
 * Function declaration in source (the {@code def} and everything within).
 */
public interface PyAstFunction extends PsiNameIdentifierOwner, PyAstCompoundStatement,
    PyAstDecoratable, PyAstCallable, PyAstStatementListContainer, PyAstPossibleClassMember,
    AstScopeOwner, PyAstDocStringOwner, PyAstTypeCommentOwner, PyAstAnnotationOwner, PyAstTypeParameterListOwner {

    @RequiredReadAction
    private static boolean isRaiseNotImplementedError(PyAstStatement statement) {
        if (!(statement instanceof PyAstRaiseStatement raiseStatement)) {
            return false;
        }
        PyAstExpression[] expressions = raiseStatement.getExpressions();
        if (expressions.length != 1) {
            return false;
        }
        PyAstExpression raisedExpression = expressions[0];

        PyAstExpression expression = raisedExpression instanceof PyAstCallExpression callExpression
            ? callExpression.getCallee()
            : raisedExpression;
        return expression != null && PyNames.NOT_IMPLEMENTED_ERROR.equals(expression.getText());
    }

    @Override
    @RequiredReadAction
    default @Nullable String getName() {
        ASTNode nameNode = getNameNode();
        return nameNode != null ? nameNode.getText() : null;
    }

    @Override
    @RequiredReadAction
    default @Nullable PsiElement getNameIdentifier() {
        ASTNode nameNode = getNameNode();
        return nameNode != null ? nameNode.getPsi() : null;
    }

    /**
     * Returns the AST node for the function name identifier.
     *
     * @return the node, or null if the function is incomplete (only the "def"
     * keyword was typed)
     */
    @RequiredReadAction
    default @Nullable ASTNode getNameNode() {
        ASTNode node = getNode();
        ASTNode identifier = node.findChildByType(PyTokenTypes.IDENTIFIER);
        if (identifier != null) {
            return identifier;
        }
        ASTNode errorElement = node.findChildByType(TokenType.ERROR_ELEMENT);
        return errorElement != null
            ? errorElement.findChildByType(PythonDialectsTokenSetProvider.getInstance().getKeywordTokens())
            : null;
    }

    @Override
    @RequiredReadAction
    default PyAstStatementList getStatementList() {
        PyAstStatementList statementList = childToPsi(PyElementTypes.STATEMENT_LIST);
        if (statementList == null) {
            throw new IllegalArgumentException("Statement list missing for function " + getText());
        }
        return statementList;
    }

    @Override
    @RequiredReadAction
    default @Nullable PyAstFunction asMethod() {
        return getContainingClass() != null ? this : null;
    }

    @Override
    @RequiredReadAction
    default @Nullable String getDocStringValue() {
        return DocStringUtilCore.getDocStringValue(this);
    }

    @Override
    @RequiredReadAction
    default int getTextOffset() {
        ASTNode nameNode = getNameNode();
        return nameNode != null ? nameNode.getStartOffset() : getNode().getStartOffset();
    }

    @Override
    @RequiredReadAction
    default @Nullable PyAstStringLiteralExpression getDocStringExpression() {
        return DocStringUtilCore.findDocStringExpression(getStatementList());
    }

    @Override
    @Nullable PyAstTypeParameterList getTypeParameterList();

    /**
     * Looks for two standard decorators to a function, or a wrapping assignment that closely follows it.
     *
     * @return a flag describing what was detected.
     */
    @Nullable Modifier getModifier();

    @RequiredReadAction
    default boolean isAsync() {
        return getNode().findChildByType(PyTokenTypes.ASYNC_KEYWORD) != null;
    }

    @RequiredReadAction
    default boolean isAsyncAllowed() {
        LanguageLevel languageLevel = LanguageLevel.forElement(this);
        if (languageLevel.isOlderThan(LanguageLevel.PYTHON35)) {
            return false;
        }

        String functionName = getName();

        if (Arrays.asList(PyNames.AITER, PyNames.ANEXT, PyNames.AENTER, PyNames.AEXIT, PyNames.CALL).contains(getName())) {
            return true;
        }

        Map<String, PyNames.BuiltinDescription> builtinMethods =
            asMethod() != null ? PyNames.getBuiltinMethods(languageLevel) : PyNames.getModuleBuiltinMethods(languageLevel);

        return !builtinMethods.containsKey(functionName);
    }

    @RequiredReadAction
    default boolean onlyRaisesNotImplementedError() {
        PyAstStatement[] statements = getStatementList().getStatements();
        return statements.length == 1 && isRaiseNotImplementedError(statements[0]) ||
            statements.length == 2 && PyUtilCore.isStringLiteral(statements[0]) && isRaiseNotImplementedError(statements[1]);
    }

    /**
     * Flags that mark common alterations of a function: decoration by and wrapping in classmethod() and staticmethod().
     */
    enum Modifier {
        /**
         * Function is decorated with @classmethod, its first param is the class.
         */
        CLASSMETHOD,

        /**
         * Function is decorated with {@code @staticmethod}, its first param is as in a regular function.
         */
        STATICMETHOD;

        public String getDecoratorName() {
            return switch (this) {
                case CLASSMETHOD -> PyNames.CLASSMETHOD;
                case STATICMETHOD -> PyNames.STATICMETHOD;
            };
        }
    }

    @Override
    @RequiredReadAction
    default @Nullable PyAstClass getContainingClass() {
        return PsiTreeUtil.getParentOfType(this, StubBasedPsiElement.class) instanceof PyAstClass pyClass ? pyClass : null;
    }

    @Override
    default void acceptPyVisitor(PyAstElementVisitor pyVisitor) {
        pyVisitor.visitPyFunction(this);
    }

    @Override
    @RequiredReadAction
    default @Nullable PsiComment getTypeComment() {
        PsiComment inlineComment = PyUtilCore.getCommentOnHeaderLine(this);
        if (inlineComment != null && PyUtilCore.getTypeCommentValue(inlineComment.getText()) != null) {
            return inlineComment;
        }

        PyAstStatementList statements = getStatementList();
        if (statements.getStatements().length != 0) {
            if (statements.getFirstChild() instanceof PsiComment comment && PyUtilCore.getTypeCommentValue(comment.getText()) != null) {
                return comment;
            }
        }
        return null;
    }
}
