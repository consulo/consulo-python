// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.quickfix;

import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.PyImportStatement;
import com.jetbrains.python.psi.PyReferenceExpression;
import consulo.annotation.access.RequiredWriteAction;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;

public class ReplaceBuiltinsQuickFix implements LocalQuickFix {
    @Override
    public LocalizeValue getName() {
        return PyLocalize.intnConvertBuiltinImport();
    }

    @Override
    @RequiredWriteAction
    public void applyFix(Project project, ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();
        PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
        if (element instanceof PyImportStatement) {
            for (PyImportElement importElement : ((PyImportStatement)element).getImportElements()) {
                PyReferenceExpression importReference = importElement.getImportReferenceExpression();
                if (importReference != null) {
                    if ("__builtin__".equals(importReference.getName())) {
                        importReference.replace(elementGenerator.createExpressionFromText(LanguageLevel.getDefault(), "builtins"));
                    }
                    else if ("builtins".equals(importReference.getName())) {
                        importReference.replace(elementGenerator.createExpressionFromText(LanguageLevel.getDefault(), "__builtin__"));
                    }
                }
            }
        }
    }
}
