// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.refactoring.suggested;

import com.jetbrains.python.impl.psi.impl.ParamHelper;
import com.jetbrains.python.impl.refactoring.changeSignature.PyExpressionCodeFragment;
import com.jetbrains.python.psi.PyExpressionStatement;
import consulo.language.editor.refactoring.suggested.SignaturePresentationBuilder;
import consulo.language.editor.refactoring.suggested.SuggestedChangeSignatureData;
import consulo.language.editor.refactoring.suggested.SuggestedRefactoringExecution;
import consulo.language.editor.refactoring.suggested.SuggestedRefactoringSupport;
import consulo.language.editor.refactoring.suggested.SuggestedRefactoringSupport.Parameter;
import consulo.language.editor.refactoring.suggested.SuggestedRefactoringUI;
import consulo.language.psi.PsiCodeFragment;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.ui.ex.awt.ValidationInfo;
import org.jspecify.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

import static com.jetbrains.python.impl.refactoring.suggested.PySuggestedRefactoringSupport.Helper.defaultValue;

final class PySuggestedRefactoringUI extends SuggestedRefactoringUI {
    static final PySuggestedRefactoringUI INSTANCE = new PySuggestedRefactoringUI();

    private PySuggestedRefactoringUI() {
    }

    @Override
    public SignaturePresentationBuilder createSignaturePresentationBuilder(
        SuggestedRefactoringSupport.Signature signature,
        SuggestedRefactoringSupport.Signature otherSignature,
        boolean isOldSignature
    ) {
        return new PySignaturePresentationBuilder(signature, otherSignature, isOldSignature);
    }

    @Override
    public List<NewParameterData> extractNewParameterData(SuggestedChangeSignatureData data) {
        Project project = data.getDeclaration().getProject();

        List<NewParameterData> result = new ArrayList<>();
        for (Parameter parameter : data.getNewSignature().getParameters()) {
            Parameter oldParameter = data.getOldSignature().parameterById(parameter.getId());
            boolean accepted;
            if (oldParameter == null) {
                accepted = ParamHelper.couldHaveDefaultValue(parameter.getName());
            }
            else {
                accepted = defaultValue(oldParameter) != null && defaultValue(parameter) == null;
            }
            if (!accepted) {
                continue;
            }

            PyExpressionCodeFragment valueFragment = new PyExpressionCodeFragment(project, parameter.getName(), "");
            boolean shouldHaveDefaultValue = shouldHaveDefaultValue(parameter, oldParameter);

            result.add(new NewParameterData(
                parameter.getName(),
                valueFragment,
                false,
                placeholderText(shouldHaveDefaultValue),
                new ParameterData(shouldHaveDefaultValue),
                false
            ));
        }
        return result;
    }

    @Override
    public @Nullable ValidationInfo validateValue(NewParameterData data, @Nullable JComponent component) {
        if (!data.getValueFragment().getText().isBlank()) {
            return null;
        }

        Boolean shouldHaveDefaultValue =
            data.getAdditionalData() instanceof ParameterData parameterData ? parameterData.shouldHaveDefaultValue() : null;
        if (shouldHaveDefaultValue == null || !shouldHaveDefaultValue) {
            return null;
        }
        return new ValidationInfo(PyLocalize.refactoringChangeSignatureDialogValidationDefaultMissing(), component);
    }

    @Override
    public SuggestedRefactoringExecution.NewParameterValue.@Nullable Expression extractValue(PsiCodeFragment fragment) {
        if (!(fragment.getFirstChild() instanceof PyExpressionStatement expressionStatement)) {
            return null;
        }
        return new SuggestedRefactoringExecution.NewParameterValue.Expression(expressionStatement.getExpression());
    }

    private static LocalizeValue placeholderText(boolean shouldHaveDefaultValue) {
        return shouldHaveDefaultValue
            ? PyLocalize.refactoringChangeSignatureSuggestedCallsiteValue()
            : PyLocalize.refactoringChangeSignatureSuggestedCallsiteValueOptional();
    }

    private static boolean shouldHaveDefaultValue(Parameter newParameter, @Nullable Parameter oldParameter) {
        return ParamHelper.couldHaveDefaultValue(newParameter.getName()) &&
            defaultValue(newParameter) == null &&
            (oldParameter == null || defaultValue(oldParameter) == null);
    }

    private static final class PySignaturePresentationBuilder extends SignaturePresentationBuilder {
        PySignaturePresentationBuilder(
            SuggestedRefactoringSupport.Signature signature,
            SuggestedRefactoringSupport.Signature otherSignature,
            boolean isOldSignature
        ) {
            super(signature, otherSignature, isOldSignature);
        }

        @Override
        public void buildPresentation() {
            fragments.add(leaf(signature.getName(), otherSignature.getName()));

            buildParameterList((fragments, parameter, correspondingParameter) -> {
                fragments.add(leaf(parameter.getName(), correspondingParameter != null ? correspondingParameter.getName() : parameter.getName()));

                String defaultValuePart = defaultValuePartInSignature(parameter);
                if (defaultValuePart != null) {
                    fragments.add(leaf(
                        defaultValuePart,
                        correspondingParameter != null ? defaultValuePartInSignature(correspondingParameter) : null
                    ));
                }
            });
        }

        private static @Nullable String defaultValuePartInSignature(Parameter parameter) {
            return ParamHelper.getDefaultValuePartInSignature(defaultValue(parameter), false);
        }
    }

    private record ParameterData(boolean shouldHaveDefaultValue) implements NewParameterAdditionalData {
    }
}
