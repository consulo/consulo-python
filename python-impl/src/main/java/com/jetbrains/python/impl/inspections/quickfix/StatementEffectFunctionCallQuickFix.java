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

import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiWhiteSpace;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import jakarta.annotation.Nonnull;

/**
 * QuickFix to replace statement that has no effect with function call
 *
 * @author catherine
 */
public class StatementEffectFunctionCallQuickFix implements LocalQuickFix {
    @Nonnull
    @Override
    public LocalizeValue getName() {
        return PyLocalize.qfixStatementEffect();
    }

    public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
        PsiElement expression = descriptor.getPsiElement();
        if (expression != null && expression.isWritable() && expression instanceof PyReferenceExpression) {
            PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
            if ("print".equals(expression.getText())) {
                replacePrint(expression, elementGenerator);
            }
            else {
                expression.replace(elementGenerator.createCallExpression(LanguageLevel.forElement(expression), expression.getText()));
            }
        }
    }

    private static void replacePrint(PsiElement expression, PyElementGenerator elementGenerator) {
        StringBuilder stringBuilder = new StringBuilder("print (");

        PsiElement whiteSpace = expression.getContainingFile().findElementAt(expression.getTextOffset() + expression.getTextLength());
        PsiElement next = null;
        if (whiteSpace instanceof PsiWhiteSpace) {
            String whiteSpaceText = whiteSpace.getText();
            if (!whiteSpaceText.contains("\n")) {
                next = whiteSpace.getNextSibling();
                while (next instanceof PsiWhiteSpace && whiteSpaceText.contains("\\")) {
                    next = next.getNextSibling();
                }
            }
        }
        else {
            next = whiteSpace;
        }

        RemoveUnnecessaryBackslashQuickFix.removeBackSlash(next);
        if (whiteSpace != null) {
            whiteSpace.delete();
        }
        if (next != null) {
            String text = next.getText();
            stringBuilder.append(text);
            if (text.endsWith(",")) {
                stringBuilder.append(" end=' '");
            }
            next.delete();
        }
        stringBuilder.append(")");
        expression.replace(elementGenerator.createFromText(LanguageLevel.forElement(expression), PyExpression.class,
            stringBuilder.toString()
        ));
    }
}
