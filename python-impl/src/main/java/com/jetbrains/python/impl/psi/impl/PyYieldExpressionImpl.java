// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import consulo.language.ast.ASTNode;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.impl.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyYieldExpression;
import com.jetbrains.python.psi.types.PyAnyType;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.impl.psi.types.PyExpectedTypeJudgement;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jspecify.annotations.Nullable;

import static com.jetbrains.python.impl.psi.types.PyTypeUtilKt.isUnknown;


public class PyYieldExpressionImpl extends PyElementImpl implements PyYieldExpression {
    public PyYieldExpressionImpl(ASTNode astNode) {
        super(astNode);
    }

    @Override
    protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
        pyVisitor.visitPyYieldExpression(this);
    }

    @Override
    public PyType getType(TypeEvalContext context, TypeEvalContext.Key key) {
        if (isDelegating()) {
            PyExpression e = getExpression();
            PyType type = e != null ? context.getType(e) : null;
            PyTypingTypeProvider.GeneratorTypeDescriptor generatorDesc = PyTypingTypeProvider.GeneratorTypeDescriptor.fromGeneratorOrProtocol(type, context);
            if (generatorDesc != null) {
                return generatorDesc.returnType();
            }
            return getNoneTypeOrUnknown();
        }
        else {
            return getSendType(context);
        }
    }

    @Override
    public @Nullable PyType getYieldType(TypeEvalContext context) {
        PyExpression expr = getExpression();
        PyType type = expr != null ? context.getType(expr) : getNoneTypeOrUnknown();

        if (isDelegating()) {
            return PyTargetExpressionImpl.getIterationType(type, this, false, context);
        }
        return type;
    }

    @Override
    public @Nullable PyType getSendType(TypeEvalContext context) {
        if (ScopeUtil.getScopeOwner(this) instanceof PyFunction function) {
            if (function.getAnnotation() != null || function.getTypeCommentAnnotation() != null) {
                PyType returnType = context.getReturnType(function);
                PyTypingTypeProvider.GeneratorTypeDescriptor generatorDesc = PyTypingTypeProvider.GeneratorTypeDescriptor.fromGeneratorOrProtocol(returnType, context);
                if (generatorDesc != null) {
                    return generatorDesc.sendType();
                }
            }
        }

        PyType expectedType = PyExpectedTypeJudgement.getExpectedType(this, context);
        if (expectedType != null && !isUnknown(expectedType)) {
            return expectedType;
        }

        if (isDelegating()) {
            PyExpression e = getExpression();
            PyType type = e != null ? context.getType(e) : null;
            PyTypingTypeProvider.GeneratorTypeDescriptor generatorDesc = PyTypingTypeProvider.GeneratorTypeDescriptor.fromGeneratorOrProtocol(type, context);
            if (generatorDesc != null) {
                return generatorDesc.sendType();
            }
            return getNoneTypeOrUnknown();
        }
        return PyAnyType.getUnknown();
    }

    /** The type of `None`, or an unknown type when the project has no builtins. */
    private @Nullable PyType getNoneTypeOrUnknown() {
        PyClassType noneType = PyBuiltinCache.getInstance(this).getNoneType();
        return noneType != null ? noneType : PyAnyType.getUnknown();
    }
}
