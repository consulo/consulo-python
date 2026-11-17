// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.validation;

import com.jetbrains.python.impl.codeInsight.functionTypeComments.psi.PyParameterTypeList;
import com.jetbrains.python.impl.codeInsight.typeHints.PyTypeHintFile;
import com.jetbrains.python.psi.PyAnnotation;
import com.jetbrains.python.psi.PyComprehensionElement;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyParenthesizedExpression;
import com.jetbrains.python.psi.PyStarExpression;
import com.jetbrains.python.psi.PyTupleExpression;
import com.jetbrains.python.psi.PyTypeParameter;
import com.jetbrains.python.psi.PyYieldExpression;
import com.jetbrains.python.validation.PyAnnotationHolder;
import consulo.language.editor.annotation.HighlightSeverity;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.python.impl.localize.PyLocalize;
import org.jspecify.annotations.Nullable;


final class PyStarAnnotatorVisitor extends PyElementVisitor {
    private final PyAnnotationHolder myHolder;

    PyStarAnnotatorVisitor(PyAnnotationHolder holder) {
        myHolder = holder;
    }

    @Override
    public void visitPyStarExpression(PyStarExpression node) {
        super.visitPyStarExpression(node);
        PsiElement parent = node.getParent();
        if (parent.getParent() instanceof PyTypeHintFile) {
            return;
        }
        if (!node.isAssignmentTarget() &&
            !(allowedUnpacking(node)) &&
            !(parent instanceof PyParameterTypeList) &&
            !(parent instanceof PyTypeParameter) &&
            !(parent instanceof PyAnnotation && isVariadicArg(parent.getParent()))) {
            myHolder.newAnnotation(HighlightSeverity.ERROR, PyLocalize.annCanTUseStarredExpressionHere().get()).create();
        }
    }

    private static boolean allowedUnpacking(PyStarExpression starExpression) {
        // PEP 798: unpacking in comprehensions/generator expressions, e.g. [*it for it in its]
        if (starExpression.getParent() instanceof PyComprehensionElement comprehension &&
            comprehension.getResultExpression() == starExpression) {
            return true;
        }
        if (!starExpression.isUnpacking()) {
            return false;
        }

        // Additional contexts where unpacking is prohibited depending on the language version are covered in CompatibilityVisitor.
        PsiElement parent = PsiTreeUtil.skipParentsOfType(starExpression, PyParenthesizedExpression.class);
        if (parent instanceof PyTupleExpression) {
            PsiElement tupleParent = parent.getParent();
            if (tupleParent instanceof PyYieldExpression && ((PyYieldExpression)tupleParent).isDelegating()) {
                return false;
            }
        }
        return true;
    }

    public static boolean isVariadicArg(@Nullable PsiElement parameter) {
        return parameter instanceof PyNamedParameter && (((PyNamedParameter)parameter).isPositionalContainer());
    }
}
