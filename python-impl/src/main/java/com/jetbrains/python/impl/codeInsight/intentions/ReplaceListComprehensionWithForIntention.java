// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.intentions;

import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyComprehensionComponent;
import com.jetbrains.python.psi.PyComprehensionForComponent;
import com.jetbrains.python.psi.PyComprehensionIfComponent;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyForStatement;
import com.jetbrains.python.psi.PyListCompExpression;
import com.jetbrains.python.psi.PyPrintStatement;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.editor.intention.PsiElementBaseIntentionAction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import org.jspecify.annotations.Nullable;

import java.util.List;

// TODO consulo: ModCommand API (PsiUpdateModCommandAction) missing, JB logic adapted to PsiElementBaseIntentionAction
@ExtensionImpl
@IntentionMetaData(ignoreId = "python.ReplaceListComprehensionWithForIntention", categories = "Python", fileExtensions = "py")
public final class ReplaceListComprehensionWithForIntention extends PsiElementBaseIntentionAction {
    public ReplaceListComprehensionWithForIntention() {
        setText(PyLocalize.intnReplaceListComprehensionsWithFor());
    }

    private static @Nullable PsiElement findElement(PsiElement leaf) {
        PsiElement current = leaf;
        while (current != null) {
            if (current instanceof PsiElement candidate) {
                return candidate;
            }
            if (current instanceof PsiFile) {
                return null;
            }
            current = current.getParent();
        }
        return null;
    }

    @Override
    public boolean isAvailable(Project project, Editor editor, PsiElement leaf) {
        PsiElement element = findElement(leaf);
        if (element == null) {
            return false;
        }
        LocalizeValue text = getPresentation(element);
        if (text == null) {
            return false;
        }
        setText(text);
        return true;
    }

    @Override
    public void invoke(Project project, Editor editor, PsiElement leaf) throws IncorrectOperationException {
        PsiElement element = findElement(leaf);
        if (element == null) {
            return;
        }
        invoke(element, editor);
    }



    private @Nullable LocalizeValue getPresentation(PsiElement element) {
        if (!(element.getContainingFile() instanceof PyFile)) {
            return null;
        }

        PyListCompExpression expression =
                PsiTreeUtil.getTopmostParentOfType(element, PyListCompExpression.class);
        if (expression == null) {
            return null;
        }
        if (expression.getComponents().isEmpty()) {
            return null;
        }
        PsiElement parent = expression.getParent();
        if (parent instanceof PyAssignmentStatement || parent instanceof PyPrintStatement) {
            return PyLocalize.intnReplaceListComprehensionsWithFor();
        }
        return null;
    }


    private void invoke(PsiElement element, Editor editor) {
        PyListCompExpression expression = PsiTreeUtil.getTopmostParentOfType(element, PyListCompExpression.class);
        if (expression == null) {
            return;
        }
        PsiElement parent = expression.getParent();
        PyElementGenerator elementGenerator = PyElementGenerator.getInstance(element.getProject());

        if (parent instanceof PyAssignmentStatement) {
            PsiElement leftExpr = ((PyAssignmentStatement)parent).getLeftHandSideExpression();
            if (leftExpr == null) {
                return;
            }
            PyAssignmentStatement initAssignment =
                    elementGenerator.createFromText(LanguageLevel.forElement(expression), PyAssignmentStatement.class,
                    leftExpr.getText() + " = []");
            PyForStatement forStatement = createForLoop(expression, elementGenerator,
                    leftExpr.getText() + ".append(" + expression.getResultExpression().getText() + ")");

            parent.getParent().addBefore(initAssignment, parent);
            parent.replace(forStatement);
        }
        else if (parent instanceof PyPrintStatement) {
            PyForStatement forStatement =
                    createForLoop(expression, elementGenerator, "print " + "(" + expression.getResultExpression().getText() + ")");
            parent.replace(forStatement);
        }
    }

    private static PyForStatement createForLoop(PyListCompExpression expression, PyElementGenerator elementGenerator,
            String result) {
        List<PyComprehensionComponent> components = expression.getComponents();
        StringBuilder stringBuilder = new StringBuilder();
        int slashNum = 1;
        for (PyComprehensionComponent component : components) {
            if (component instanceof PyComprehensionForComponent) {
                stringBuilder.append("for ");
                stringBuilder.append(((PyComprehensionForComponent)component).getIteratorVariable().getText());
                stringBuilder.append(" in ");
                stringBuilder.append(((PyComprehensionForComponent)component).getIteratedList().getText());
                stringBuilder.append(":\n");
            }
            if (component instanceof PyComprehensionIfComponent) {
                PyExpression test = ((PyComprehensionIfComponent)component).getTest();
                if (test != null) {
                    stringBuilder.append("if ");
                    stringBuilder.append(test.getText());
                    stringBuilder.append(":\n");
                }
            }
            stringBuilder.append("\t".repeat(slashNum));
            ++slashNum;
        }
        stringBuilder.append(result);
        return elementGenerator.createFromText(LanguageLevel.forElement(expression), PyForStatement.class,
                stringBuilder.toString());
    }
}

