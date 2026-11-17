// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.completion;

import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.impl.psi.resolve.PyResolveUtil;
import com.jetbrains.python.impl.psi.types.PyTypedDictType;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyDictLiteralExpression;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyKeyValueExpression;
import com.jetbrains.python.psi.PyKeywordArgument;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PySubscriptionExpression;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.dumb.DumbAware;
import consulo.language.Language;
import consulo.language.editor.completion.CompletionContributor;
import consulo.language.editor.completion.CompletionParameters;
import consulo.language.editor.completion.CompletionProvider;
import consulo.language.editor.completion.CompletionResultSet;
import consulo.language.editor.completion.CompletionType;
import consulo.language.editor.completion.lookup.LookupElementBuilder;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.ProcessingContext;
import consulo.platform.base.icon.PlatformIconGroup;

import java.util.Collection;

import static consulo.language.pattern.PlatformPatterns.psiElement;

/**
 * User: catherine
 * <p/>
 * Complete known keys for dictionaries
 */
@ExtensionImpl(id = "PyDictKeyNames")
public final class PyDictKeyNamesCompletionContributor extends CompletionContributor implements DumbAware {
    private static final String DEFAULT_QUOTE = "\"";

    public PyDictKeyNamesCompletionContributor() {
        extend(
            CompletionType.BASIC,
            psiElement().inside(PySubscriptionExpression.class),
            new CompletionProvider() {
                @Override
                @RequiredReadAction
                public void addCompletions(CompletionParameters parameters,
                                           ProcessingContext context,
                                           CompletionResultSet result) {
                    PsiElement original = parameters.getOriginalPosition();
                    if (original == null) {
                        return;
                    }

                    PySubscriptionExpression subscription = PsiTreeUtil.getParentOfType(original, PySubscriptionExpression.class);
                    if (subscription == null) {
                        return;
                    }

                    PyExpression operand = subscription.getOperand();
                    DictKeyCompletionResultSet dictCompletion =
                        new DictKeyCompletionResultSet(result, subscription.getIndexExpression() instanceof PyStringLiteralExpression);

                    if (addCompletionIfOperandIsTypedDict(operand, dictCompletion)) {
                        return;
                    }
                    if (operand instanceof PyReferenceExpression) {
                        PyExpression resolvedElement = PyResolveUtil.fullResolveLocally((PyReferenceExpression) operand);
                        if (resolvedElement instanceof PyDictLiteralExpression) {
                            addDictLiteralKeys((PyDictLiteralExpression) resolvedElement, dictCompletion);
                            addAdditionalKeys(parameters.getOriginalFile(), operand, dictCompletion);
                        }
                        if (resolvedElement instanceof PyCallExpression) {
                            addDictConstructorKeys((PyCallExpression) resolvedElement, dictCompletion);
                            addAdditionalKeys(parameters.getOriginalFile(), operand, dictCompletion);
                        }
                    }
                }
            }
        );
    }

    /**
     * Add index expression completion if an operand is a TypedDict
     *
     * @return true if an operand is a TypedDict
     */
    private static boolean addCompletionIfOperandIsTypedDict(PyExpression operand,
                                                             DictKeyCompletionResultSet dictCompletion) {
        TypeEvalContext typeEvalContext = TypeEvalContext.codeCompletion(operand.getProject(), operand.getContainingFile());
        PyType type = typeEvalContext.getType(operand);
        if (type instanceof PyTypedDictType dictType) {
            for (String key : dictType.fields(typeEvalContext).keySet()) {
                dictCompletion.addKey(key);
            }
            return true;
        }
        return false;
    }

    /**
     * add keys to completion result from dict constructor
     */
    @RequiredReadAction
    private static void addDictConstructorKeys(PyCallExpression dictConstructor, DictKeyCompletionResultSet result) {
        PyExpression callee = dictConstructor.getCallee();
        if (callee == null) {
            return;
        }
        String name = callee.getText();
        if ("dict".equals(name)) {
            TypeEvalContext context = TypeEvalContext.codeCompletion(callee.getProject(), callee.getContainingFile());
            PyType type = context.getType(dictConstructor);
            if (type != null && type.isBuiltin()) {
                PyArgumentList list = dictConstructor.getArgumentList();
                if (list == null) {
                    return;
                }
                PyExpression[] argumentList = list.getArguments();
                for (PyExpression argument : argumentList) {
                    if (argument instanceof PyKeywordArgument keywordArgument) {
                        String keyword = keywordArgument.getKeyword();
                        if (keyword != null) {
                            result.addKey(keyword);
                        }
                    }
                }
            }
        }
    }

    /**
     * add keys from assignment statements
     * For instance, dictionary['b']=b
     *
     * @param file    to get additional keys
     * @param operand is operand of origin element
     * @param result  is completion result set
     */
    @RequiredReadAction
    private static void addAdditionalKeys(PsiFile file, PsiElement operand, DictKeyCompletionResultSet result) {
        Collection<PySubscriptionExpression> subscriptionExpressions = PsiTreeUtil.findChildrenOfType(file, PySubscriptionExpression.class);
        for (PySubscriptionExpression expr : subscriptionExpressions) {
            if (expr.getOperand().getText().equals(operand.getText()) &&
                expr.getParent() instanceof PyAssignmentStatement assignmentStatement &&
                expr.equals(assignmentStatement.getLeftHandSideExpression()) &&
                expr.getIndexExpression() instanceof PyStringLiteralExpression key) {
                result.addKey(key);
            }
        }
    }

    /**
     * add keys from dict literal expression
     */
    private static void addDictLiteralKeys(PyDictLiteralExpression dict, DictKeyCompletionResultSet result) {
        PyKeyValueExpression[] keyValues = dict.getElements();
        for (PyKeyValueExpression expression : keyValues) {
            if (expression.getKey() instanceof PyStringLiteralExpression key) {
                result.addKey(key);
            }
        }
    }

    @Override
    public Language getLanguage() {
        return PythonLanguage.INSTANCE;
    }

    private static class DictKeyCompletionResultSet {
        private final CompletionResultSet myResult;
        private final boolean myIsInsideString;

        DictKeyCompletionResultSet(CompletionResultSet result, boolean isInsideString) {
            myResult = result;
            myIsInsideString = isInsideString;
        }

        @RequiredReadAction
        void addKey(PyStringLiteralExpression keyExpression) {
            if (myIsInsideString) {
                addElement(keyExpression.getStringValue());
            }
            else {
                addElement(keyExpression.getText());
            }
        }

        void addKey(String key) {
            String lookupString = myIsInsideString ? key : "%s%s%s".formatted(DEFAULT_QUOTE, key, DEFAULT_QUOTE);
            addElement(lookupString);
        }

        private void addElement(String lookupString) {
            myResult.addElement(LookupElementBuilder
                .create(lookupString)
                .withTypeText("dict key")
                .withIcon(PlatformIconGroup.nodesParameter()));
        }
    }
}
