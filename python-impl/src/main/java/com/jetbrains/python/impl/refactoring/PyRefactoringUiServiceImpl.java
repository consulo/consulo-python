// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.refactoring;

import com.jetbrains.python.impl.refactoring.introduce.IntroduceOperation;
import com.jetbrains.python.impl.refactoring.introduce.IntroduceValidator;
import com.jetbrains.python.impl.refactoring.introduce.PyIntroduceHandlerUi;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.impl.codeInsight.codeFragment.PyCodeFragment;
import com.jetbrains.python.impl.refactoring.extractmethod.PyExtractMethodSettings;
import com.jetbrains.python.impl.refactoring.extractmethod.PyExtractMethodUtil;
import com.jetbrains.python.impl.refactoring.extractmethod.PyVariableData;
import consulo.annotation.component.ServiceImpl;
import consulo.codeEditor.Editor;
import consulo.language.editor.refactoring.extractMethod.AbstractExtractMethodDialog;
import consulo.language.editor.refactoring.extractMethod.AbstractVariableData;
import consulo.language.editor.refactoring.extractMethod.ExtractMethodDecorator;
import consulo.language.editor.refactoring.extractMethod.ExtractMethodValidator;
import consulo.language.editor.refactoring.introduce.IntroduceTargetChooser;
import consulo.language.editor.refactoring.introduce.inplace.OccurrencesChooser;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.virtualFileSystem.fileType.FileType;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

@ServiceImpl
@Singleton
public final class PyRefactoringUiServiceImpl extends PyRefactoringUiService {
    @Override
    public void showIntroduceTargetChooser(
        IntroduceOperation operation,
        Editor editor,
        List<PyExpression> expressions,
        Consumer<? super IntroduceOperation> callback
    ) {
        IntroduceTargetChooser.showChooser(editor, expressions, pyExpression -> {
            operation.setElement(pyExpression);
            callback.accept(operation);
        }, pyExpression -> pyExpression.getText());
    }

    @Override
    public void showOccurrencesChooser(IntroduceOperation operation, Editor editor, Consumer<? super IntroduceOperation> callback) {
        OccurrencesChooser.simpleChooser(editor).showChooser(operation.getElement(), operation.getOccurrences(), replaceChoice -> {
            operation.setReplaceAll(replaceChoice == OccurrencesChooser.ReplaceChoice.ALL);
            callback.accept(operation);
        });
    }

    @Override
    public void performIntroduceWithDialog(
        IntroduceOperation operation,
        LocalizeValue dialogTitle,
        IntroduceValidator validator,
        String id,
        Consumer<? super IntroduceOperation> performRefactoringCallback
    ) {
        PyIntroduceHandlerUi.performIntroduceWithDialog(operation, dialogTitle, validator, id, performRefactoringCallback);
    }

    @Override
    public void performInplaceIntroduceVariable(IntroduceOperation operation, PsiElement statement) {
        PyIntroduceHandlerUi.performInplaceIntroduceVariable(operation, statement);
    }

    @Override
    @RequiredUIAccess
    public @Nullable PyExtractMethodSettings showExtractMethodDialog(
        Project project,
        String defaultName,
        PyCodeFragment fragment,
        Object[] visibilityVariants,
        ExtractMethodValidator validator,
        ExtractMethodDecorator decorator,
        FileType type,
        String helpId
    ) {
        // TODO consulo: JB shows PyExtractMethodDialog (python-refactoring, with the "add type annotations" checkbox) here;
        // it is not ported yet, so the platform AbstractExtractMethodDialog used by Consulo before is kept
        AbstractExtractMethodDialog dialog = new AbstractExtractMethodDialog(project, defaultName, fragment, validator, decorator, type) {
            @Override
            protected String getHelpId() {
                return helpId;
            }
        };
        dialog.show();

        //return if don`t want to extract method
        if (!dialog.isOK()) {
            return null;
        }

        AbstractVariableData[] abstractVariableData = dialog.getAbstractVariableData();
        PyVariableData[] variableData = new PyVariableData[abstractVariableData.length];
        for (int i = 0; i < abstractVariableData.length; i++) {
            AbstractVariableData data = abstractVariableData[i];
            PyVariableData d = new PyVariableData();
            d.name = data.name;
            d.originalName = data.originalName;
            d.passAsParameter = data.passAsParameter;
            d.typeName = fragment.getInputTypeName(data.originalName);
            d.type = fragment.getInputType(data.originalName);
            variableData[i] = d;
        }
        return new PyExtractMethodSettings(
            dialog.getMethodName(),
            variableData,
            fragment.getOutputType(),
            fragment.getOutputTypes(),
            PyExtractMethodUtil.getAddTypeAnnotations(project)
        );
    }

    // TODO consulo: JB overrides showPyInlineFunctionDialog (PyInlineFunctionDialog from python-refactoring) here; not ported yet
}
