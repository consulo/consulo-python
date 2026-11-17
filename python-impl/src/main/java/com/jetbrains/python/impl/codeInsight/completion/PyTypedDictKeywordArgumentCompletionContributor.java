// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.completion;

import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyKeywordArgument;
import com.jetbrains.python.psi.PyReferenceExpression;
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
import consulo.language.pattern.PlatformPatterns;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.ProcessingContext;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.jetbrains.python.impl.codeInsight.typing.PyTypedDictTypeProvider.Helper.isTypingTypedDictInheritor;
import static com.jetbrains.python.impl.psi.types.PyTypedDictType.TYPED_DICT_CLOSED_PARAMETER;
import static com.jetbrains.python.impl.psi.types.PyTypedDictType.TYPED_DICT_EXTRA_ITEMS_PARAMETER;
import static com.jetbrains.python.impl.psi.types.PyTypedDictType.TYPED_DICT_TOTAL_PARAMETER;

/**
 * Completes the `total=`, `closed=`, and `extra_items=` (PEP 728) class parameters inside the base-class list of a
 * `TypedDict` definition, e.g. `class Movie(TypedDict, <caret>)`.
 */
@ExtensionImpl
public class PyTypedDictKeywordArgumentCompletionContributor extends CompletionContributor implements DumbAware {
    public PyTypedDictKeywordArgumentCompletionContributor() {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement()
                .withLanguage(PythonLanguage.getInstance())
                .withParents(PyReferenceExpression.class, PyArgumentList.class, PyClass.class),
            new CompletionProvider() {
                @Override
                @RequiredReadAction
                public void addCompletions(CompletionParameters parameters, ProcessingContext context, CompletionResultSet result) {
                    PyClass pyClass = PsiTreeUtil.getParentOfType(parameters.getPosition(), PyClass.class);
                    if (pyClass == null) {
                        return;
                    }
                    TypeEvalContext typeEvalContext = TypeEvalContext.codeCompletion(pyClass.getProject(), parameters.getOriginalFile());
                    if (!isTypingTypedDictInheritor(pyClass, typeEvalContext)) {
                        return;
                    }

                    Set<String> existingKeywords = new HashSet<>();
                    PyArgumentList superClassExpressionList = pyClass.getSuperClassExpressionList();
                    if (superClassExpressionList != null) {
                        for (PyExpression argument : superClassExpressionList.getArguments()) {
                            if (argument instanceof PyKeywordArgument keywordArgument && keywordArgument.getKeyword() != null) {
                                existingKeywords.add(keywordArgument.getKeyword());
                            }
                        }
                    }

                    for (String parameter : List.of(TYPED_DICT_TOTAL_PARAMETER, TYPED_DICT_CLOSED_PARAMETER, TYPED_DICT_EXTRA_ITEMS_PARAMETER)) {
                        if (!existingKeywords.contains(parameter)) {
                            result.addElement(LookupElementBuilder.create(parameter + "="));
                        }
                    }
                }
            }
        );
    }

    @Override
    public Language getLanguage() {
        return PythonLanguage.INSTANCE;
    }
}
