// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.postfix;

import com.jetbrains.python.impl.refactoring.surround.surrounders.expressions.PyExpressionSurrounder;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyStatement;
import consulo.application.dumb.DumbAware;
import consulo.codeEditor.Editor;
import consulo.document.util.TextRange;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.editor.postfixTemplate.PostfixTemplateProvider;
import consulo.language.editor.refactoring.postfixTemplate.SurroundPostfixTemplateBase;
import consulo.language.editor.surroundWith.Surrounder;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import org.jspecify.annotations.Nullable;

public class PyPrintPostfixTemplate extends SurroundPostfixTemplateBase implements DumbAware {

    public static final String DESCR = "print(expr)";

    protected PyPrintPostfixTemplate(PostfixTemplateProvider provider) {
        super("print", DESCR, PyPostfixUtils.PY_PSI_INFO, PyPostfixUtils.selectorTopmost(), provider);
    }

    @Override
    protected Surrounder getSurrounder() {
        return new PyExpressionSurrounder() {
            @Override
            public boolean isApplicable(PyExpression expr) {
                return true;
            }

            @Override
            public @Nullable TextRange surroundExpression(Project project, Editor editor, PyExpression expression)
                throws IncorrectOperationException {
                LanguageLevel level = LanguageLevel.forElement(expression);
                String textToGenerate = !level.isPython2() ? "print(a)" : "print a";
                PyStatement pyStatement = PyElementGenerator.getInstance(project).createFromText(level, PyStatement.class, textToGenerate);
                if (level.isPython2()) {
                    pyStatement.getLastChild().replace(expression);
                }
                else {
                    PyArgumentList argumentList = PsiTreeUtil.findChildOfType(pyStatement, PyArgumentList.class);
                    if (argumentList == null) {
                        return null;
                    }
                    argumentList.getArguments()[0].replace(expression);
                }
                pyStatement = (PyStatement) CodeStyleManager.getInstance(project).reformat(pyStatement);
                pyStatement = (PyStatement) expression.getParent().replace(pyStatement);
                return TextRange.from(pyStatement.getTextRange().getEndOffset(), 0);
            }

            @Override
            public LocalizeValue getTemplateDescription() {
                return LocalizeValue.of(DESCR);
            }
        };
    }
}
