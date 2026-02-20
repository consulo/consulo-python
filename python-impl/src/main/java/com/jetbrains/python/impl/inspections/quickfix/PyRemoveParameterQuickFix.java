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

import com.jetbrains.python.impl.documentation.docstrings.PyDocstringGenerator;
import com.jetbrains.python.impl.refactoring.PyRefactoringUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.usage.UsageInfo;
import jakarta.annotation.Nonnull;

import java.util.List;
import java.util.Map;

public class PyRemoveParameterQuickFix implements LocalQuickFix {
    @Nonnull
    @Override
    public LocalizeValue getName() {
        return PyLocalize.qfixNameRemoveParameter();
    }

    public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();
        assert element instanceof PyParameter;

        PyFunction pyFunction = PsiTreeUtil.getParentOfType(element, PyFunction.class);

        if (pyFunction != null) {
            List<UsageInfo> usages = PyRefactoringUtil.findUsages(pyFunction, false);
            for (UsageInfo usage : usages) {
                PsiElement usageElement = usage.getElement();
                if (usageElement != null) {
                    PsiElement callExpression = usageElement.getParent();
                    if (callExpression instanceof PyCallExpression) {
                        PyArgumentList argumentList = ((PyCallExpression) callExpression).getArgumentList();
                        if (argumentList != null) {
                            PyResolveContext resolveContext = PyResolveContext.noImplicits();
                            PyCallExpression.PyArgumentsMapping mapping =
                                ((PyCallExpression) callExpression).mapArguments(resolveContext);
                            for (Map.Entry<PyExpression, PyNamedParameter> parameterEntry : mapping.getMappedParameters().entrySet()) {
                                if (parameterEntry.getValue().equals(element)) {
                                    parameterEntry.getKey().delete();
                                }
                            }
                        }
                    }
                }
            }
            PyStringLiteralExpression expression = pyFunction.getDocStringExpression();
            String paramName = ((PyParameter) element).getName();
            if (expression != null && paramName != null) {
                PyDocstringGenerator.forDocStringOwner(pyFunction).withoutParam(paramName).buildAndInsert();
            }
        }
        element.delete();
    }
}
