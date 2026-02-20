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
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import jakarta.annotation.Nonnull;

public class PyConvertToNewStyleQuickFix implements LocalQuickFix {
    @Nonnull
    @Override
    public LocalizeValue getName() {
        return PyLocalize.qfixConvertToNewStyle();
    }

    @Override
    public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();
        PyClass pyClass = PsiTreeUtil.getParentOfType(element, PyClass.class);
        assert pyClass != null;

        PyElementGenerator generator = PyElementGenerator.getInstance(project);
        PyArgumentList expressionList = pyClass.getSuperClassExpressionList();
        if (expressionList != null) {
            PyExpression object = generator.createExpressionFromText(LanguageLevel.forElement(element), "object");
            expressionList.addArgumentFirst(object);
        }
        else {
            PyArgumentList list = generator.createFromText(LanguageLevel.forElement(element), PyClass.class, "class A(object):pass")
                .getSuperClassExpressionList();
            assert list != null;
            ASTNode node = pyClass.getNameNode();
            assert node != null;
            pyClass.addAfter(list, node.getPsi());
        }
    }
}
