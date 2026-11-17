// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.quickfix;

import com.jetbrains.python.impl.refactoring.PyPsiRefactoringUtil;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.Property;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import consulo.annotation.access.RequiredWriteAction;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.util.lang.StringUtil;

import java.util.Map;

public class PyAddPropertyForFieldQuickFix implements LocalQuickFix {
    private final LocalizeValue myName;

    public PyAddPropertyForFieldQuickFix(LocalizeValue name) {
        myName = name;
    }

    @Override
    public LocalizeValue getName() {
        return myName;
    }

    @Override
    @RequiredWriteAction
    public void applyFix(Project project, ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();
        if (element instanceof PyReferenceExpression) {
            PsiReference reference = element.getReference();
            // Consulo: no ModPsiUpdater.getWritable, the fix runs on the physical file
            PsiElement resolved = reference.resolve();
            if (resolved instanceof PyTargetExpression target) {
                PyClass containingClass = target.getContainingClass();
                if (containingClass != null) {
                    String name = target.getName();
                    if (name == null) {
                        return;
                    }
                    String propertyName = StringUtil.trimStart(name, "_");
                    Map<String, Property> properties = containingClass.getProperties();
                    PyElementGenerator generator = PyElementGenerator.getInstance(project);
                    if (!properties.containsKey(propertyName)) {
                        PyFunction property =
                            generator.createProperty(LanguageLevel.forElement(containingClass), propertyName, name, AccessDirection.READ);
                        PyPsiRefactoringUtil.addElementToStatementList(property, containingClass.getStatementList(), false);
                    }
                    PyExpression qualifier = ((PyReferenceExpression)element).getQualifier();
                    if (qualifier != null) {
                        String newElementText = qualifier.getText() + "." + propertyName;
                        PyExpression newElement = generator.createExpressionFromText(LanguageLevel.forElement(containingClass), newElementText);
                        element.replace(newElement);
                    }
                }
            }
        }
    }
}
