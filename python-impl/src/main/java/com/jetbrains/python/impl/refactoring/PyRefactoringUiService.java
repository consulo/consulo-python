// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.refactoring;

import com.jetbrains.python.impl.codeInsight.codeFragment.PyCodeFragment;
import com.jetbrains.python.impl.refactoring.extractmethod.PyExtractMethodSettings;
import com.jetbrains.python.impl.refactoring.extractmethod.PyExtractMethodUtil;
import com.jetbrains.python.impl.refactoring.extractmethod.PyVariableData;
import com.jetbrains.python.impl.refactoring.introduce.IntroduceOperation;
import com.jetbrains.python.impl.refactoring.introduce.IntroduceValidator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFunction;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.application.Application;
import consulo.codeEditor.Editor;
import consulo.language.editor.refactoring.extractMethod.ExtractMethodDecorator;
import consulo.language.editor.refactoring.extractMethod.ExtractMethodValidator;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.virtualFileSystem.fileType.FileType;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

@ServiceAPI(ComponentScope.APPLICATION)
public class PyRefactoringUiService {
    public void performIntroduceWithDialog(
        IntroduceOperation operation,
        LocalizeValue dialogTitle,
        IntroduceValidator validator,
        String id,
        Consumer<? super IntroduceOperation> performRefactoringCallback
    ) {

    }

    public void performInplaceIntroduceVariable(IntroduceOperation operation, PsiElement statement) {

    }

    public void showIntroduceTargetChooser(
        IntroduceOperation operation,
        Editor editor,
        List<PyExpression> expressions,
        Consumer<? super IntroduceOperation> callback
    ) {
    }

    public void showOccurrencesChooser(IntroduceOperation operation, Editor editor, Consumer<? super IntroduceOperation> callback) {
        operation.setReplaceAll(true);
        callback.accept(operation);
    }

    public @Nullable PyExtractMethodSettings showExtractMethodDialog(
        Project project,
        String defaultName,
        PyCodeFragment fragment,
        Object[] visibilityVariants,
        ExtractMethodValidator validator,
        // TODO consulo: generic ExtractMethodDecorator<Object> missing, Consulo's ExtractMethodDecorator is not generic
        ExtractMethodDecorator decorator,
        FileType type,
        String helpId
    ) {
        return new PyExtractMethodSettings(
            defaultName,
            new PyVariableData[0],
            fragment.getOutputType(),
            fragment.getOutputTypes(),
            PyExtractMethodUtil.getAddTypeAnnotations(project)
        );
    }

    public void showPyInlineFunctionDialog(
        Project project,
        Editor editor,
        PyFunction function,
        @Nullable PsiReference reference
    ) {
    }

    public static PyRefactoringUiService getInstance() {
        return Application.get().getInstance(PyRefactoringUiService.class);
    }
}
