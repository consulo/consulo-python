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

import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import jakarta.annotation.Nonnull;

/**
 * QuickFix to replace true with True, false with False
 *
 * @author catherine
 */
public class UnresolvedRefTrueFalseQuickFix implements LocalQuickFix {
    PsiElement myElement;
    String newName;

    public UnresolvedRefTrueFalseQuickFix(PsiElement element) {
        myElement = element;
        char[] charArray = element.getText().toCharArray();
        charArray[0] = Character.toUpperCase(charArray[0]);
        newName = new String(charArray);
    }

    @Nonnull
    @Override
    public LocalizeValue getName() {
        return PyLocalize.qfixUnresolvedReferenceReplace$0(newName);
    }

    public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
        PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);

        PyExpression expression = elementGenerator.createExpressionFromText(newName);
        myElement.replace(expression);
    }
}
