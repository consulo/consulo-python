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
package com.jetbrains.python.impl.codeInsight.intentions;

import com.jetbrains.python.impl.psi.impl.PyStatementListImpl;
import com.jetbrains.python.psi.*;
import consulo.codeEditor.Editor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import jakarta.annotation.Nonnull;

import java.util.List;

/**
 * @author catherine
 */
public class ReplaceListComprehensionWithForIntention extends PyBaseIntentionAction {
    @Nonnull
    @Override
    public LocalizeValue getText() {
        return PyLocalize.intnReplaceListComprehensionsWithFor();
    }

    public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
        if (!(file instanceof PyFile)) {
            return false;
        }

        PyListCompExpression expression =
            PsiTreeUtil.getTopmostParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), PyListCompExpression.class);
        if (expression == null) {
            return false;
        }
        if (expression.getComponents().isEmpty()) {
            return false;
        }
        PsiElement parent = expression.getParent();
        if (parent instanceof PyAssignmentStatement || parent instanceof PyPrintStatement) {
            return true;
        }
        return false;
    }

    @Override
    public void doInvoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        PyListCompExpression expression =
            PsiTreeUtil.getTopmostParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), PyListCompExpression.class);
        if (expression == null) {
            return;
        }
        PsiElement parent = expression.getParent();
        PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);

        if (parent instanceof PyAssignmentStatement) {
            final PsiElement leftExpr = ((PyAssignmentStatement) parent).getLeftHandSideExpression();
            if (leftExpr == null) {
                return;
            }
            PyAssignmentStatement initAssignment = elementGenerator.createFromText(
                LanguageLevel.forElement(expression),
                PyAssignmentStatement.class,
                leftExpr.getText() + " = []"
            );
            PyForStatement forStatement = createForLoop(
                expression,
                elementGenerator,
                leftExpr.getText() + ".append(" + expression.getResultExpression().getText() + ")"
            );

            PyStatementList stList = new PyStatementListImpl(initAssignment.getNode());
            stList.add(initAssignment);
            stList.add(forStatement);
            stList.getStatements()[0].delete();
            parent.replace(stList);

        }
        else if (parent instanceof PyPrintStatement) {
            PyForStatement forStatement =
                createForLoop(expression, elementGenerator, "print " + "(" + expression.getResultExpression().getText() + ")");
            parent.replace(forStatement);
        }
    }

    private static PyForStatement createForLoop(
        final PyListCompExpression expression,
        final PyElementGenerator elementGenerator,
        final String result
    ) {
        final List<PyComprehensionComponent> components = expression.getComponents();
        final StringBuilder stringBuilder = new StringBuilder();
        int slashNum = 1;
        for (PyComprehensionComponent component : components) {
            if (component instanceof PyComprehensionForComponent) {
                stringBuilder.append("for ");
                stringBuilder.append(((PyComprehensionForComponent) component).getIteratorVariable().getText());
                stringBuilder.append(" in ");
                stringBuilder.append(((PyComprehensionForComponent) component).getIteratedList().getText());
                stringBuilder.append(":\n");
            }
            if (component instanceof PyComprehensionIfComponent) {
                final PyExpression test = ((PyComprehensionIfComponent) component).getTest();
                if (test != null) {
                    stringBuilder.append("if ");
                    stringBuilder.append(test.getText());
                    stringBuilder.append(":\n");
                }
            }
            for (int i = 0; i != slashNum; ++i) {
                stringBuilder.append("\t");
            }
            ++slashNum;
        }
        stringBuilder.append(result);
        return elementGenerator.createFromText(LanguageLevel.forElement(expression), PyForStatement.class, stringBuilder.toString());
    }
}