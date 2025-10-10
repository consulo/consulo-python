/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.impl.inspections.quickfix;

import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import jakarta.annotation.Nonnull;

public class PyCreatePropertyQuickFix implements LocalQuickFix {
    private final AccessDirection myAccessDirection;

    public PyCreatePropertyQuickFix(AccessDirection dir) {
        myAccessDirection = dir;
    }

    @Nonnull
    @Override
    public LocalizeValue getName() {
        return PyLocalize.qfixCreateProperty();
    }

    public void applyFix(@Nonnull final Project project, @Nonnull final ProblemDescriptor descriptor) {
        final PsiElement element = descriptor.getPsiElement();
        if (element instanceof PyQualifiedExpression) {
            final PyExpression qualifier = ((PyQualifiedExpression) element).getQualifier();
            if (qualifier != null) {
                final PyType type = TypeEvalContext.codeAnalysis(element.getProject(), element.getContainingFile()).getType(qualifier);
                if (type instanceof PyClassType) {
                    final PyClass cls = ((PyClassType) type).getPyClass();
                    final String propertyName = ((PyQualifiedExpression) element).getName();
                    if (propertyName == null) {
                        return;
                    }
                    final String fieldName = "_" + propertyName;
                    final PyElementGenerator generator = PyElementGenerator.getInstance(project);
                    final PyFunction property =
                        generator.createProperty(LanguageLevel.forElement(cls), propertyName, fieldName, myAccessDirection);
                    PyUtil.addElementToStatementList(property, cls.getStatementList(), myAccessDirection == AccessDirection.READ);
                }
            }
        }
    }
}
