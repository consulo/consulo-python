// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.completion;

import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.impl.extensions.CaptureExtKt;
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
import consulo.language.util.ProcessingContext;
import consulo.platform.base.icon.PlatformIconGroup;

import static consulo.language.pattern.PlatformPatterns.psiElement;


@ExtensionImpl
public final class PyParameterCompletionContributor extends CompletionContributor implements DumbAware {
    public PyParameterCompletionContributor() {
        extend(CompletionType.BASIC,
            CaptureExtKt.inParameterList(psiElement()).afterLeaf("*"),
            new ParameterCompletionProvider("args"));
        extend(CompletionType.BASIC,
            CaptureExtKt.inParameterList(psiElement()).afterLeaf("**"),
            new ParameterCompletionProvider("kwargs"));
    }

    @Override
    public Language getLanguage() {
        return PythonLanguage.INSTANCE;
    }

    private static final class ParameterCompletionProvider implements CompletionProvider {
        private final String myName;

        private ParameterCompletionProvider(String name) {
            myName = name;
        }

        @Override
        @RequiredReadAction
        public void addCompletions(CompletionParameters parameters,
                                   ProcessingContext context,
                                   CompletionResultSet result) {
            result.addElement(LookupElementBuilder.create(myName).withIcon(PlatformIconGroup.nodesParameter()));
        }
    }
}
