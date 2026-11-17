// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.impl.refactoring.inline;

import com.jetbrains.python.impl.pyi.PyiUtil;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyImportStatementBase;
import consulo.annotation.access.RequiredReadAction;
import consulo.codeEditor.Editor;
import consulo.language.editor.refactoring.inline.InlineOptionsDialog;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiNameIdentifierOwner;
import consulo.language.psi.PsiReference;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import org.jspecify.annotations.Nullable;

/**
 * @author Aleksei.Kniazev
 */
public class PyInlineFunctionDialog extends InlineOptionsDialog {
    private final Editor myEditor;
    private final PyFunction myFunction;
    private final @Nullable PsiReference myInvocationReference;
    private final boolean isMethod;
    private final @Nullable String myFunctionName;
    private final int myNumberOfOccurrences;

    @RequiredReadAction
    public PyInlineFunctionDialog(Project project, Editor editor, PyFunction function, @Nullable PsiReference invocationReference) {
        super(project, true, function);
        myEditor = editor;
        myFunction = function;
        myInvocationReference = invocationReference;
        isMethod = myFunction.asMethod() != null;
        myFunctionName = myFunction.getName();
        myNumberOfOccurrences = getNumberOfOccurrences(myFunction);

        myInvokedOnReference = myInvocationReference != null;
        setTitle(isMethod ? PyLocalize.refactoringInlineMethod(myFunctionName) : PyLocalize.refactoringInlineFunction(myFunctionName));
        init();
    }

    @Override
    protected void doAction() {
        PyFunction originalFunction = (PyFunction) PyiUtil.getOriginalElement(myFunction);
        invokeRefactoring(new PyInlineFunctionProcessor(
            myProject,
            myEditor,
            originalFunction != null ? originalFunction : myFunction,
            myInvocationReference,
            isInlineThisOnly(),
            !isKeepTheDeclaration()
        ));
    }

    private boolean isKeepTheDeclaration() {
        // TODO consulo: InlineOptionsDialog "keep the declaration" option (getKeepTheDeclarationText/isKeepTheDeclaration) missing
        return false;
    }

    @Override
    protected LocalizeValue getNameLabelText() {
        LocalizeValue text = isMethod
            ? PyLocalize.refactoringInlineLabelMethod(myFunctionName)
            : PyLocalize.refactoringInlineLabelFunction(myFunctionName);
        if (myNumberOfOccurrences != -1) {
            return PyLocalize.refactoringNameLabelText(text, myNumberOfOccurrences, myNumberOfOccurrences == 1 ? 0 : 1);
        }
        return text;
    }

    @Override
    protected LocalizeValue getBorderTitle() {
        return PyLocalize.refactoringInlineAllBorderTitle();
    }

    @Override
    protected LocalizeValue getInlineAllText() {
        return PyLocalize.refactoringInlineAllRemoveDeclaration();
    }

    // TODO consulo: InlineOptionsDialog#getKeepTheDeclarationText missing
    protected LocalizeValue getKeepTheDeclarationText() {
        return PyLocalize.refactoringInlineAllKeepDeclaration();
    }

    @Override
    protected LocalizeValue getInlineThisText() {
        return PyLocalize.refactoringInlineThisOnly();
    }

    @Override
    protected String getHelpId() {
        return PyInlineFunctionHandler.Helper.REFACTORING_ID;
    }

    // TODO consulo: InlineOptionsDialog#allowInlineAll missing
    protected boolean allowInlineAll() {
        return true;
    }

    @Override
    protected boolean isInlineThis() {
        return true;
    }

    // TODO consulo: InlineOptionsDialog#ignoreOccurrence missing, occurrences are counted by initOccurrencesNumber without this filter
    protected boolean ignoreOccurrence(PsiReference reference) {
        return PsiTreeUtil.getParentOfType(reference.getElement(), PyImportStatementBase.class) == null;
    }

    // TODO consulo: InlineOptionsDialog#getNumberOfOccurrences missing, initOccurrencesNumber is used instead
    @RequiredReadAction
    protected int getNumberOfOccurrences(PsiNameIdentifierOwner nameIdentifierOwner) {
        // TODO: this override will be redundant after PY-26881 is fixed and should be deleted then
        int originalNum = initOccurrencesNumber(nameIdentifierOwner);
        PsiElement stubOrImplementation =
            PyiUtil.isInsideStub(myFunction) ? PyiUtil.getOriginalElement(myFunction) : PyiUtil.getPythonStub(myFunction);
        if (originalNum != -1 && stubOrImplementation != null) {
            int fromOtherLocation = initOccurrencesNumber((PsiNameIdentifierOwner) stubOrImplementation);
            if (fromOtherLocation != -1) {
                return originalNum + fromOtherLocation;
            }
        }
        return originalNum;
    }
}
