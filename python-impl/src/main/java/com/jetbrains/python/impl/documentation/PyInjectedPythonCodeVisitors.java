package com.jetbrains.python.impl.documentation;

import com.jetbrains.python.impl.inspections.PyArgumentListInspection;
import com.jetbrains.python.impl.inspections.PyByteLiteralInspection;
import com.jetbrains.python.impl.inspections.PyClassHasNoInitInspection;
import com.jetbrains.python.impl.inspections.PyDocstringTypesInspection;
import com.jetbrains.python.impl.inspections.PyIncorrectDocstringInspection;
import com.jetbrains.python.impl.inspections.PyMandatoryEncodingInspection;
import com.jetbrains.python.impl.inspections.PyMissingOrEmptyDocstringInspection;
import com.jetbrains.python.impl.inspections.PyNonAsciiCharInspection;
import com.jetbrains.python.impl.inspections.PyPep8Inspection;
import com.jetbrains.python.impl.inspections.PySingleQuotedDocstringInspection;
import com.jetbrains.python.impl.inspections.PyStatementEffectInspection;
import com.jetbrains.python.impl.inspections.PyUnboundLocalVariableInspection;
import com.jetbrains.python.impl.inspections.PyUnnecessaryBackslashInspection;
import com.jetbrains.python.impl.validation.PyDocStringHighlightingAnnotator;
import com.jetbrains.python.impl.validation.PyFunctionHighlightingAnnotator;
import com.jetbrains.python.impl.validation.PyParameterListAnnotatorVisitor;
import com.jetbrains.python.impl.validation.PyReturnYieldAnnotatorVisitor;

import java.util.Set;

/**
 * Holds the visitor sets that PyDocstringCodeBlockVisitorFilter and PyDoctestVisitorFilter both disable
 * for Python code injected into a docstring code block or a doctest fragment.
 */
public final class PyInjectedPythonCodeVisitors {
    private PyInjectedPythonCodeVisitors() {
    }

    public static final Set<Class<?>> disabledAnnotators = Set.of(
        PyDocStringHighlightingAnnotator.class,
        PyParameterListAnnotatorVisitor.class,
        PyReturnYieldAnnotatorVisitor.class,
        PyFunctionHighlightingAnnotator.class
    );

    public static final Set<Class<?>> disabledInspections = Set.of(
        PyPep8Inspection.class,
        PyArgumentListInspection.class,
        PyIncorrectDocstringInspection.class,
        PyMissingOrEmptyDocstringInspection.class,
        PyUnboundLocalVariableInspection.class,
        PyUnnecessaryBackslashInspection.class,
        PyByteLiteralInspection.class,
        PyNonAsciiCharInspection.class,
        PyMandatoryEncodingInspection.class,
        PyDocstringTypesInspection.class,
        PySingleQuotedDocstringInspection.class,
        PyClassHasNoInitInspection.class,
        PyStatementEffectInspection.class
    );
}
