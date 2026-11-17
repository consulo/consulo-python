// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.validation;

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.impl.inspections.quickfix.WrapExceptTupleInParenthesesQuickFix;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyBreakStatement;
import com.jetbrains.python.psi.PyContinueStatement;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExceptPart;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFinallyPart;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyLoopStatement;
import com.jetbrains.python.psi.PyParenthesizedExpression;
import com.jetbrains.python.psi.PyRaiseStatement;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyReturnStatement;
import com.jetbrains.python.psi.PyTryExceptStatement;
import com.jetbrains.python.psi.PyTupleExpression;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.validation.PyAnnotationHolder;
import consulo.document.util.TextRange;
import consulo.language.editor.annotation.HighlightSeverity;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.python.impl.localize.PyLocalize;
import org.jspecify.annotations.Nullable;

final class PyTryExceptAnnotatorVisitor extends PyElementVisitor {
    private final PyAnnotationHolder myHolder;

    PyTryExceptAnnotatorVisitor(PyAnnotationHolder holder) {
        myHolder = holder;
    }

    @Override
    public void visitPyTryExceptStatement(PyTryExceptStatement node) {
        boolean haveDefaultExcept = false;
        for (PyExceptPart part : node.getExceptParts()) {
            if (haveDefaultExcept) {
                myHolder.newAnnotation(HighlightSeverity.ERROR, PyLocalize.annDefaultExceptMustBeLast().get()).range(part).create();
            }
            if (part.getExceptClass() == null) {
                haveDefaultExcept = true;
            }
        }

        boolean haveStar = false;
        boolean haveNotStar = false;
        for (PyExceptPart exceptPart : node.getExceptParts()) {
            PsiElement star = PyPsiUtils.getFirstChildOfType(exceptPart, PyTokenTypes.MULT);
            if (star != null) {
                haveStar = true;
            }
            else {
                haveNotStar = true;
            }
            if (haveNotStar && haveStar) {
                PsiElement exceptKeyword = exceptPart.getFirstChild();
                int startOffset = exceptKeyword.getTextOffset();
                int endOffset = exceptKeyword.getTextRange().getEndOffset();
                if (star != null) {
                    endOffset = star.getTextRange().getEndOffset();
                }
                TextRange textRange = new TextRange(startOffset, endOffset);
                myHolder.newAnnotation(HighlightSeverity.ERROR, PyLocalize.annTryExceptCanNotHaveExceptAndStarExcept().get())
                    .range(textRange).create();
                break;
            }
        }
    }

    @Override
    public void visitPyRaiseStatement(PyRaiseStatement node) {
        if (node.getExpressions().length == 0 &&
            PsiTreeUtil.getParentOfType(node, PyExceptPart.class, PyFinallyPart.class, PyFunction.class) == null) {
            myHolder.markError(node, PyLocalize.annNoExceptionToReraise().get());
        }
    }

    private static @Nullable PyReferenceExpression tryGetExceptionGroupInExpression(@Nullable PsiElement exceptExpression) {
        if (exceptExpression instanceof PyReferenceExpression &&
            "ExceptionGroup".equals(((PyReferenceExpression)exceptExpression).getName())) {
            return (PyReferenceExpression)exceptExpression;
        }
        if (exceptExpression instanceof PyParenthesizedExpression) {
            return tryGetExceptionGroupInExpression(PyPsiUtils.flattenParens((PyParenthesizedExpression)exceptExpression));
        }
        if (exceptExpression instanceof PyTupleExpression) {
            for (PsiElement child : exceptExpression.getChildren()) {
                PyReferenceExpression result = tryGetExceptionGroupInExpression(child);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    @Override
    public void visitPyExceptBlock(PyExceptPart node) {
        if (node.isStar()) {
            PyExpression exceptClass = node.getExceptClass();
            PyReferenceExpression exceptionGroup = tryGetExceptionGroupInExpression(exceptClass);
            if (exceptionGroup != null) {
                myHolder.newAnnotation(HighlightSeverity.ERROR, PyLocalize.annExceptionGroupInStarExcept().get()).range(exceptionGroup)
                    .create();
            }
        }

        // Add PEP-758 Py314+ missing parentheses check: except Error1, Error2 as e:
        LanguageLevel level = LanguageLevel.forElement(node);
        if (!level.isPy3K()) {
            return;
        }
        if (node.getTarget() == null) {
            return;
        }
        PyExpression exceptExpr = node.getExceptClass();
        if (exceptExpr instanceof PyParenthesizedExpression) {
            return;
        }
        if (exceptExpr instanceof PyTupleExpression tuple && tuple.getElements().length > 1) {
            myHolder.newAnnotation(HighlightSeverity.ERROR, PyLocalize.inspExceptClauseMissingParens().get())
                .range(tuple)
                .withFix(new WrapExceptTupleInParenthesesQuickFix(tuple))
                .create();
        }
    }

    @Override
    public void visitPyReturnStatement(PyReturnStatement node) {
        PyExceptPart exceptPart = PsiTreeUtil.getParentOfType(node, PyExceptPart.class, false, PyFunction.class);
        if (exceptPart != null && exceptPart.isStar()) {
            myHolder.newAnnotation(HighlightSeverity.ERROR, PyLocalize.annContinueBreakOrReturnInStarExcept().get()).create();
        }
    }

    private void checkForContinueAndReturn(PsiElement node) {
        PyExceptPart exceptPart = PsiTreeUtil.getParentOfType(node, PyExceptPart.class, false, PyLoopStatement.class);
        if (exceptPart != null && exceptPart.isStar()) {
            myHolder.newAnnotation(HighlightSeverity.ERROR, PyLocalize.annContinueBreakOrReturnInStarExcept().get()).create();
        }
    }

    @Override
    public void visitPyContinueStatement(PyContinueStatement node) {
        checkForContinueAndReturn(node);
    }

    @Override
    public void visitPyBreakStatement(PyBreakStatement node) {
        checkForContinueAndReturn(node);
    }
}
