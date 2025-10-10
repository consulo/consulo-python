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
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExceptPart;
import com.jetbrains.python.psi.PyTryExceptStatement;
import consulo.language.ast.ASTNode;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiWhiteSpace;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import jakarta.annotation.Nonnull;

/**
 * @author Alexey.Ivanov
 */
public class ReplaceExceptPartQuickFix implements LocalQuickFix {
    @Nonnull
    @Override
    public LocalizeValue getName() {
        return PyLocalize.intnConvertExceptTo();
    }

    @Override
    public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
        PsiElement exceptPart = descriptor.getPsiElement();
        if (exceptPart instanceof PyExceptPart) {
            PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
            PsiElement element = ((PyExceptPart) exceptPart).getExceptClass().getNextSibling();
            while (element instanceof PsiWhiteSpace) {
                element = element.getNextSibling();
            }
            assert element != null;
            PyTryExceptStatement newElement =
                elementGenerator.createFromText(
                    LanguageLevel.forElement(exceptPart),
                    PyTryExceptStatement.class,
                    "try:  pass except a as b:  pass"
                );
            ASTNode node = newElement.getExceptParts()[0].getNode().findChildByType(PyTokenTypes.AS_KEYWORD);
            assert node != null;
            element.replace(node.getPsi());
        }
    }
}
