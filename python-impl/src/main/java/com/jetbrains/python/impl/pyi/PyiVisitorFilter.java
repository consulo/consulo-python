// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.pyi;

import com.google.common.collect.ImmutableSet;
import com.jetbrains.python.impl.inspections.PyCompatibilityInspection;
import com.jetbrains.python.impl.inspections.PyMissingConstructorInspection;
import com.jetbrains.python.impl.inspections.PyMissingOrEmptyDocstringInspection;
import com.jetbrains.python.impl.inspections.PyPropertyDefinitionInspection;
import com.jetbrains.python.impl.inspections.PyShadowingBuiltinsInspection;
import com.jetbrains.python.impl.inspections.PyStatementEffectInspection;
import com.jetbrains.python.impl.inspections.PyTypeCheckerInspection;
import com.jetbrains.python.impl.inspections.PythonVisitorFilter;
import com.jetbrains.python.impl.inspections.unusedLocal.PyUnusedFunctionInspection;
import com.jetbrains.python.impl.inspections.unusedLocal.PyUnusedLocalVariableInspection;
import com.jetbrains.python.impl.inspections.unusedLocal.PyUnusedParameterInspection;
import com.jetbrains.python.pyi.PyiLanguageDialect;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.psi.PsiFile;

import java.util.Set;

@ExtensionImpl
public final class PyiVisitorFilter implements PythonVisitorFilter {

    private static final Set<Class<?>> disabledVisitors = ImmutableSet.of(
        PyUnusedLocalVariableInspection.class,
        PyUnusedParameterInspection.class,
        PyUnusedFunctionInspection.class,
        PyStatementEffectInspection.class,
        PyCompatibilityInspection.class,
        PyMissingOrEmptyDocstringInspection.class,
        PyTypeCheckerInspection.class,
        PyPropertyDefinitionInspection.class,
        PyMissingConstructorInspection.class,
        PyShadowingBuiltinsInspection.class
    );

    @Override
    public boolean isSupported(Class visitorClass, PsiFile file) {
        return !disabledVisitors.contains(visitorClass);
    }

    // Consulo: PythonVisitorFilter is a LanguageExtension
    @Override
    public Language getLanguage() {
        return PyiLanguageDialect.getInstance();
    }
}
