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
package com.jetbrains.python.impl.codeInsight.intentions;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.refactoring.PyRefactoringUtil;
import com.jetbrains.python.psi.*;
import consulo.codeEditor.Editor;
import consulo.language.editor.intention.BaseIntentionAction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.usage.UsageInfo;
import jakarta.annotation.Nonnull;

import java.util.List;

/**
 * @author ktisha
 */
public class PyConvertStaticMethodToFunctionIntention extends BaseIntentionAction {
    @Nonnull
    @Override
    public LocalizeValue getText() {
        return PyLocalize.intnConvertStaticMethodToFunction();
    }

    public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
        if (!(file instanceof PyFile)) {
            return false;
        }
        PsiElement element = PyUtil.findNonWhitespaceAtOffset(file, editor.getCaretModel().getOffset());
        PyFunction function = PsiTreeUtil.getParentOfType(element, PyFunction.class);
        if (function == null) {
            return false;
        }
        PyClass containingClass = function.getContainingClass();
        if (containingClass == null) {
            return false;
        }
        PyDecoratorList decoratorList = function.getDecoratorList();
        if (decoratorList != null) {
            PyDecorator staticMethod = decoratorList.findDecorator(PyNames.STATICMETHOD);
            if (staticMethod != null) {
                return true;
            }
        }
        return false;
    }

    public void invoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        PsiElement element = PyUtil.findNonWhitespaceAtOffset(file, editor.getCaretModel().getOffset());
        PyFunction problemFunction = PsiTreeUtil.getParentOfType(element, PyFunction.class);
        if (problemFunction == null) {
            return;
        }
        PyClass containingClass = problemFunction.getContainingClass();
        if (containingClass == null) {
            return;
        }
        List<UsageInfo> usages = PyRefactoringUtil.findUsages(problemFunction, false);
        PyDecoratorList decoratorList = problemFunction.getDecoratorList();
        if (decoratorList != null) {
            PyDecorator decorator = decoratorList.findDecorator(PyNames.STATICMETHOD);
            if (decorator != null) {
                decorator.delete();
            }
        }
        PyElementGenerator generator = PyElementGenerator.getInstance(project);

        PsiElement copy = problemFunction.copy();
        PyStatementList classStatementList = containingClass.getStatementList();
        classStatementList.deleteChildRange(problemFunction, problemFunction);
        if (classStatementList.getStatements().length < 1) {
            classStatementList.add(generator.createPassStatement());
        }
        file.addAfter(copy, containingClass);

        for (UsageInfo usage : usages) {
            PsiElement usageElement = usage.getElement();
            if (usageElement instanceof PyReferenceExpression) {
                PyUtil.removeQualifier((PyReferenceExpression) usageElement);
            }
        }
    }
}
