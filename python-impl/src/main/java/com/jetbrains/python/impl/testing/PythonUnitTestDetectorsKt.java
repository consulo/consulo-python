// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.testing;

import com.jetbrains.python.impl.extensions.PyClassExtKt;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyDecorator;
import com.jetbrains.python.psi.PyDecoratorList;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;

import static com.jetbrains.python.impl.testing.pyTestFixtures.PyTestFixtureUtilsKt.TEST_FIXTURE_DECORATOR_NAMES;

public final class PythonUnitTestDetectorsKt {
    private PythonUnitTestDetectorsKt() {
    }

    /**
     * Pytest could use any "test_" function as a test.
     * See {@code PythonUnitTestDetectorsBasedOnSettings.isTestFunction}.
     */
    public static boolean isTestFunction(PyFunction function) {
        // Check if the function name starts with "test"
        String name = function.getName();
        boolean isTestNamed = name != null && name.startsWith("test");
        if (!isTestNamed) {
            return false;
        }

        // Get the list of decorators, if any, otherwise consider this a test function
        PyDecoratorList decorators = function.getDecoratorList();
        if (decorators == null) {
            return true;
        }
        PyDecorator[] decoratorList = decorators.getDecorators();

        // Determine if any decorator matches known pytest fixture names
        boolean isPytestFixture = false;
        for (PyDecorator decorator : decoratorList) {
            PyExpression callee = decorator.getCallee();
            String decoratorName = callee != null ? callee.getText() : null;
            if (TEST_FIXTURE_DECORATOR_NAMES.contains(decoratorName)) {
                isPytestFixture = true;
                break;
            }
        }

        // A test function must not be a pytest fixture
        return !isPytestFixture;
    }

    /**
     * Inheritor of TestCase class is always test for unittest and could also be launched with pytest.
     * See {@code PythonUnitTestDetectorsBasedOnSettings.isTestFunction}.
     */
    public static boolean isUnitTestCaseClass(PyClass clazz, TypeEvalContext context) {
        return PyClassExtKt.inherits(clazz, context, "unittest.TestCase", "unittest.case.TestCase");
    }

    /**
     * Checks if class {@link #isUnitTestCaseClass} or both conditions are true: it's name starts/ends with "Test" and it has at least one
     * "test" or setup method.
     * See {@code PythonUnitTestDetectorsBasedOnSettings.isTestFunction}.
     */
    public static boolean isTestClass(PyClass clazz, TypeEvalContext context) {
        if (isUnitTestCaseClass(clazz, context)) {
            return true;
        }
        String className = clazz.getName();
        if (className == null) {
            return false;
        }
        if (!className.startsWith("Test") && !className.endsWith("Test")) {
            return false;
        }

        // Check if any method is a test method or setUp
        boolean[] hasTestMethod = {false};
        clazz.visitMethods(function -> {
            if (isTestFunction(function) || "setUp".equals(function.getName())) {
                hasTestMethod[0] = true;
                return false;
            }
            return true;
        }, true, context);

        // Check nested classes
        boolean hasTestInNested = false;
        PyStatement[] statements = clazz.getStatementList().getStatements();
        for (PyStatement stmt : statements) {
            if (stmt instanceof PyClass nested && isTestClass(nested, context)) {
                hasTestInNested = true;
                break;
            }
        }

        return hasTestMethod[0] || hasTestInNested;
    }

    public static boolean isTestFile(PyFile file, TypeEvalContext context) {
        for (PyClass o : file.getTopLevelClasses()) {
            if (isTestClass(o, context)) {
                return true;
            }
        }
        if (file.getName().startsWith("test_")) {
            return true;
        }
        for (PyFunction o : file.getTopLevelFunctions()) {
            if (isTestFunction(o)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isTestElement(PsiElement element, TypeEvalContext typeEvalContext) {
        if (element instanceof PyFile file) {
            return isTestFile(file, typeEvalContext);
        }
        if (element instanceof PsiDirectory directory) {
            if (directory.getName().toLowerCase().contains("test")) {
                return true;
            }
            for (PsiElement it : directory.getChildren()) {
                if (it instanceof PyFile file && isTestFile(file, typeEvalContext)) {
                    return true;
                }
            }
            return false;
        }
        if (element instanceof PyFunction function) {
            return isTestFunction(function);
        }
        if (element instanceof PyClass cls) {
            return isTestClass(cls, typeEvalContext);
        }
        return false;
    }
}
