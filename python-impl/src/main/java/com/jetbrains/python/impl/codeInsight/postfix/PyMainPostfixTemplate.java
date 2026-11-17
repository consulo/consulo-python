// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.postfix;

import com.jetbrains.python.impl.refactoring.surround.surrounders.statements.PyStatementSurrounder;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyIfStatement;
import com.jetbrains.python.psi.PyStatementList;
import consulo.application.dumb.DumbAware;
import consulo.codeEditor.Editor;
import consulo.document.util.TextRange;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.editor.postfixTemplate.PostfixTemplateProvider;
import consulo.language.editor.refactoring.postfixTemplate.SurroundPostfixTemplateBase;
import consulo.language.editor.surroundWith.Surrounder;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;

public class PyMainPostfixTemplate extends SurroundPostfixTemplateBase implements DumbAware {

    public static final String DESCR = "if __name__ == '__main__': expr";

    protected PyMainPostfixTemplate(PostfixTemplateProvider provider) {
        super("main", DESCR, PyPostfixUtils.PY_PSI_INFO, PyPostfixUtils.currentStatementSelector(), provider);
    }

    @Override
    protected Surrounder getSurrounder() {
        return new PyStatementSurrounder() {
            @Override
            protected TextRange surroundStatement(Project project, Editor editor, PsiElement[] elements)
                throws IncorrectOperationException {
                PyIfStatement ifStatement = PyElementGenerator.getInstance(project).createFromText(LanguageLevel.forElement(elements[0]),
                    PyIfStatement.class, "if __name__ == '__main__':\n expr");
                ifStatement = (PyIfStatement) CodeStyleManager.getInstance(project).reformat(ifStatement);
                PsiElement parent = elements[0].getParent();
                ifStatement = (PyIfStatement) parent.addBefore(ifStatement, elements[0]);
                PyStatementList statementList = ifStatement.getIfPart().getStatementList();
                statementList.addRange(elements[0], elements[elements.length - 1]);
                statementList.getFirstChild().delete();
                parent.deleteChildRange(elements[0], elements[elements.length - 1]);
                return TextRange.from(statementList.getTextRange().getEndOffset(), 0);
            }

            @Override
            public LocalizeValue getTemplateDescription() {
                return LocalizeValue.of(DESCR);
            }
        };
    }
}
