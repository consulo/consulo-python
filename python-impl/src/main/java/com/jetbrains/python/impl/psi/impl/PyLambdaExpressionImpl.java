// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import com.jetbrains.python.psi.PyParameter;
import consulo.util.lang.Pair;
import com.jetbrains.python.psi.impl.PyTypeProvider;

import consulo.language.ast.ASTNode;
import consulo.util.collection.ContainerUtil;
import com.jetbrains.python.impl.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.impl.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyLambdaExpression;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PySlashParameter;
import com.jetbrains.python.psi.types.PyAnyType;
import com.jetbrains.python.psi.types.PyCallableParameter;
import com.jetbrains.python.impl.psi.types.PyCallableParameterImpl;
import com.jetbrains.python.psi.types.PyCallableType;
import com.jetbrains.python.impl.psi.types.PyExpectedTypeJudgement;
import com.jetbrains.python.impl.psi.types.PyFunctionTypeImpl;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.impl.psi.types.PyUnionType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static consulo.util.collection.ContainerUtil.map;
import static com.jetbrains.python.impl.psi.types.PyTypeUtil.toStream;


public class PyLambdaExpressionImpl extends PyElementImpl implements PyLambdaExpression {
    public PyLambdaExpressionImpl(ASTNode astNode) {
        super(astNode);
    }

    @Override
    protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
        pyVisitor.visitPyLambdaExpression(this);
    }

    @Override
    public PyType getType(TypeEvalContext context, TypeEvalContext.Key key) {
        for (PyTypeProvider provider : PyTypeProvider.EP_NAME.getExtensionList()) {
            PyType type = provider.getCallableType(this, context);
            if (type != null) {
                return type;
            }
        }

        @Nullable PyType expected = PyExpectedTypeJudgement.getExpectedType(this, context);

        List<PyType> result = toStream(expected).<PyType>map(item -> {
            if (!(item instanceof PyCallableType expectedCallable)) {
              return null;
            }
            List<PyCallableParameter> params = new ArrayList<>();
            PyParameter[] rawActualParams = getParameterList().getParameters();
            List<PyCallableParameter> rawExpectedParams = expectedCallable.getParameters(context);
            if (rawExpectedParams == null) {
              return null;
            }
            // commence actual parameter to expected parameter mapping
            // currently we only map positional arguments TODO: PY-85576
            List<PyParameter> actualParams = ContainerUtil.filter(rawActualParams, element -> !(element instanceof PySlashParameter));
            List<PyCallableParameter> expectedParams = ContainerUtil.filter(rawExpectedParams, element -> !(element.isPositionOnlySeparator()));
            boolean supported = true;

            for (Pair<PyParameter, PyCallableParameter> x : ContainerUtil.zip(actualParams, expectedParams)) {
              PyParameter rawActualParameter = x.first;
              PyCallableParameter expectedParameter = x.second;
              if (!(rawActualParameter instanceof PyNamedParameter actualParameter)) {
                supported = false;
                break;
              }
              if (
                actualParameter.isPositionalContainer() ||
                actualParameter.isKeywordContainer() ||
                expectedParameter.isPositionalContainer() ||
                expectedParameter.isKeywordContainer() ||
                expectedParameter.isKeywordOnlySeparator()
              ) {
                supported = false;
                break;
              }
              params.add(PyCallableParameterImpl.psi(actualParameter, expectedParameter.getArgumentType(context)));
            }
            if (supported) {
              return new PyFunctionTypeImpl(this, params);
            }
            return null;
            })
            .nonNull()
            .toList();
        return result.isEmpty() ? new PyFunctionTypeImpl(
            this, map(getParameterList().getParameters(), param -> PyCallableParameterImpl.psi(param, PyAnyType.getUnknown()))
        ) : PyUnionType.unionOrUnknown(result);
    }

    @Override
    public List<PyCallableParameter> getParameters(TypeEvalContext context) {
        return Optional
            .ofNullable(context.getType(this))
            .filter(PyCallableType.class::isInstance)
            .map(PyCallableType.class::cast)
            .map(callableType -> callableType.getParameters(context))
            .orElseGet(() -> ContainerUtil.map(getParameterList().getParameters(), PyCallableParameterImpl::psi));
    }

    @Override
    public @Nullable PyType getReturnType(TypeEvalContext context, TypeEvalContext.Key key) {
        PyExpression body = getBody();
        if (body == null) {
            return PyAnyType.getUnknown();
        }

        PyFunctionImpl.YieldCollector visitor = new PyFunctionImpl.YieldCollector();
        body.accept(visitor);

        List<PyType> yieldTypes = map(visitor.getYieldExpressions(), it -> it.getYieldType(context));
        List<PyType> sendTypes = map(visitor.getYieldExpressions(), it -> it.getSendType(context));

        if (!yieldTypes.isEmpty()) {
            return PyTypingTypeProvider.wrapInGeneratorType(
                PyUnionType.unionOrUnknown(yieldTypes), PyUnionType.unionOrUnknown(sendTypes), context.getType(body), this);
        }
        return context.getType(body);
    }

    @Override
    public void subtreeChanged() {
        super.subtreeChanged();
        ControlFlowCache.clear(this);
    }

    @Override
    public @Nullable String getQualifiedName() {
        return null;
    }
}
