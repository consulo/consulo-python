// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.completion;

import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.impl.psi.types.PyTypedDictType;
import com.jetbrains.python.psi.PyAnnotation;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyCallSiteExpression;
import com.jetbrains.python.psi.PyDictLiteralExpression;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyKeyValueExpression;
import com.jetbrains.python.psi.PyKeywordArgument;
import com.jetbrains.python.psi.PyParenthesizedExpression;
import com.jetbrains.python.psi.PyReturnStatement;
import com.jetbrains.python.psi.PySequenceExpression;
import com.jetbrains.python.psi.PySetLiteralExpression;
import com.jetbrains.python.psi.PyStringElement;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PyTupleExpression;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.PyCallableParameter;
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
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.ProcessingContext;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.util.lang.Pair;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static com.jetbrains.python.impl.psi.impl.PyCallExpressionHelper.mapArguments;
import static consulo.language.pattern.PlatformPatterns.psiElement;

/**
 * Provides completion variants for keys of dict literals marked as TypedDict
 *
 * @see PyTypedDictType
 */
@ExtensionImpl
public class PyDictLiteralCompletionContributor extends CompletionContributor {
    public PyDictLiteralCompletionContributor() {
        extend(CompletionType.BASIC, psiElement().inside(PySequenceExpression.class), new DictLiteralCompletionProvider());
    }

    @Override
    public Language getLanguage() {
        return PythonLanguage.INSTANCE;
    }

    private static final class DictLiteralCompletionProvider implements CompletionProvider {

        private static final String DEFAULT_QUOTE = "\"";

        @Override
        @RequiredReadAction
        public void addCompletions(CompletionParameters parameters, ProcessingContext context, CompletionResultSet result) {
            PsiElement originalPosition = parameters.getOriginalPosition();
            PsiElement originalElement = originalPosition != null ? originalPosition.getParent() : null;
            if (originalElement == null) {
                return;
            }

            PsiElement possibleSequenceExpr =
                originalElement instanceof PyStringLiteralExpression ? originalElement.getParent() : originalElement;
            if (possibleSequenceExpr instanceof PyKeyValueExpression) {
                possibleSequenceExpr = possibleSequenceExpr.getParent();
            }
            // it's set literal when user is typing the first key
            if (possibleSequenceExpr instanceof PyDictLiteralExpression || possibleSequenceExpr instanceof PySetLiteralExpression) {
                PySequenceExpression sequenceExpression = (PySequenceExpression) possibleSequenceExpr;
                addCompletionToCallExpression(originalElement, sequenceExpression, result);
                addCompletionToAssignment(originalElement, sequenceExpression, result);
                addCompletionToReturnStatement(originalElement, sequenceExpression, result);
            }
        }

        private static void addCompletionToCallExpression(PsiElement originalElement,
                                                          PySequenceExpression possibleSequenceExpr,
                                                          CompletionResultSet result) {
            TypeEvalContext typeEvalContext = TypeEvalContext.codeCompletion(originalElement.getProject(), originalElement.getContainingFile());
            String quote = getForcedQuote(possibleSequenceExpr, originalElement);
            List<PyCallableParameter> mappedParameters =
                getMappedParameters(possibleSequenceExpr, PyResolveContext.defaultContext(typeEvalContext));
            if (mappedParameters != null) {
                for (PyCallableParameter it : mappedParameters) {
                    addCompletionForTypedDictKeys(it.getType(typeEvalContext), possibleSequenceExpr, result, quote, typeEvalContext);
                }
            }
        }

        private static String getForcedQuote(PySequenceExpression possibleSequenceExpr, PsiElement originalElement) {
            if (!(originalElement instanceof PyStringLiteralExpression)) {
                Set<String> usedQuotes = new LinkedHashSet<>();
                for (PyExpression it : possibleSequenceExpr.getElements()) {
                    PyExpression keyOrElement = it instanceof PyKeyValueExpression keyValue ? keyValue.getKey() : it;
                    if (keyOrElement instanceof PyStringLiteralExpression stringLiteral) {
                        for (PyStringElement stringElement : stringLiteral.getStringElements()) {
                            usedQuotes.add(stringElement.getQuote());
                        }
                    }
                }
                return usedQuotes.size() == 1 ? usedQuotes.iterator().next() : DEFAULT_QUOTE;
            }
            return "";
        }

        /**
         * `argument` can be (parenthesized) expression or a value of a {@link PyKeywordArgument}
         */
        private static @Nullable List<PyCallableParameter> getMappedParameters(PyExpression argument, PyResolveContext resolveContext) {
            PyExpression currentArgument = argument;
            while (true) {
                PsiElement newArgument = currentArgument.getParent();
                if (!(newArgument instanceof PyParenthesizedExpression parenthesizedExpression)) {
                    break;
                }
                currentArgument = parenthesizedExpression;
            }

            if (currentArgument.getParent() instanceof PyKeywordArgument keywordArgument) {
                assert keywordArgument.getValueExpression() == currentArgument;
                currentArgument = keywordArgument;
            }

            PsiElement parent = currentArgument.getParent();
            if (parent instanceof PyArgumentList) {
                parent = parent.getParent();
            }
            if (!(parent instanceof PyCallSiteExpression callSiteExpression)) {
                return null;
            }

            PyExpression finalArgument = currentArgument;
            List<PyCallableParameter> result = new ArrayList<>();
            for (PyCallExpression.PyArgumentsMapping it : mapArguments(callSiteExpression, resolveContext)) {
                PyCallableParameter parameter = it.getMappedParameters().get(finalArgument);
                if (parameter != null) {
                    result.add(parameter);
                }
            }
            return result;
        }

