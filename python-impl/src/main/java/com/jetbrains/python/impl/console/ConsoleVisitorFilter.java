// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.console;

import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.impl.inspections.PyCompatibilityInspection;
import com.jetbrains.python.impl.inspections.PyIncorrectDocstringInspection;
import com.jetbrains.python.impl.inspections.PyMandatoryEncodingInspection;
import com.jetbrains.python.impl.inspections.PyMissingOrEmptyDocstringInspection;
import com.jetbrains.python.impl.inspections.PyPep8Inspection;
import com.jetbrains.python.impl.inspections.PySingleQuotedDocstringInspection;
import com.jetbrains.python.impl.inspections.PyStatementEffectInspection;
import com.jetbrains.python.impl.inspections.PyUnboundLocalVariableInspection;
import com.jetbrains.python.impl.inspections.PythonVisitorFilter;
import com.jetbrains.python.impl.inspections.unusedLocal.PyUnusedFunctionInspection;
import com.jetbrains.python.impl.inspections.unusedLocal.PyUnusedLocalVariableInspection;
import com.jetbrains.python.impl.inspections.unusedLocal.PyUnusedParameterInspection;
import com.jetbrains.python.impl.validation.PyDocStringHighlightingAnnotator;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.psi.PsiFile;

/**
 * User : catherine
 * <p>
 * filter out some python inspections and annotations if we're in console
 */
@ExtensionImpl
public final class ConsoleVisitorFilter implements PythonVisitorFilter {
    @Override
    public boolean isSupported(Class visitorClass, PsiFile file) {
        //if we're in console
        if (PydevConsoleRunnerUtil.isInPydevConsole(file)) {
            //inspections
            if (visitorClass == PyUnusedLocalVariableInspection.class || visitorClass == PyUnusedParameterInspection.class ||
                visitorClass == PyUnusedFunctionInspection.class || visitorClass == PyUnboundLocalVariableInspection.class ||
                visitorClass == PyStatementEffectInspection.class || visitorClass == PySingleQuotedDocstringInspection.class ||
                visitorClass == PyIncorrectDocstringInspection.class || visitorClass == PyMissingOrEmptyDocstringInspection.class ||
                visitorClass == PyMandatoryEncodingInspection.class || visitorClass == PyPep8Inspection.class ||
                visitorClass == PyCompatibilityInspection.class) {
                return false;
            }

            //annotators
            if (visitorClass == PyDocStringHighlightingAnnotator.class) {
                return false;
            }
        }
        return true;
    }

    @Override
    public Language getLanguage() {
        return PythonLanguage.INSTANCE;
    }
}
