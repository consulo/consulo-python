/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyElementType;
import com.jetbrains.python.psi.PyExpression;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import jakarta.annotation.Nonnull;

/**
 * @author Alexey.Ivanov
 * @since 2010-03-24
 */
public class ComparisonWithNoneQuickFix implements LocalQuickFix {
    @Nonnull
    @Override
    public LocalizeValue getName() {
        return PyLocalize.qfixReplaceEquality();
    }

    public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
        PsiElement problemElement = descriptor.getPsiElement();
        if (problemElement instanceof PyBinaryExpression) {
            PyBinaryExpression binaryExpression = (PyBinaryExpression) problemElement;
            PyElementType operator = binaryExpression.getOperator();
            PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
            String temp;
            temp = (operator == PyTokenTypes.EQEQ) ? "is" : "is not";
            PyExpression expression = elementGenerator.createBinaryExpression(
                temp,
                binaryExpression.getLeftExpression(),
                binaryExpression.getRightExpression()
            );
            binaryExpression.replace(expression);
        }
    }
}
