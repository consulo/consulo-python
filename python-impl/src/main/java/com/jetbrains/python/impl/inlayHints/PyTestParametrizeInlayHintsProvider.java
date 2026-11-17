// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inlayHints;

import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.impl.psi.resolve.PyResolveUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.language.Language;
import consulo.language.editor.inlay.*;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.QualifiedName;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

// TODO: consider merging with `PythonInlayParameterHintsProvider`

/**
 * Provides inlay hints for tuple elements in @pytest.mark.parametrize decorators,
 * showing which parameter each value corresponds to.
 */
@ExtensionImpl
public class PyTestParametrizeInlayHintsProvider implements DeclarativeInlayHintsProvider {
    private static final String paramName = "pytest.param";
    private static final String parametrizePublicName = "pytest.mark.parametrize";
    @SuppressWarnings("unused")
    private static final String trueParametrizeName = "_pytest.mark.structures.MarkGenerator.parametrize";

    @Override
    public DeclarativeInlayHintsCollector createCollector(PsiFile file, Editor editor) {
        return new Collector();
    }

    @Override
    public Language getLanguage() {
        return PythonLanguage.INSTANCE;
    }

    @Override
    public String getId() {
        return "python.pytest.parametrize.inlays";
    }

    @Override
    public LocalizeValue getName() {
        return PyLocalize.inlayPytestParametrizeTuple();
    }

    @Override
    public LocalizeValue getDescription() {
        return LocalizeValue.empty();
    }

    @Override
    public LocalizeValue getPreviewFileText() {
        // resources/inlayProviders/python.pytest.parametrize.inlays/preview.py
        return LocalizeValue.of("""
            import pytest

            # Parametrize values with parameter names
            @pytest.mark.parametrize("input_num, expected", [
                (/*<#input_num#>*/2, /*<#expected#>*/4),
                (/*<#input_num#>*/3, /*<#expected#>*/9),
                pytest.param(/*<#input_num#>*/4, /*<#expected#>*/16),
            ])
            def test_square(input_num, expected):
                assert input_num ** 2 == expected
            """);
    }

    @Override
    public InlayGroup getGroup() {
        return InlayGroup.PARAMETERS_GROUP;
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    private static class Collector implements DeclarativeInlayHintsCollector.SharedBypassCollector {
        private final HintFormat hintFormat = HintFormat.DEFAULT
            .withFontSize(HintFontSize.ABitSmallerThanInEditor);

        @Override
        public void collectFromElement(PsiElement element, DeclarativeInlayTreeSink sink) {
            if (!(element instanceof PyDecorator decorator)) {
                return;
            }

            if (!(decorator.getCallee() instanceof PyReferenceExpression callee)) {
                return;
            }

            // NOTE: we can't resolve the true origin using code analysis because part of the chain has an inferred type
            List<QualifiedName> resolvedQNames = PyResolveUtil.resolveImportedElementQNameLocally(callee);
            if (resolvedQNames.stream().noneMatch(it -> it.toString().equals(parametrizePublicName))) {
                return;
            }

            List<String> parameterNames = getParameterNames(decorator);
            if (parameterNames == null) {
                return;
            }

            PyExpression[] arguments = decorator.getArguments();
            PyExpression secondArgument = arguments.length > 1 ? PyPsiUtils.flattenParens(arguments[1]) : null;
            if (!(secondArgument instanceof PySequenceExpression valueSets)) {
                return;
            }

            for (PyExpression rawValueSet : valueSets.getElements()) {
                PyExpression valueSet = PyPsiUtils.flattenParens(rawValueSet);
                PyExpression[] values;
                if (valueSet instanceof PyCallExpression callExpression &&
                    callExpression.getCallee() instanceof PyReferenceExpression valueSetCallee &&
                    PyResolveUtil.resolveImportedElementQNameLocally(valueSetCallee).stream().anyMatch(it -> it.toString().equals(paramName))) {
                    values = callExpression.getArguments();
                }
                else if (valueSet instanceof PyTupleExpression tupleExpression) {
                    values = tupleExpression.getElements();
                }
                else {
                    continue;
                }
                int count = Math.min(values.length, parameterNames.size());
                for (int i = 0; i < count; i++) {
                    PyExpression expr = values[i];
                    String name = parameterNames.get(i);
                    sink.addPresentation(
                        new DeclarativeInlayPosition.InlineInlayPosition(expr.getTextRange().getStartOffset(), false),
                        hintFormat,
                        builder -> builder.text(name)
                    );
                }
            }
        }

        private static @Nullable List<String> getParameterNames(PyDecorator decorator) {
            PyExpression[] arguments = decorator.getArguments();
            if (arguments.length == 0 || !(arguments[0] instanceof PyStringLiteralExpression stringLiteral)) {
                return null;
            }
            String stringValue = stringLiteral.getStringValue();

            List<String> result = new ArrayList<>();
            for (String it : stringValue.split(",", -1)) {
                String trimmed = it.trim();
                if (!trimmed.isBlank()) {
                    result.add(trimmed);
                }
            }
            return result;
        }
    }
}