        private static void addCompletionToAssignment(PsiElement originalElement,
                                                      PySequenceExpression possibleSequenceExpr,
                                                      CompletionResultSet result) {
            PyAssignmentStatement assignment = PsiTreeUtil.getParentOfType(originalElement, PyAssignmentStatement.class);
            if (assignment != null) {
                TypeEvalContext typeEvalContext = TypeEvalContext.codeCompletion(originalElement.getProject(), originalElement.getContainingFile());
                Pair<PyExpression, PyExpression> targetToValue;
                if (assignment.getTargets().length == 1) {
                    targetToValue = Pair.create(assignment.getTargets()[0], assignment.getAssignedValue());
                }
                else {
                    targetToValue = null;
                    for (Pair<PyExpression, PyExpression> it : assignment.getTargetsToValuesMapping()) {
                        if (Objects.equals(it.getSecond(), possibleSequenceExpr)) {
                            targetToValue = Pair.create(it.getFirst(), it.getSecond());
                            break;
                        }
                    }
                }

                PyExpression target = targetToValue != null ? targetToValue.getFirst() : null;
                PyExpression value = targetToValue != null ? targetToValue.getSecond() : null;
                if (target != null && value != null) {
                    PyType expectedType = typeEvalContext.getType(target);
                    addCompletionForTypedDictKeys(expectedType, value, result, getForcedQuote(possibleSequenceExpr, originalElement),
                        typeEvalContext);
                }
                else { //multiple target expressions and there is a PsiErrorElement
                    PyExpression targetExpr = assignment.getAssignedValue();
                    if (!(targetExpr instanceof PyTupleExpression tupleExpression)) {
                        return;
                    }
                    PyExpression element = null;
                    for (PyExpression it : tupleExpression.getElements()) {
                        if (Objects.equals(it, possibleSequenceExpr)) {
                            element = it;
                            break;
                        }
                    }
                    if (element != null) {
                        int index = Arrays.asList(tupleExpression.getElements()).indexOf(element);
                        if (index < assignment.getTargets().length) {
                            PyType expectedType = typeEvalContext.getType(assignment.getTargets()[index]);
                            addCompletionForTypedDictKeys(expectedType, element, result, getForcedQuote(possibleSequenceExpr, originalElement),
                                typeEvalContext);
                        }
                    }
                }
            }
        }

        private static void addCompletionToReturnStatement(PsiElement originalElement,
                                                           PySequenceExpression possibleSequenceExpr,
                                                           CompletionResultSet result) {
            PyReturnStatement returnStatement = PsiTreeUtil.getParentOfType(originalElement, PyReturnStatement.class);
            if (returnStatement != null) {
                TypeEvalContext typeEvalContext = TypeEvalContext.codeCompletion(originalElement.getProject(), originalElement.getContainingFile());
                ScopeOwner owner = ScopeUtil.getScopeOwner(returnStatement);
                if (owner instanceof PyFunction function) {
                    PyAnnotation annotation = function.getAnnotation();
                    String typeCommentAnnotation = function.getTypeCommentAnnotation();
                    if (annotation != null || typeCommentAnnotation != null) { // to ensure that we have return type specified, not inferred
                        PyType expectedType = typeEvalContext.getReturnType(function);
                        addCompletionForTypedDictKeys(expectedType, possibleSequenceExpr, result,
                            getForcedQuote(possibleSequenceExpr, originalElement), typeEvalContext);
                    }
                }
            }
        }

        private static void addCompletionForTypedDictKeys(@Nullable PyType expectedType,
                                                          PyExpression expression,
                                                          CompletionResultSet dictCompletion,
                                                          String quote,
                                                          TypeEvalContext context) {
            if (expectedType instanceof PyTypedDictType typedDictType) {
                List<String> keys = new ArrayList<>();
                if (expression instanceof PyDictLiteralExpression dictLiteral) {
                    for (PyKeyValueExpression it : dictLiteral.getElements()) {
                        if (it.getKey() instanceof PyStringLiteralExpression key) {
                            keys.add(key.getStringValue());
                        }
                    }
                }
                else if (!(expression instanceof PySetLiteralExpression)) {
                    return;
                }
                for (String key : typedDictType.fields(context).keySet()) {
                    if (keys.contains(key)) {
                        continue;
                    }
                    // TODO consulo: MLRankingIgnorable missing, MLRankingIgnorable.wrap(...) dropped
                    dictCompletion.addElement(
                        LookupElementBuilder
                            .create(quote + key + quote)
                            .withTypeText("dict key")
                            .withIcon(PlatformIconGroup.nodesParameter())
                    );
                }
            }
        }
    }
}
