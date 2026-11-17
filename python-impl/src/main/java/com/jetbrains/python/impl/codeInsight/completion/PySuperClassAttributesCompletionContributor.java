// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.completion;

import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyExpressionStatement;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyStatementList;
import com.jetbrains.python.psi.PyTargetExpression;
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
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.ProcessingContext;

import java.util.ArrayList;
import java.util.List;

@ExtensionImpl
public final class PySuperClassAttributesCompletionContributor extends CompletionContributor implements DumbAware {
    public PySuperClassAttributesCompletionContributor() {
        extend(CompletionType.BASIC,
            PlatformPatterns.psiElement()
                .withParents(PyReferenceExpression.class, PyExpressionStatement.class, PyStatementList.class, PyClass.class),
            new CompletionProvider() {
                @Override
                @RequiredReadAction
                public void addCompletions(CompletionParameters parameters,
                                           ProcessingContext context,
                                           CompletionResultSet result) {
                    PsiElement position = parameters.getOriginalPosition();
                    PyClass containingClass = PsiTreeUtil.getParentOfType(position, PyClass.class);

                    if (containingClass == null) {
                        return;
                    }
                    for (PyTargetExpression expr : getSuperClassAttributes(containingClass)) {
                        result.addElement(LookupElementBuilder.createWithSmartPointer(expr.getName() + " = ", expr));
                    }
                }
            }
        );
    }

    public static List<PyTargetExpression> getSuperClassAttributes(PyClass cls) {
        List<PyTargetExpression> attrs = new ArrayList<>();
        List<String> seenNames = new ArrayList<>();
        for (PyTargetExpression expr : cls.getClassAttributes()) {
            seenNames.add(expr.getName());
        }
        for (PyClass ancestor : cls.getAncestorClasses(null)) {
            for (PyTargetExpression expr : ancestor.getClassAttributes()) {
                if (!seenNames.contains(expr.getName())) {
                    seenNames.add(expr.getName());
                    attrs.add(expr);
                }
            }
        }
        return attrs;
    }

    @Override
    public Language getLanguage() {
        return PythonLanguage.INSTANCE;
    }
}
