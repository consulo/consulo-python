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

package com.jetbrains.python.codeInsight.completion;

import static com.intellij.patterns.PlatformPatterns.psiElement;

import javax.annotation.Nonnull;
import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.psi.PyParameterList;
import consulo.codeInsight.completion.CompletionProvider;
import consulo.ui.image.Image;

/**
 * @author yole
 */
public class PyParameterCompletionContributor extends CompletionContributor {
  public PyParameterCompletionContributor() {
    extend(CompletionType.BASIC,
           psiElement().inside(PyParameterList.class).afterLeaf("*"),
           new ParameterCompletionProvider("args"));
    extend(CompletionType.BASIC,
           psiElement().inside(PyParameterList.class).afterLeaf("**"),
           new ParameterCompletionProvider("kwargs"));
  }

  private static class ParameterCompletionProvider implements CompletionProvider
  {
    private String myName;

    private ParameterCompletionProvider(String name) {
      myName = name;
    }

    @Override
	public void addCompletions(@Nonnull CompletionParameters parameters,
                                  ProcessingContext context,
                                  @Nonnull CompletionResultSet result) {
      result.addElement(LookupElementBuilder.create(myName).withIcon((Image) AllIcons.Nodes.Parameter));
    }
  }
}
