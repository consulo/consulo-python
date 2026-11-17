package com.jetbrains.python.ast;

import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.ast.ASTNode;
import consulo.language.ast.TokenSet;
import consulo.language.psi.util.PsiTreeUtil;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Represents an argument list of a function call.
 */
public interface PyAstArgumentList extends PyAstElement {

    /**
     * @return all argument list param expressions (keyword argument or nameless)
     */
    @RequiredReadAction
    default Collection<? extends PyAstExpression> getArgumentExpressions() {
        PyAstExpression[] arguments = getArguments();
        Collection<PyAstExpression> result = new ArrayList<>(arguments.length);
        for (PyAstExpression expression : arguments) {
            if (expression instanceof PyAstKeywordArgument) {
                PyAstExpression valueExpression = ((PyAstKeywordArgument)expression).getValueExpression();
                result.add(valueExpression);
            }
            if (expression instanceof PyAstReferenceExpression) {
                result.add(expression);
            }
        }
        return result;
    }

    @RequiredReadAction
    default PyAstExpression[] getArguments() {
        return childrenToPsi(PythonDialectsTokenSetProvider.getInstance().getExpressionTokens(), PyAstExpression.EMPTY_ARRAY);
    }

    @RequiredReadAction
    default @Nullable PyAstKeywordArgument getKeywordArgument(String name) {
        ASTNode node = getNode().getFirstChildNode();
        while (node != null) {
            if (node.getElementType() == PyElementTypes.KEYWORD_ARGUMENT_EXPRESSION) {
                PyAstKeywordArgument arg = (PyAstKeywordArgument)node.getPsi();
                String keyword = arg.getKeyword();
                if (keyword != null && keyword.equals(name)) {
                    return arg;
                }
            }
            node = node.getTreeNext();
        }
        return null;
    }

    /**
     * @return the call expression to which this argument list belongs; not null in correctly parsed cases.
     */
    @RequiredReadAction
    default @Nullable PyAstCallExpression getCallExpression() {
        return PsiTreeUtil.getParentOfType(this, PyAstCallExpression.class);
    }

    @RequiredReadAction
    default @Nullable ASTNode getClosingParen() {
        ASTNode node = getNode();
        ASTNode[] children = node.getChildren(TokenSet.create(PyTokenTypes.RPAR));
        return children.length == 0 ? null : children[children.length - 1];
    }

    @Override
    default void acceptPyVisitor(PyAstElementVisitor pyVisitor) {
        pyVisitor.visitPyArgumentList(this);
    }
}
