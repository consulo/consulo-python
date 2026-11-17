// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import consulo.language.ast.ASTNode;
import consulo.util.lang.ref.Ref;
import consulo.language.psi.PsiPolyVariantReference;
import consulo.language.util.IncorrectOperationException;
import consulo.util.lang.ThreeState;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.impl.psi.impl.references.PyOperatorReference;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.PyAnyType;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.impl.psi.types.PyStructuralType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.impl.psi.types.PyTypeChecker;
import com.jetbrains.python.impl.psi.types.PyTypeUtil;
import com.jetbrains.python.impl.psi.types.PyUnionType;
import com.jetbrains.python.impl.psi.types.PyUnsafeUnionType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jspecify.annotations.Nullable;

import static com.jetbrains.python.impl.psi.types.PyTypeUtilKt.isUnknown;

public class PyBinaryExpressionImpl extends PyElementImpl implements PyBinaryExpression {

    public PyBinaryExpressionImpl(ASTNode astNode) {
        super(astNode);
    }

    @Override
    protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
        pyVisitor.visitPyBinaryExpression(this);
    }

    @Override
    public void deleteChildInternal(ASTNode child) {
        PyExpression left = getLeftExpression();
        PyExpression right = getRightExpression();
        if (left == child.getPsi() && right != null) {
            replace(right);
        }
        else if (right == child.getPsi() && left != null) {
            replace(left);
        }
        else {
            throw new IncorrectOperationException("Element " + child.getPsi() + " is neither left expression or right expression");
        }
    }

    @Override
    public PsiPolyVariantReference getReference() {
        return getReference(PyResolveContext.defaultContext(TypeEvalContext.codeInsightFallback(getProject())));
    }

    @Override
    public PsiPolyVariantReference getReference(PyResolveContext context) {
        return new PyOperatorReference(this, context);
    }

    @Override
    public @Nullable PyType getType(TypeEvalContext context, TypeEvalContext.Key key) {
        if (isOperator(PyNames.AND) || isOperator(PyNames.OR)) {
            PyExpression left = getLeftExpression();
            PyType leftType = left != null ? context.getType(left) : PyAnyType.getUnknown();
            PyExpression right = getRightExpression();
            PyType rightType = right != null ? context.getType(right) : PyAnyType.getUnknown();
            if (left == null && right == null) {
                return PyAnyType.getUnknown();
            }
            // `a or b` evaluates to `a` when `a` is truthy and to `b` otherwise.
            // `a and b` evaluates to `a` when `a` is falsy and to `b` otherwise.
            // So `a` contributes only the part of its type with the relevant truthiness (excluding e.g. `None`,
            // `Literal[False]` or a class whose `__bool__` is `Literal[False]`), and `b` contributes only when it can be reached.
            boolean isOr = isOperator(PyNames.OR);
            ThreeState leftTruthiness = PyTypeUtil.getTypeTruthiness(leftType, left, context);
            Ref<PyType> leftContribution = PyTypeUtil.narrowTypeByTruthiness(leftType, isOr, left, context);
            boolean rightReachable = isOr ? leftTruthiness != ThreeState.YES : leftTruthiness != ThreeState.NO;
            if (leftContribution == null) {
                // `a` never yields its own value as the result (e.g. an always-falsy `a` in `a or b`).
                return rightType != null ? rightType : PyAnyType.getUnknown();
            }
            if (!rightReachable) {
                // `b` is never evaluated (e.g. an always-truthy `a` in `a or b`).
                return leftContribution.get();
            }
            return PyUnionType.unionOrUnknown(leftContribution.get(), rightType);
        }
        String referencedName = getReferencedName();
        if (PyNames.CONTAINS.equals(referencedName) || getOperator() == PyTokenTypes.IS_KEYWORD) {
            PyClassType boolType = PyBuiltinCache.getInstance(this).getBoolType();
            return boolType != null ? boolType : PyAnyType.getUnknown();
        }
        PyType callResultType = PyCallExpressionHelper.getCallType(this, context, key);
        if (callResultType instanceof PyAnyType.Any) {
            return callResultType;
        }
        if (isUnknown(callResultType)) {
            if (referencedName != null && PyNames.COMPARISON_OPERATORS.contains(referencedName)) {
                // it was not an explicit `Any`, so we form an unsafe union of `Unknown` and `bool`
                PyClassType boolType = PyBuiltinCache.getInstance(this).getBoolType();
                return PyUnsafeUnionType.unsafeUnion(callResultType, boolType != null ? boolType : PyAnyType.getUnknown());
            }
            return callResultType;
        }
        boolean bothOperandsAreKnown = operandIsKnown(getLeftExpression(), context) && operandIsKnown(getRightExpression(), context);
        // TODO requires weak union. See PyTypeCheckerInspectionTest#testBinaryExpressionWithUnknownOperand
        return bothOperandsAreKnown ? callResultType : PyUnionType.createWeakType(callResultType);
    }

    @Override
    public PyExpression getLeftExpression() {
        return PyBinaryExpression.super.getLeftExpression();
    }

    @Override
    public @Nullable PyExpression getRightExpression() {
        return PyBinaryExpression.super.getRightExpression();
    }

    @Override
    public @Nullable PyExpression getQualifier() {
        return PyBinaryExpression.super.getQualifier();
    }

    private static boolean operandIsKnown(@Nullable PyExpression operand, TypeEvalContext context) {
        if (operand == null) {
            return false;
        }

        PyType operandType = context.getType(operand);
        if (operandType instanceof PyStructuralType || PyTypeChecker.isUnknown(operandType, context)) {
            return false;
        }

        return true;
    }
}
