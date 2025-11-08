/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.impl.codeInsight.completion;

import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.PyParameterList;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.editor.completion.*;
import consulo.language.editor.completion.lookup.LookupElementBuilder;
import consulo.language.util.ProcessingContext;
import consulo.platform.base.icon.PlatformIconGroup;
import jakarta.annotation.Nonnull;

import static consulo.language.pattern.PlatformPatterns.psiElement;

/**
 * @author yole
 */
@ExtensionImpl
public class PyParameterCompletionContributor extends CompletionContributor {
    public PyParameterCompletionContributor() {
        extend(
            CompletionType.BASIC,
            psiElement().inside(PyParameterList.class).afterLeaf("*"),
            new ParameterCompletionProvider("args")
        );
        extend(
            CompletionType.BASIC,
            psiElement().inside(PyParameterList.class).afterLeaf("**"),
            new ParameterCompletionProvider("kwargs")
        );
    }

    @Nonnull
    @Override
    public Language getLanguage() {
        return PythonLanguage.INSTANCE;
    }

    private static class ParameterCompletionProvider implements CompletionProvider {
        private String myName;

        private ParameterCompletionProvider(String name) {
            myName = name;
        }

        @Override
        @RequiredReadAction
        public void addCompletions(
            @Nonnull CompletionParameters parameters,
            ProcessingContext context,
            @Nonnull CompletionResultSet result
        ) {
            result.addElement(LookupElementBuilder.create(myName).withIcon(PlatformIconGroup.nodesParameter()));
        }
    }
}
