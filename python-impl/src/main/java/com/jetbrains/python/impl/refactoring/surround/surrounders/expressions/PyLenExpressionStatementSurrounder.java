// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.impl.refactoring.surround.surrounders.expressions;

import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyExpressionStatement;
import consulo.codeEditor.Editor;
import consulo.document.util.TextRange;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;

public class PyLenExpressionStatementSurrounder extends PyExpressionSurrounder {
    public static final int FUNCTION_LENGTH = "len()".length();

    @Override
    public TextRange surroundExpression(Project project, Editor editor, PyExpression element) throws IncorrectOperationException {
        TextRange range = element.getTextRange();
        PyExpressionStatement statement = PyElementGenerator.getInstance(project)
            .createFromText(LanguageLevel.getDefault(), PyExpressionStatement.class, "len(a)");
        PyCallExpression callExpression = (PyCallExpression) statement.getExpression();
        PyExpression arg = callExpression.getArguments()[0];
        arg.replace(element);
        element.replace(callExpression);
        return TextRange.from(range.getEndOffset() + FUNCTION_LENGTH, 0);
    }

    @Override
    public boolean isApplicable(PyExpression expr) {
        return true;
    }

    @Override
    public LocalizeValue getTemplateDescription() {
        return PyLocalize.surroundWithLenTemplate();
    }
}
