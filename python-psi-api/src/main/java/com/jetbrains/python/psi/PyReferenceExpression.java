// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import consulo.annotation.access.RequiredReadAction;
import consulo.language.psi.PsiExternalReferenceHost;
import consulo.language.psi.PsiPolyVariantReference;
import com.jetbrains.python.ast.PyAstReferenceExpression;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.QualifiedRatedResolveResult;
import com.jetbrains.python.psi.resolve.QualifiedResolveResult;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

public interface PyReferenceExpression extends PyAstReferenceExpression, PyQualifiedExpression, PyReferenceOwner, PsiExternalReferenceHost {
    PyReferenceExpression[] EMPTY_ARRAY = new PyReferenceExpression[0];

    @Override
    @RequiredReadAction
    default @Nullable PyExpression getQualifier() {
        return (PyExpression)PyAstReferenceExpression.super.getQualifier();
    }

    /**
     * Goes through a chain of assignment statements until a non-assignment expression is encountered.
     * Starts at this, expecting it to resolve to a target of an assignment.
     * <i>Note: currently limited to non-branching definite assignments.</i>
     *
     * @param resolveContext resolve context
     * @return the value that is assigned to this element via a chain of definite assignments, or an empty resolve result.
     * <i>Note: will return null if the assignment chain ends in a target of a non-assignment statement such as 'for'.</i>
     * @see PyReferenceExpression#multiFollowAssignmentsChain(PyResolveContext)
     */
    QualifiedResolveResult followAssignmentsChain(PyResolveContext resolveContext);

    /**
     * Goes through a chain of assignment statements until a non-assignment expression is encountered.
     * Starts at this, expecting it to resolve to a target of an assignment.
     *
     * @param resolveContext resolve context
     * @return the values that could be assigned to this element via a chain of assignments, or an empty list.
     * <i>Note: the returned list does not contain null values.</i>
     */
    @RequiredReadAction
    default List<QualifiedRatedResolveResult> multiFollowAssignmentsChain(PyResolveContext resolveContext) {
        return multiFollowAssignmentsChain(resolveContext, target -> true);
    }

    /**
     * Goes through a chain of assignment statements until a non-assignment expression is encountered.
     * Starts at this, expecting it to resolve to a target of an assignment.
     *
     * @param resolveContext resolve context
     * @param follow         predicate to test if target should be followed
     * @return the values that could be assigned to this element via a chain of assignments, or an empty list.
     * <i>Note: the returned list does not contain null values.</i>
     */
    List<QualifiedRatedResolveResult> multiFollowAssignmentsChain(PyResolveContext resolveContext,
            Predicate<? super PyTargetExpression> follow);

    @Override
    PsiPolyVariantReference getReference();
}
