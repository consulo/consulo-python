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

import com.jetbrains.python.psi.*;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import jakarta.annotation.Nonnull;

/**
 * @author Alexey.Ivanov
 * @since 2010-02-16
 */
public class ConvertSetLiteralQuickFix implements LocalQuickFix {
    @Nonnull
    @Override
    public LocalizeValue getName() {
        return PyLocalize.intnConvertSetLiteralTo();
    }

    @Override
    public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
        PsiElement setLiteral = descriptor.getPsiElement();
        if (setLiteral instanceof PySetLiteralExpression) {
            PyExpression[] expressions = ((PySetLiteralExpression) setLiteral).getElements();
            PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
            assert expressions.length != 0;
            StringBuilder stringBuilder = new StringBuilder(expressions[0].getText());
            for (int i = 1; i < expressions.length; ++i) {
                stringBuilder.append(", ");
                stringBuilder.append(expressions[i].getText());
            }
            PyStatement newElement = elementGenerator.createFromText(
                LanguageLevel.getDefault(),
                PyExpressionStatement.class,
                "set([" + stringBuilder.toString() + "])"
            );
            setLiteral.replace(newElement);
        }
    }
}
