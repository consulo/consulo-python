// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.ast.PyAstCallExpression;
import com.jetbrains.python.ast.PyAstExpressionStatement;
import com.jetbrains.python.ast.PyAstFunction;
import com.jetbrains.python.ast.PyAstReferenceExpression;
import com.jetbrains.python.ast.PyAstStatement;
import com.jetbrains.python.ast.impl.PyPsiUtilsCore;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.psi.impl.PyTypeProvider;
import com.jetbrains.python.psi.stubs.PyFunctionStub;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.psi.PsiNameIdentifierOwner;
import consulo.language.psi.StubBasedPsiElement;
import consulo.util.collection.ArrayFactory;
import org.jspecify.annotations.Nullable;

import java.util.List;


/**
 * Function declaration in source (the {@code def} and everything within).
 */
public interface PyFunction extends PyAstFunction, StubBasedPsiElement<PyFunctionStub>, PsiNameIdentifierOwner, PyCompoundStatement,
    PyDecoratable, PyCallable, PyStatementListContainer, PyPossibleClassMember,
    ScopeOwner, PyDocStringOwner, PyTypeCommentOwner, PyAnnotationOwner, PyTypeParameterListOwner,
    PyDeprecatable {

    PyFunction[] EMPTY_ARRAY = new PyFunction[0];
    ArrayFactory<PyFunction> ARRAY_FACTORY = count -> count == 0 ? EMPTY_ARRAY : new PyFunction[count];

    /**
     * Infers function's return type by analyzing <b>only return statements</b> (including implicit returns) in its control flow.
     * Does not consider yield statements or return type annotations.
     *
     * @see PyFunction#getInferredReturnType(TypeEvalContext)
     */
    @Nullable
    PyType getReturnStatementType(TypeEvalContext context);

    /**
     * Infers function's return type by analyzing <b>return statements</b> (including implicit returns) and <b>yield expression</b>.
     * In contrast with {@link TypeEvalContext#getReturnType(PyCallable)} does not consider
     * return type annotations or any other {@link PyTypeProvider}.
     *
     * @apiNote Does not cache the result.
     */
    @Nullable
    PyType getInferredReturnType(TypeEvalContext context);

    /**
     * Returns a list of all function exit points that can return a value.
     * This includes explicit 'return' statements and statements that can complete
     * normally with an implicit 'return None', excluding statements that raise exceptions.
     *
     * @return List of exit point statements, in control flow order
     * @see PyFunction#getReturnStatementType(TypeEvalContext)
     */
    List<PyStatement> getReturnPoints(TypeEvalContext context);

    /**
     * Checks whether the function contains a yield expression in its body.
     */
    boolean isGenerator();

    /**
     * Returns a property for which this function is a getter, setter or deleter.
     *
     * @return the corresponding property, or null if there isn't any.
     */
    @Nullable
    Property getProperty();

    /**
     * Searches for function attributes.
     * See <a href="http://legacy.python.org/dev/peps/pep-0232/">PEP-0232</a>
     *
     * @return assignment statements for function attributes
     */
    List<PyAssignmentStatement> findAttributes();

    @Override
    @RequiredReadAction
    default PyStatementList getStatementList() {
        return (PyStatementList)PyAstFunction.super.getStatementList();
    }

    @Override
    @RequiredReadAction
    default @Nullable PyFunction asMethod() {
        return (PyFunction)PyAstFunction.super.asMethod();
    }

    @Override
    @RequiredReadAction
    default @Nullable PyStringLiteralExpression getDocStringExpression() {
        return (PyStringLiteralExpression)PyAstFunction.super.getDocStringExpression();
    }

    @RequiredReadAction
    static @Nullable String extractDeprecationMessage(List<? extends PyAstStatement> statements) {
        for (PyAstStatement statement : statements) {
            if (statement instanceof PyAstExpressionStatement expressionStatement) {
                if (expressionStatement.getExpression() instanceof PyAstCallExpression callExpression) {
                    if (callExpression.isCalleeText(PyNames.WARN)) {
                        PyAstReferenceExpression warningClass = callExpression.getArgument(1, PyAstReferenceExpression.class);
                        if (warningClass != null && (PyNames.DEPRECATION_WARNING.equals(warningClass.getReferencedName()) ||
                            PyNames.PENDING_DEPRECATION_WARNING.equals(warningClass.getReferencedName()))) {
                            return PyPsiUtilsCore.strValue(callExpression.getArguments()[0]);
                        }
                    }
                }
            }
        }
        return null;
    }

    @Override
    @RequiredReadAction
    default @Nullable PyClass getContainingClass() {
        return (PyClass)PyAstFunction.super.getContainingClass();
    }
}
