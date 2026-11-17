// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.quickfix;

import com.jetbrains.python.impl.refactoring.PyPsiRefactoringUtil;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.PyStatementList;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.access.RequiredWriteAction;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;

public class PyCreatePropertyQuickFix implements LocalQuickFix {
    private final AccessDirection myAccessDirection;

    public PyCreatePropertyQuickFix(AccessDirection dir) {
        myAccessDirection = dir;
    }

    @Override
    public LocalizeValue getName() {
        return PyLocalize.qfixCreateProperty();
    }

    @Override
    @RequiredWriteAction
    public void applyFix(Project project, ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();
        if (element instanceof PyQualifiedExpression) {
            PyExpression qualifier = ((PyQualifiedExpression)element).getQualifier();
            if (qualifier != null) {
                PyType type = TypeEvalContext.codeAnalysis(element.getProject(), element.getContainingFile()).getType(qualifier);
                if (type instanceof PyClassType) {
                    PyClass cls = ((PyClassType)type).getPyClass();
                    String propertyName = ((PyQualifiedExpression)element).getName();
                    if (propertyName == null) {
                        return;
                    }
                    String fieldName = "_" + propertyName;
                    PyElementGenerator generator = PyElementGenerator.getInstance(project);
                    PyFunction property = generator.createProperty(LanguageLevel.forElement(cls), propertyName, fieldName, myAccessDirection);
                    // Consulo: no ModPsiUpdater.getWritable, the fix runs on the physical file
                    PyStatementList statementsList = cls.getStatementList();
                    PyPsiRefactoringUtil.addElementToStatementList(property, statementsList, myAccessDirection == AccessDirection.READ);
                }
            }
        }
    }
}
