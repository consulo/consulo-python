// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.completion;

import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyExpressionStatement;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyStatementList;
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
import consulo.language.pattern.FilterPattern;
import consulo.language.pattern.PlatformPatterns;
import consulo.language.psi.PsiElement;
import consulo.language.psi.filter.ElementFilter;
import consulo.language.util.ProcessingContext;
import org.jspecify.annotations.Nullable;

import java.util.function.Predicate;

@ExtensionImpl
public final class PyMetaClassCompletionContributor extends CompletionContributor implements DumbAware {
    public PyMetaClassCompletionContributor() {
        extend(CompletionType.BASIC,
            PlatformPatterns
                .psiElement()
                .withLanguage(PythonLanguage.getInstance())
                .withParents(PyReferenceExpression.class, PyExpressionStatement.class, PyStatementList.class, PyClass.class)
                .and(hasLanguageLevel(LanguageLevel::isPython2)),
            new CompletionProvider() {
                @Override
                @RequiredReadAction
                public void addCompletions(CompletionParameters parameters,
                                           ProcessingContext context,
                                           CompletionResultSet result) {
                    result.addElement(LookupElementBuilder.create("__metaclass__ = "));
                }
            });
        extend(CompletionType.BASIC,
            PlatformPatterns
                .psiElement()
                .withLanguage(PythonLanguage.getInstance())
                .withParents(PyReferenceExpression.class, PyArgumentList.class, PyClass.class)
                .and(hasLanguageLevel(level -> !level.isPython2())),
            new CompletionProvider() {
                @Override
                @RequiredReadAction
                public void addCompletions(CompletionParameters parameters,
                                           ProcessingContext context,
                                           CompletionResultSet result) {
                    result.addElement(LookupElementBuilder.create("metaclass="));
                }
            });
    }

    public static FilterPattern hasLanguageLevel(Predicate<? super LanguageLevel> processor) {
        return new FilterPattern(new ElementFilter() {
            @Override
            public boolean isAcceptable(Object element, @Nullable PsiElement context) {
                if (element instanceof PsiElement) {
                    return processor.test(LanguageLevel.forElement((PsiElement) element));
                }
                return false;
            }

            @Override
            public boolean isClassAcceptable(Class hintClass) {
                return true;
            }
        });
    }

    @Override
    public Language getLanguage() {
        return PythonLanguage.INSTANCE;
    }
}
