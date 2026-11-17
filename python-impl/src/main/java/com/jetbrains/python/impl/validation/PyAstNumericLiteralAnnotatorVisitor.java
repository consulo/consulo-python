// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.validation;

import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.ast.PyAstElementVisitor;
import com.jetbrains.python.ast.PyAstNumericLiteralExpression;
import com.jetbrains.python.validation.PyAnnotationHolder;
import consulo.language.editor.annotation.HighlightSeverity;
import consulo.python.psi.localize.PySyntaxCoreLocalize;

public class PyAstNumericLiteralAnnotatorVisitor extends PyAstElementVisitor {
    private final PyAnnotationHolder myHolder;

    public PyAstNumericLiteralAnnotatorVisitor(PyAnnotationHolder holder) {
        myHolder = holder;
    }

    @Override
    public void visitPyNumericLiteralExpression(PyAstNumericLiteralExpression node) {
        String suffix = node.getIntegerLiteralSuffix();
        if (suffix == null || "l".equalsIgnoreCase(suffix)) {
            return;
        }
        if (node.getContainingFile().getLanguage() != PythonLanguage.getInstance()) {
            return;
        }
        myHolder.newAnnotation(HighlightSeverity.ERROR, PySyntaxCoreLocalize.inspPythonTrailingSuffixNotSupport(suffix).get())
            .range(node).create();
    }
}
