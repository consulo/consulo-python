// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.quickfix;

import consulo.language.editor.inspection.FileModifier;
import com.jetbrains.python.impl.refactoring.PyRefactoringUtil;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyExpressionStatement;
import consulo.annotation.access.RequiredWriteAction;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.SmartPointerManager;
import consulo.language.psi.SmartPsiElementPointer;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * User: catherine
 * <p>
 * QuickFix to remove redundant argument equal default
 */
public class RemoveArgumentEqualDefaultQuickFix implements LocalQuickFix {
    private final List<SmartPsiElementPointer<PyExpression>> myProblemElements;

    public RemoveArgumentEqualDefaultQuickFix(Collection<PyExpression> problemElements) {
        myProblemElements = new ArrayList<>();
        for (PyExpression element : problemElements) {
            myProblemElements.add(SmartPointerManager.createPointer(element));
        }
    }

    @Override
    public LocalizeValue getName() {
        return PyLocalize.qfixRemoveArgumentEqualDefault();
    }

    @Override
    @RequiredWriteAction
    public void applyFix(Project project, ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();
        List<PyExpression> problemElements = ContainerUtil.map(myProblemElements, SmartPsiElementPointer::getElement);
        if (problemElements.contains(null)) {
            return;
        }

        PyArgumentList argumentList = PsiTreeUtil.getParentOfType(element, PyArgumentList.class);
        if (argumentList == null) {
            return;
        }
        StringBuilder newArgumentList = new StringBuilder("foo(");

        PyExpression[] arguments = argumentList.getArguments();
        List<String> newArgs = new ArrayList<>();
        for (int i = 0; i != arguments.length; ++i) {
            if (!problemElements.contains(arguments[i])) {
                newArgs.add(arguments[i].getText());
            }
        }

        newArgumentList.append(StringUtil.join(newArgs, ", ")).append(")");
        PyExpression expression = PyElementGenerator.getInstance(project).createFromText(
            LanguageLevel.forElement(argumentList), PyExpressionStatement.class, newArgumentList.toString()).getExpression();
        if (expression instanceof PyCallExpression) {
            argumentList.replace(((PyCallExpression)expression).getArgumentList());
        }
    }

    @Override
    public @Nullable FileModifier getFileModifierForPreview(PsiFile target) {
        List<PyExpression> copies = new ArrayList<>();
        for (SmartPsiElementPointer<PyExpression> element : myProblemElements) {
            PyExpression copy = PyRefactoringUtil.findSameElementForPreview(element, target);
            if (copy == null) {
                return null;
            }
            copies.add(copy);
        }
        return new RemoveArgumentEqualDefaultQuickFix(copies);
    }
}
