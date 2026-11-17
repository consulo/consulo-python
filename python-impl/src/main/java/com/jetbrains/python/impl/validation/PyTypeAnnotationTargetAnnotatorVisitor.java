// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.validation;

import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyListLiteralExpression;
import com.jetbrains.python.psi.PySubscriptionExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.PyTupleExpression;
import com.jetbrains.python.psi.PyTypeDeclarationStatement;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.validation.PyAnnotationHolder;
import consulo.language.editor.annotation.HighlightSeverity;
import consulo.python.impl.localize.PyLocalize;

/**
 * @author Mikhail Golubev
 */
public class PyTypeAnnotationTargetAnnotatorVisitor extends PyElementVisitor {
    private final PyAnnotationHolder myHolder;

    public PyTypeAnnotationTargetAnnotatorVisitor(PyAnnotationHolder holder) {
        myHolder = holder;
    }

    @Override
    public void visitPyAssignmentStatement(PyAssignmentStatement node) {
        if (node.getAnnotation() != null && LanguageLevel.forElement(node).isAtLeast(LanguageLevel.PYTHON36)) {
            if (node.getRawTargets().length > 1) {
                myHolder.newAnnotation(HighlightSeverity.ERROR,
                    PyLocalize.annVariableAnnotationCannotBeUsedInAssignmentWithMultipleTargets().get()).create();
            }
            PyExpression target = node.getLeftHandSideExpression();
            if (target != null) {
                checkAnnotationTarget(target);
            }
        }
    }

    @Override
    public void visitPyTypeDeclarationStatement(PyTypeDeclarationStatement node) {
        if (node.getAnnotation() != null && LanguageLevel.forElement(node).isAtLeast(LanguageLevel.PYTHON36)) {
            checkAnnotationTarget(node.getTarget());
        }
    }

    private void checkAnnotationTarget(PyExpression expression) {
        PyExpression innerExpr = PyPsiUtils.flattenParens(expression);
        if (innerExpr instanceof PyTupleExpression || innerExpr instanceof PyListLiteralExpression) {
            myHolder.newAnnotation(HighlightSeverity.ERROR,
                    PyLocalize.annVariableAnnotationCannotBeCombinedWithTupleUnpacking().get()).range(innerExpr)
                .create();
        }
        else if (innerExpr != null && !(innerExpr instanceof PyTargetExpression || innerExpr instanceof PySubscriptionExpression)) {
            myHolder.newAnnotation(HighlightSeverity.ERROR, PyLocalize.annIllegalTargetForVariableAnnotation().get()).range(innerExpr)
                .create();
        }
    }
}
