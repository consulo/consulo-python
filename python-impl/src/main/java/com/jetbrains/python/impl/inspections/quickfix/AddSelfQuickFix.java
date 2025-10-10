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
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyParameterList;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import jakarta.annotation.Nonnull;

/**
 * Insert 'self' in a method that lacks any arguments
 *
 * @author dcheryasov
 * @since 2008-11-19
 */
public class AddSelfQuickFix implements LocalQuickFix {
    private final String myParamName;

    public AddSelfQuickFix(String paramName) {
        myParamName = paramName;
    }

    @Nonnull
    @Override
    public LocalizeValue getName() {
        return PyLocalize.qfixAddParameterSelf(myParamName);
    }

    public void applyFix(@Nonnull final Project project, @Nonnull final ProblemDescriptor descriptor) {
        PsiElement problem_elt = descriptor.getPsiElement();
        if (problem_elt instanceof PyParameterList) {
            final PyParameterList param_list = (PyParameterList) problem_elt;
            if (!FileModificationService.getInstance().preparePsiElementForWrite(problem_elt)) {
                return;
            }
            PyNamedParameter new_param = PyElementGenerator.getInstance(project).createParameter(myParamName);
            param_list.addParameter(new_param);
        }
    }
}
