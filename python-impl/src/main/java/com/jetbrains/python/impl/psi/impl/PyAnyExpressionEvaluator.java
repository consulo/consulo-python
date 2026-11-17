// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PySequenceExpression;
import com.jetbrains.python.impl.psi.PyUtil;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Evaluator that supports expression of any type (it evaluates any expression, concatenating several expressions to list, etc.).
 */
public class PyAnyExpressionEvaluator extends PyEvaluator {
    private final boolean myEvalSequence;

    /**
     * @param evalSequence evaluate sequencies ((a,b) + (c,d) == (a,b,c,d)) or store them as single ((a,b) + (c,d) = [(a,b), (c,d)])
     */
    public PyAnyExpressionEvaluator(boolean evalSequence) {
        myEvalSequence = evalSequence;
    }

    @Override
    public @Nullable Object evaluate(@Nullable PyExpression expression) {
        Object evaluate = super.evaluate(expression);
        return evaluate != null ? evaluate : expression;
    }

    @Override
    public Object applyPlus(@Nullable Object lhs, @Nullable Object rhs) {
        Object evaluate = super.applyPlus(lhs, rhs);
        return evaluate != null ? evaluate : Arrays.asList(lhs, rhs);
    }

    @Override
    protected Object evaluateReference(PyReferenceExpression expression) {
        Object evaluate = super.evaluateReference(expression);
        return evaluate != null ? evaluate : expression;
    }

    @Override
    protected Object evaluateCall(PyCallExpression expression) {
        Object evaluate = super.evaluateCall(expression);
        return evaluate != null ? evaluate : expression;
    }

    @Override
    protected Object evaluateSequence(PySequenceExpression expression) {
        return myEvalSequence ? super.evaluateSequence(expression) : expression;
    }

    /**
     * Evaluates expression to single element
     *
     * @param expression exp to eval
     * @param aClass     expected class
     * @param <T>        expected class
     * @return instance of aClass, or null if failed to eval
     */
    public static @Nullable <T> T evaluateOne(PyExpression expression, Class<T> aClass) {
        PyAnyExpressionEvaluator evaluator = new PyAnyExpressionEvaluator(false);
        Object evaluate = evaluator.evaluate(expression);
        T resultSingle = PyUtil.as(evaluate, aClass);
        if (resultSingle != null) {
            return resultSingle;
        }
        List<?> resultMultiple = PyUtil.as(evaluate, List.class);
        if ((resultMultiple != null) && !resultMultiple.isEmpty()) {
            return PyUtil.as(resultMultiple.get(0), aClass);
        }
        return null;
    }

    /**
     * Evaluates expression to string
     *
     * @param expression exp to eval
     * @return string, or null if failed to eval
     */
    public static @Nullable String evaluateString(PyExpression expression) {
        return PyUtil.as(new PyAnyExpressionEvaluator(false).evaluate(expression), String.class);
    }

    /**
     * Evaluates expression as list of values
     *
     * @param expression exp to eval
     * @param aClass     expected element class
     * @param <T>        expected element class
     * @return a list of elements of expected type
     */
    public static <T> List<T> evaluateIterable(PyExpression expression, Class<T> aClass) {
        PyAnyExpressionEvaluator evaluator = new PyAnyExpressionEvaluator(true);
        Object evaluate = evaluator.evaluate(expression);
        T resultSingle = PyUtil.as(evaluate, aClass);
        if (resultSingle != null) {
            return Collections.singletonList(resultSingle);
        }
        return PyUtil.asList(PyUtil.as(evaluate, List.class), aClass);
    }
}
