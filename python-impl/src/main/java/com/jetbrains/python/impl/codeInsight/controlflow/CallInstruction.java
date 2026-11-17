// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.controlflow;

import com.jetbrains.python.impl.psi.PyKnownDecoratorUtil;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.PyCallExpressionHelper;
import com.jetbrains.python.impl.psi.impl.PyFunctionImpl;
import com.jetbrains.python.impl.psi.types.PyTypeUtil;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyCallable;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.PyCallableType;
import com.jetbrains.python.psi.types.PyNeverType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.language.controlFlow.ControlFlowBuilder;
import consulo.language.controlFlow.base.InstructionImpl;

import java.util.List;

public class CallInstruction extends InstructionImpl {
    public CallInstruction(ControlFlowBuilder builder, PyCallExpression call) {
        super(builder, call);
    }

    @Override
    public PyCallExpression getElement() {
        return (PyCallExpression) super.getElement();
    }

    public boolean isNoReturnCall(TypeEvalContext context) {
        return isNoReturnCall(context, true);
    }

    /**
     * Checks if the call never returns normally.
     * <p>
     * An annotated {@code NoReturn} or {@code Never} return type of the callee makes the call no-return.
     * If {@code inspectCalleeBody} is true, a single unannotated callee is no-return also when its body has no return points.
     * The analysis of that body does not inspect the bodies of its own callees. Thus, it does not go recursively through the call graph.
     */
    public boolean isNoReturnCall(TypeEvalContext context, boolean inspectCalleeBody) {
        List<PyCallable> callees = getElement().multiResolveCalleeFunction(PyResolveContext.defaultContext(context));
        if (callees.size() == 1) {
            if (!(callees.get(0) instanceof PyFunction pyFunction)) {
                return false;
            }
            if (hasReturnTypeAnnotation(pyFunction)) {
                return context.getReturnType(pyFunction) instanceof PyNeverType;
            }
            return inspectCalleeBody && hasNoReturnPoints(pyFunction, context);
        }
        if (!callees.isEmpty()) {
            return false;
        }

        // The callee is not a plain function, e.g. an attribute annotated with `Callable[...]`
        // or an instance whose `__call__` is such an attribute. Its return type always comes from an annotation.
        PyExpression callee = getElement().getCallee();
        if (callee == null) {
            return false;
        }
        PyType calleeType = PyCallExpressionHelper.getCalleeType(callee, PyResolveContext.defaultContext(context));
        List<PyCallableType> signatures = PyTypeUtil.getCallableItems(calleeType);
        return !signatures.isEmpty() && signatures.stream().allMatch(it -> it.getReturnType(context) instanceof PyNeverType);
    }

    private static boolean hasReturnTypeAnnotation(PyFunction function) {
        return function.getAnnotation() != null || function.getTypeCommentAnnotation() != null;
    }

    /**
     * Checks if the body of an unannotated {@code function} cannot complete normally, e.g. because it always raises an exception.
     * <p>
     * Some functions are not analyzed:
     * <ul>
     * <li>a function in a file that {@code context} does not allow to load as AST, such as a function from another file in the code analysis;</li>
     * <li>a generator or an {@code async} function, because the call only creates a generator or a coroutine;</li>
     * <li>an abstract method, or a method whose body only raises {@code NotImplementedError}, because a subclass overrides it;</li>
     * <li>a function with an unknown decorator, because the decorator can replace the function.</li>
     * </ul>
     */
    private static boolean hasNoReturnPoints(PyFunction function, TypeEvalContext context) {
        return context.maySwitchToAST(function) &&
            !function.isGenerator() &&
            !function.isAsync() &&
            !function.onlyRaisesNotImplementedError() &&
            !PyKnownDecoratorUtil.hasUnknownDecorator(function, context) &&
            !PyKnownDecoratorUtil.hasAbstractDecorator(function, context) &&
            PyUtil.getParameterizedCachedValue(function, context, it -> PyFunctionImpl.collectReturnPoints(function, it, false).isEmpty());
    }
}
