// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.testing.pyTestFixtures;

import com.jetbrains.python.impl.psi.impl.PyEvaluator;
import com.jetbrains.python.psi.PyDecorator;
import com.jetbrains.python.psi.PyExpression;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Set;

public final class PyTestFixtureUtilsKt {
    private PyTestFixtureUtilsKt() {
    }

    public static final String CONFTEST_PY = "conftest.py";
    public static final String REQUEST_FIXTURE = "request";
    public static final String _PYTEST_DIR = "_pytest";
    public static final String USE_FIXTURES = "usefixtures";
    public static final String PARAMETRIZE = "parametrize";

    public static final Set<String> reservedFixturesSet = Set.of(
        "capfd",
        "capfdbinary",
        "caplog",
        "capsys",
        "capsysbinary",
        "cache",
        "doctest_namespace",
        "monkeypatch",
        "pytestconfig",
        "pytester",
        "record_property",
        "record_testsuite_property",
        "recwarn",
        "tmp_path",
        "tmp_path_factory"
    );

    public static final Set<String> reservedFixtureClassSet = Set.of(
        "testdir",
        "tmpdir",
        "tmpdir_factory"
    );

    public static final List<String> TEST_FIXTURE_DECORATOR_NAMES = List.of("pytest.fixture", "fixture", "pytest_asyncio.fixture");

    public static @Nullable String getTestFixtureName(PyDecorator decorator) {
        PyExpression nameArgument = decorator.getKeywordArgument("name");
        if (nameArgument == null) {
            return null;
        }
        return PyEvaluator.evaluateNoResolve(nameArgument, String.class);
    }
}
