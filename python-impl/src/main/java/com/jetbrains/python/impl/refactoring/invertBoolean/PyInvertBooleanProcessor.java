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
import consulo.language.editor.refactoring.BaseRefactoringProcessor;
import consulo.language.editor.refactoring.rename.RenameProcessor;
import consulo.language.editor.refactoring.rename.RenameUtil;
import consulo.language.psi.*;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.usage.MoveRenameUsageInfo;
import consulo.usage.UsageInfo;
import consulo.usage.UsageViewDescriptor;
import consulo.util.lang.ref.Ref;

import jakarta.annotation.Nonnull;

import java.util.*;

/**
 * User : ktisha
 */
public class PyInvertBooleanProcessor extends BaseRefactoringProcessor {
    private PsiElement myElement;
    private String myNewName;
    private final RenameProcessor myRenameProcessor;
    private final Map<UsageInfo, SmartPsiElementPointer> myToInvert = new HashMap<UsageInfo, SmartPsiElementPointer>();
    private final SmartPointerManager mySmartPointerManager;

    public PyInvertBooleanProcessor(@Nonnull final PsiElement namedElement, @Nonnull final String newName) {
        super(namedElement.getProject());
        myElement = namedElement;
        myNewName = newName;
        mySmartPointerManager = SmartPointerManager.getInstance(myProject);
        myRenameProcessor = new RenameProcessor(myProject, namedElement, newName, false, false);
    }

    @Override
    @Nonnull
    protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usages) {
        return new PyInvertBooleanUsageViewDescriptor(myElement);
    }

    @Override
    protected boolean preprocessUsages(Ref<UsageInfo[]> refUsages) {
        if (!myNewName.equals(myElement instanceof PsiNamedElement ? ((PsiNamedElement)myElement).getName() : myElement.getText())) {
            if (myRenameProcessor.preprocessUsages(refUsages)) {
                prepareSuccessful();
                return true;
            }
            return false;
        }
        prepareSuccessful();
        return true;
    }

    @Override
    @Nonnull
    protected UsageInfo[] findUsages() {
        final List<SmartPsiElementPointer> toInvert = new ArrayList<SmartPsiElementPointer>();

        addRefsToInvert(toInvert, myElement);

        final UsageInfo[] renameUsages = myRenameProcessor.findUsages();

        final Map<PsiElement, UsageInfo> expressionsToUsages = new HashMap<PsiElement, UsageInfo>();
        final List<UsageInfo> result = new ArrayList<UsageInfo>();
        for (UsageInfo renameUsage : renameUsages) {
            expressionsToUsages.put(renameUsage.getElement(), renameUsage);
            result.add(renameUsage);
        }

        for (SmartPsiElementPointer pointer : toInvert) {
            final PyExpression expression = (PyExpression)pointer.getElement();
            if (!expressionsToUsages.containsKey(expression) && expression != null) {
                final UsageInfo usageInfo = new UsageInfo(expression);
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

    private void addRefsToInvert(@Nonnull final List<SmartPsiElementPointer> toInvert, @Nonnull final PsiElement psiElement) {
        final Collection<PsiReference> refs = ReferencesSearch.search(psiElement).findAll();

        for (PsiReference ref : refs) {
            final PsiElement element = ref.getElement();
            if (element instanceof PyTargetExpression) {
                final PyTargetExpression target = (PyTargetExpression)element;
                final PyAssignmentStatement parent = PsiTreeUtil.getParentOfType(target, PyAssignmentStatement.class);
                if (parent != null && parent.getTargets().length == 1) {
                    final PyExpression value = parent.getAssignedValue();
                    if (value != null) {
                        toInvert.add(mySmartPointerManager.createSmartPsiElementPointer(value));
                    }
                }
            }
            else if (element.getParent() instanceof PyPrefixExpression) {
                toInvert.add(mySmartPointerManager.createSmartPsiElementPointer(element.getParent()));
            }
            else if (element instanceof PyReferenceExpression) {
                final PyReferenceExpression refExpr = (PyReferenceExpression)element;
                toInvert.add(mySmartPointerManager.createSmartPsiElementPointer(refExpr));
            }
        }
        if (psiElement instanceof PyNamedParameter) {
            final PyExpression defaultValue = ((PyNamedParameter)psiElement).getDefaultValue();
            if (defaultValue != null) {
                toInvert.add(mySmartPointerManager.createSmartPsiElementPointer(defaultValue));
            }
        }
    }

    @Nonnull
    private static UsageInfo[] extractUsagesForElement(@Nonnull final PsiElement element, @Nonnull final UsageInfo[] usages) {
        final ArrayList<UsageInfo> extractedUsages = new ArrayList<UsageInfo>(usages.length);
        for (UsageInfo usage : usages) {
            if (usage instanceof MoveRenameUsageInfo) {
                MoveRenameUsageInfo usageInfo = (MoveRenameUsageInfo)usage;
                if (element.equals(usageInfo.getReferencedElement())) {
                    extractedUsages.add(usageInfo);
                }
            }
        }
        return extractedUsages.toArray(new UsageInfo[extractedUsages.size()]);
    }


    @Override
    protected void performRefactoring(UsageInfo[] usages) {
        for (final PsiElement element : myRenameProcessor.getElements()) {
            try {
                RenameUtil.doRename(
                    element,
                    myRenameProcessor.getNewName(element),
                    extractUsagesForElement(element, usages),
                    myProject,
                    null
                );
            }
            catch (final IncorrectOperationException e) {
                RenameUtil.showErrorMessage(e, element, myProject);
                return;
            }
        }
        for (UsageInfo usage : usages) {
            final SmartPsiElementPointer pointerToInvert = myToInvert.get(usage);
            if (pointerToInvert != null) {
                PsiElement expression = pointerToInvert.getElement();
                if (expression != null && PsiTreeUtil.getParentOfType(expression, PyImportStatementBase.class, false) == null) {
                    final PyExpression replacement = invertExpression(expression);
                    expression.replace(replacement);
                }
            }
        }
    }

    @Nonnull
    private PyExpression invertExpression(@Nonnull final PsiElement expression) {
        final PyElementGenerator elementGenerator = PyElementGenerator.getInstance(myProject);
        if (expression instanceof PyBoolLiteralExpression) {
            final String value = ((PyBoolLiteralExpression)expression).getValue() ? PyNames.FALSE : PyNames.TRUE;
            return elementGenerator.createExpressionFromText(LanguageLevel.forElement(expression), value);
        }
        if (expression instanceof PyReferenceExpression && (PyNames.FALSE.equals(expression.getText()) ||
            PyNames.TRUE.equals(expression.getText()))) {

            final String value = PyNames.TRUE.equals(expression.getText()) ? PyNames.FALSE : PyNames.TRUE;
            return elementGenerator.createExpressionFromText(LanguageLevel.forElement(expression), value);
        }
        else if (expression instanceof PyPrefixExpression) {
            if (((PyPrefixExpression)expression).getOperator() == PyTokenTypes.NOT_KEYWORD) {
                final PyExpression operand = ((PyPrefixExpression)expression).getOperand();
                if (operand != null) {
                    return elementGenerator.createExpressionFromText(LanguageLevel.forElement(expression), operand.getText());
                }
            }
        }
        return elementGenerator.createExpressionFromText(LanguageLevel.forElement(expression), "not " + expression.getText());
    }

    @Override
    protected String getCommandName() {
        return PyInvertBooleanHandler.REFACTORING_NAME;
    }
}
