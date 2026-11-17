// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.completion;

import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.impl.psi.types.PyExpectedTypeJudgement;
import com.jetbrains.python.impl.psi.types.PyLiteralType;
import com.jetbrains.python.impl.psi.types.PyTypeUtil;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.editor.completion.CompletionContributor;
import consulo.language.editor.completion.CompletionParameters;
import consulo.language.editor.completion.CompletionProvider;
import consulo.language.editor.completion.CompletionResultSet;
import consulo.language.editor.completion.CompletionType;
import consulo.language.editor.completion.lookup.LookupElementBuilder;
import consulo.language.editor.completion.lookup.PrioritizedLookupElement;
import consulo.language.util.ProcessingContext;
import consulo.platform.base.icon.PlatformIconGroup;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static consulo.language.pattern.PlatformPatterns.psiElement;

/**
 * Provides literal type variants in the following cases:
 * <pre>{@code
 * x: Literal["foo", "bar"]
 * x = <caret>
 * x = fo<caret>
 * x = "fo<caret>"
 * }</pre>
 * <p>
 * or
 *
 * <pre>{@code
 * def f(x: Literal["foo", "bar"]): ...
 * f(<caret>)
 * f(fo<caret>)
 * f("fo<caret>")
 * }</pre>
 */
@ExtensionImpl
public class PyLiteralTypeCompletionContributor extends CompletionContributor {
    public PyLiteralTypeCompletionContributor() {
        extend(CompletionType.BASIC, psiElement(), new PyLiteralTypeCompletionProvider());
    }

    @Override
    public Language getLanguage() {
        return PythonLanguage.INSTANCE;
    }

    private static final class PyLiteralTypeCompletionProvider implements CompletionProvider {
        @Override
        @RequiredReadAction
        public void addCompletions(CompletionParameters parameters, ProcessingContext context, CompletionResultSet result) {
            if (!(parameters.getPosition().getParent() instanceof PyExpression position)) {
                return;
            }
            if (!(position instanceof PyStringLiteralExpression
                || position instanceof PyReferenceExpression referenceExpression && !referenceExpression.isQualified())) {
                return;
            }

            TypeEvalContext typeEvalContext = TypeEvalContext.codeCompletion(position.getProject(), position.getContainingFile());
            PyType expectedType = PyExpectedTypeJudgement.getExpectedType(position, typeEvalContext);
            List<PyType> possibleTypes = new ArrayList<>();
            if (expectedType != null) {
                possibleTypes.add(expectedType);
            }
            addToResult(position, possibleTypes, result);
        }

        private static void addToResult(PyExpression position, List<PyType> possibleTypes, CompletionResultSet result) {
            boolean useStringValue = position instanceof PyStringLiteralExpression;

            possibleTypes.stream()
                .flatMap(it -> PyTypeUtil.toStream(it))
                .filter(PyLiteralType.class::isInstance)
                .map(PyLiteralType.class::cast)
                .filter(it -> it.getStringValue() != null) // Take only string literals
                .map(it -> useStringValue ? it.getStringValue() : it.getExpressionText())
                .filter(Objects::nonNull)
                .forEach(lookupString ->
                    // TODO consulo: MLRankingIgnorable missing, MLRankingIgnorable.wrap(...) dropped
                    result.addElement(
                        PrioritizedLookupElement.withPriority(
                            LookupElementBuilder
                                .create(lookupString)
                                .withIcon(PlatformIconGroup.nodesParameter()),
                            (double) PythonCompletionWeigher.PRIORITY_WEIGHT
                        )
                    )
                );
        }
    }
}
