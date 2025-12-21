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
package com.jetbrains.python.impl.refactoring.invertBoolean;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.language.editor.refactoring.BaseRefactoringProcessor;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.rename.RenameProcessor;
import consulo.language.editor.refactoring.rename.RenameUtil;
import consulo.language.psi.*;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.usage.MoveRenameUsageInfo;
import consulo.usage.UsageInfo;
import consulo.usage.UsageViewDescriptor;
import consulo.util.lang.ref.SimpleReference;
import jakarta.annotation.Nonnull;

import java.util.*;

/**
 * @author ktisha
 */
public class PyInvertBooleanProcessor extends BaseRefactoringProcessor {
    private PsiElement myElement;
    private String myNewName;
    private final RenameProcessor myRenameProcessor;
    private final Map<UsageInfo, SmartPsiElementPointer> myToInvert = new HashMap<>();
    private final SmartPointerManager mySmartPointerManager;

    @RequiredReadAction
    public PyInvertBooleanProcessor(@Nonnull PsiElement namedElement, @Nonnull String newName) {
        super(namedElement.getProject());
        myElement = namedElement;
        myNewName = newName;
        mySmartPointerManager = SmartPointerManager.getInstance(myProject);
        myRenameProcessor = new RenameProcessor(myProject, namedElement, newName, false, false);
    }

    @Override
    @Nonnull
    protected UsageViewDescriptor createUsageViewDescriptor(@Nonnull UsageInfo[] usages) {
        return new PyInvertBooleanUsageViewDescriptor(myElement);
    }

    @Override
    @RequiredUIAccess
    protected boolean preprocessUsages(@Nonnull SimpleReference<UsageInfo[]> refUsages) {
        if (!myNewName.equals(myElement instanceof PsiNamedElement namedElement ? namedElement.getName() : myElement.getText())) {
            if (myRenameProcessor.preprocessUsages(refUsages)) {
                prepareSuccessful();
                return true;
            }
            return false;
        }
        prepareSuccessful();
        return true;
    }

    @Nonnull
    @Override
    @RequiredReadAction
    protected UsageInfo[] findUsages() {
        List<SmartPsiElementPointer> toInvert = new ArrayList<>();

        addRefsToInvert(toInvert, myElement);

        UsageInfo[] renameUsages = myRenameProcessor.findUsages();

        Map<PsiElement, UsageInfo> expressionsToUsages = new HashMap<>();
        List<UsageInfo> result = new ArrayList<>();
        for (UsageInfo renameUsage : renameUsages) {
            expressionsToUsages.put(renameUsage.getElement(), renameUsage);
            result.add(renameUsage);
        }

        for (SmartPsiElementPointer pointer : toInvert) {
            PyExpression expression = (PyExpression) pointer.getElement();
            if (!expressionsToUsages.containsKey(expression) && expression != null) {
                UsageInfo usageInfo = new UsageInfo(expression);
                expressionsToUsages.put(expression, usageInfo);
                result.add(usageInfo);
                myToInvert.put(usageInfo, pointer);
            }
            else {
                myToInvert.put(expressionsToUsages.get(expression), pointer);
            }
        }

        return result.toArray(new UsageInfo[result.size()]);
    }

    @RequiredReadAction
    private void addRefsToInvert(@Nonnull List<SmartPsiElementPointer> toInvert, @Nonnull PsiElement psiElement) {
        Collection<PsiReference> refs = ReferencesSearch.search(psiElement).findAll();

        for (PsiReference ref : refs) {
            PsiElement element = ref.getElement();
            if (element instanceof PyTargetExpression target) {
                PyAssignmentStatement parent = PsiTreeUtil.getParentOfType(target, PyAssignmentStatement.class);
                if (parent != null && parent.getTargets().length == 1) {
                    PyExpression value = parent.getAssignedValue();
                    if (value != null) {
                        toInvert.add(mySmartPointerManager.createSmartPsiElementPointer(value));
                    }
                }
            }
            else if (element.getParent() instanceof PyPrefixExpression prefixExpression) {
                toInvert.add(mySmartPointerManager.createSmartPsiElementPointer(prefixExpression));
            }
            else if (element instanceof PyReferenceExpression refExpr) {
                toInvert.add(mySmartPointerManager.createSmartPsiElementPointer(refExpr));
            }
        }
        if (psiElement instanceof PyNamedParameter namedParam) {
            PyExpression defaultValue = namedParam.getDefaultValue();
            if (defaultValue != null) {
                toInvert.add(mySmartPointerManager.createSmartPsiElementPointer(defaultValue));
            }
        }
    }

    @Nonnull
    private static UsageInfo[] extractUsagesForElement(@Nonnull PsiElement element, @Nonnull UsageInfo[] usages) {
        List<UsageInfo> extractedUsages = new ArrayList<>(usages.length);
        for (UsageInfo usage : usages) {
            if (usage instanceof MoveRenameUsageInfo usageInfo && element.equals(usageInfo.getReferencedElement())) {
                extractedUsages.add(usageInfo);
            }
        }
        return extractedUsages.toArray(new UsageInfo[extractedUsages.size()]);
    }

    @Override
    @RequiredWriteAction
    protected void performRefactoring(@Nonnull UsageInfo[] usages) {
        for (PsiElement element : myRenameProcessor.getElements()) {
            try {
                RenameUtil.doRename(
                    element,
                    myRenameProcessor.getNewName(element),
                    extractUsagesForElement(element, usages),
                    myProject,
                    null
                );
            }
            catch (IncorrectOperationException e) {
                RenameUtil.showErrorMessage(e, element, myProject);
                return;
            }
        }
        for (UsageInfo usage : usages) {
            SmartPsiElementPointer pointerToInvert = myToInvert.get(usage);
            if (pointerToInvert != null) {
                PsiElement expression = pointerToInvert.getElement();
                if (expression != null && PsiTreeUtil.getParentOfType(expression, PyImportStatementBase.class, false) == null) {
                    PyExpression replacement = invertExpression(expression);
                    expression.replace(replacement);
                }
            }
        }
    }

    @Nonnull
    @RequiredReadAction
    private PyExpression invertExpression(@Nonnull PsiElement expression) {
        PyElementGenerator elementGenerator = PyElementGenerator.getInstance(myProject);
        if (expression instanceof PyBoolLiteralExpression boolLiteralExpression) {
            String value = boolLiteralExpression.getValue() ? PyNames.FALSE : PyNames.TRUE;
            return elementGenerator.createExpressionFromText(LanguageLevel.forElement(expression), value);
        }
        if (expression instanceof PyReferenceExpression
            && (PyNames.FALSE.equals(expression.getText()) || PyNames.TRUE.equals(expression.getText()))) {

            String value = PyNames.TRUE.equals(expression.getText()) ? PyNames.FALSE : PyNames.TRUE;
            return elementGenerator.createExpressionFromText(LanguageLevel.forElement(expression), value);
        }
        else if (expression instanceof PyPrefixExpression prefixExpression) {
            if (prefixExpression.getOperator() == PyTokenTypes.NOT_KEYWORD) {
                PyExpression operand = prefixExpression.getOperand();
                if (operand != null) {
                    return elementGenerator.createExpressionFromText(LanguageLevel.forElement(expression), operand.getText());
                }
            }
        }
        return elementGenerator.createExpressionFromText(LanguageLevel.forElement(expression), "not " + expression.getText());
    }

    @Nonnull
    @Override
    protected LocalizeValue getCommandName() {
        return RefactoringLocalize.invertBooleanTitle();
    }
}
