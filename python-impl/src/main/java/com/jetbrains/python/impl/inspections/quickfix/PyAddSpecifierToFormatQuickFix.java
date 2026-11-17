// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.quickfix;

import com.jetbrains.python.impl.PyStringFormatParser;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import com.jetbrains.python.impl.psi.types.PyTypeChecker;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyParenthesizedExpression;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PyTupleExpression;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.access.RequiredWriteAction;
import consulo.document.Document;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;

import java.util.List;

import static com.jetbrains.python.impl.PyStringFormatParser.filterSubstitutions;
import static com.jetbrains.python.impl.PyStringFormatParser.parsePercentFormat;

// JB: PsiUpdateModCommandQuickFix (ModCommand API is not available in Consulo)
public class PyAddSpecifierToFormatQuickFix implements LocalQuickFix {
    @Override
    public LocalizeValue getName() {
        return PyLocalize.qfixNameAddSpecifier();
    }

    @Override
    @RequiredWriteAction
    public void applyFix(Project project, ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();
        if (element == null) {
            return;
        }
        final PyBinaryExpression expression = PsiTreeUtil.getParentOfType(element, PyBinaryExpression.class);
        if (expression == null) {
            return;
        }
        PyExpression rightExpression = expression.getRightExpression();
        if (rightExpression instanceof PyParenthesizedExpression) {
            rightExpression = ((PyParenthesizedExpression) rightExpression).getContainedExpression();
        }
        if (rightExpression == null) {
            return;
        }

        final PsiFile file = element.getContainingFile();
        final Document document = file.getViewProvider().getDocument();
        if (document == null) {
            return;
        }
        final int offset = element.getTextOffset();
        final TypeEvalContext context = TypeEvalContext.userInitiated(file.getProject(), file);

        final PyClassType strType = PyBuiltinCache.getInstance(element).getStrType();
        final PyClassType floatType = PyBuiltinCache.getInstance(element).getFloatType();
        final PyClassType intType = PyBuiltinCache.getInstance(element).getIntType();

        final PyExpression leftExpression = expression.getLeftExpression();
        if (leftExpression instanceof PyStringLiteralExpression) {
            final List<PyStringFormatParser.SubstitutionChunk> chunks =
                filterSubstitutions(parsePercentFormat(leftExpression.getText()));
            PyExpression[] elements;
            if (rightExpression instanceof PyTupleExpression) {
                elements = ((PyTupleExpression) rightExpression).getElements();
            }
            else {
                elements = new PyExpression[]{rightExpression};
            }

            int shift = 1;
            for (int i = 0; i < chunks.size(); i++) {
                final PyStringFormatParser.PercentSubstitutionChunk chunk =
                    PyUtil.as(chunks.get(i), PyStringFormatParser.PercentSubstitutionChunk.class);
                if (chunk != null) {
                    if (elements.length <= i) {
                        return;
                    }
                    final PyType type = context.getType(elements[i]);
                    final char conversionType = chunk.getConversionType();
                    if (conversionType == '\u0000') {
                        final int insertOffset = offset + chunk.getStartIndex() + shift;
                        if (insertOffset > leftExpression.getTextRange().getEndOffset()) {
                            return;
                        }
                        if (PyTypeChecker.match(strType, type, context)) {
                            document.insertString(insertOffset, "s");
                            shift += 1;
                        }
                        if (PyTypeChecker.match(intType, type, context) || PyTypeChecker.match(floatType, type, context)) {
                            document.insertString(insertOffset, "d");
                            shift += 1;
                        }
                    }
                }
            }
        }
    }
}
