// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.validation;

import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyLambdaExpression;
import com.jetbrains.python.psi.PyReturnStatement;
import com.jetbrains.python.psi.PyYieldExpression;
import com.jetbrains.python.validation.PyAnnotationHolder;
import consulo.language.editor.annotation.HighlightSeverity;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.python.impl.localize.PyLocalize;

/**
 * Highlights incorrect return statements: 'return' and 'yield' outside functions
 */
public class PyReturnYieldAnnotatorVisitor extends PyElementVisitor {
    private final PyAnnotationHolder myHolder;

    PyReturnYieldAnnotatorVisitor(PyAnnotationHolder holder) {
        myHolder = holder;
    }

    @Override
    public void visitPyReturnStatement(PyReturnStatement node) {
        PyFunction function = PsiTreeUtil.getParentOfType(node, PyFunction.class, false, PyClass.class);
        if (function == null) {
            myHolder.newAnnotation(HighlightSeverity.ERROR, PyLocalize.annReturnOutsideOfFunction().get()).create();
        }
        if (function != null && node.getExpression() != null && function.isGenerator() && (function.isAsync() && function.isAsyncAllowed())) {
            myHolder.newAnnotation(HighlightSeverity.ERROR, PyLocalize.annNonEmptyReturnInsideAsynchronousGenerator().get()).create();
        }
    }

    @Override
    public void visitPyYieldExpression(PyYieldExpression node) {
        ScopeOwner owner = ScopeUtil.getScopeOwner(node);
        if (!(owner instanceof PyFunction || owner instanceof PyLambdaExpression)) {
            myHolder.newAnnotation(HighlightSeverity.ERROR, PyLocalize.annYieldOutsideOfFunction().get()).create();
        }

        if (node.isDelegating() && owner instanceof PyFunction function) {

            if (function.isAsync() && function.isAsyncAllowed()) {
                myHolder.newAnnotation(HighlightSeverity.ERROR,
                    PyLocalize.annPythonDoesNotSupportYieldFromInsideAsyncFunctions().get()).create();
            }
        }
    }
}
