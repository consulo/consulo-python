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

import com.jetbrains.python.psi.*;
import consulo.language.ast.ASTNode;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiPolyVariantReference;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import jakarta.annotation.Nonnull;

public class PyAddExceptionSuperClassQuickFix implements LocalQuickFix {
    @Nonnull
    @Override
    public LocalizeValue getName() {
        return PyLocalize.qfixNameAddExceptionBase();
    }

    public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
        final PsiElement element = descriptor.getPsiElement();
        if (element instanceof PyCallExpression) {
            PyExpression callee = ((PyCallExpression) element).getCallee();
            if (callee instanceof PyReferenceExpression) {
                final PsiPolyVariantReference reference = ((PyReferenceExpression) callee).getReference();
                PsiElement psiElement = reference.resolve();
                if (psiElement instanceof PyClass) {
                    final PyElementGenerator generator = PyElementGenerator.getInstance(project);
                    final PyArgumentList list = ((PyClass) psiElement).getSuperClassExpressionList();
                    if (list != null) {
                        final PyExpression exception = generator.createExpressionFromText(LanguageLevel.forElement(element), "Exception");
                        list.addArgument(exception);
                    }
                    else {
                        final PyArgumentList expressionList =
                            generator.createFromText(LanguageLevel.forElement(element), PyClass.class, "class A(Exception): pass")
                                .getSuperClassExpressionList();
                        assert expressionList != null;
                        final ASTNode nameNode = ((PyClass) psiElement).getNameNode();
                        assert nameNode != null;
                        psiElement.addAfter(expressionList, nameNode.getPsi());
                    }
                }
            }
        }
    }
}
