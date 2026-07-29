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
package com.jetbrains.python.impl.refactoring.surround.surrounders.expressions;

import com.jetbrains.python.psi.PyExpression;
import consulo.codeEditor.Editor;
import consulo.document.util.TextRange;
import consulo.language.editor.surroundWith.Surrounder;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.project.Project;

/**
 * @author Alexey.Ivanov
 * @since 2009-08-27
 */
public abstract class PyExpressionSurrounder implements Surrounder {
    private static final Logger LOG = Logger.getInstance(PyExpressionSurrounder.class);

    @Override
    public boolean isApplicable(PsiElement[] elements) {
        LOG.assertTrue(elements.length == 1 && elements[0] instanceof PyExpression);
        return isApplicable((PyExpression) elements[0]);
    }

    public abstract boolean isApplicable(PyExpression expr);

    public abstract TextRange surroundExpression(Project project, Editor editor, PyExpression element)
        throws IncorrectOperationException;

    @Override
    public TextRange surroundElements(Project project, Editor editor, PsiElement[] elements) throws IncorrectOperationException {
        return surroundExpression(project, editor, (PyExpression) elements[0]);
    }
}
