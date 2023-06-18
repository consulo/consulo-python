/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.editor.completion.CompletionContributor;
import consulo.language.editor.completion.CompletionType;
import consulo.language.editor.completion.lookup.LookupElementBuilder;
import consulo.language.editor.completion.lookup.TailType;
import consulo.language.editor.completion.lookup.TailTypeDecorator;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.psi.util.PsiTreeUtil;

import javax.annotation.Nonnull;
import java.util.HashSet;
import java.util.Set;

import static consulo.language.pattern.PlatformPatterns.psiElement;

/**
 * @author yole
 */
@ExtensionImpl
public class PySuperMethodCompletionContributor extends CompletionContributor {
  public PySuperMethodCompletionContributor() {
    extend(CompletionType.BASIC,
           psiElement().afterLeafSkipping(psiElement().whitespace(), psiElement().withElementType(PyTokenTypes.DEF_KEYWORD)),
           (parameters, context, result) -> {
             PsiElement position = parameters.getOriginalPosition();
             PyClass containingClass = PsiTreeUtil.getParentOfType(position, PyClass.class);
             if (containingClass == null && position instanceof PsiWhiteSpace) {
               position = PsiTreeUtil.prevLeaf(position);
               containingClass = PsiTreeUtil.getParentOfType(position, PyClass.class);
             }
             if (containingClass == null) {
               return;
             }
             Set<String> seenNames = new HashSet<>();
             for (PyFunction function : containingClass.getMethods()) {
               seenNames.add(function.getName());
             }
             LanguageLevel languageLevel = LanguageLevel.forElement(parameters.getOriginalFile());
             seenNames.addAll(PyNames.getBuiltinMethods(languageLevel).keySet());
             for (PyClass ancestor : containingClass.getAncestorClasses(null)) {
               for (PyFunction superMethod : ancestor.getMethods()) {
                 if (!seenNames.contains(superMethod.getName())) {
                   String text = superMethod.getName() + superMethod.getParameterList().getText();
                   LookupElementBuilder element = LookupElementBuilder.create(text);
                   result.addElement(TailTypeDecorator.withTail(element, TailType.CASE_COLON));
                   seenNames.add(superMethod.getName());
                 }
               }
             }
           });
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return PythonLanguage.INSTANCE;
  }
}
