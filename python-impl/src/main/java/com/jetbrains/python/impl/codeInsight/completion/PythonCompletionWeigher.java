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
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.completion.CompletionLocation;
import consulo.language.editor.completion.CompletionWeigher;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.completion.lookup.LookupElementPresentation;
import consulo.language.psi.PsiUtilCore;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nonnull;

/**
 * Weighs down items starting with two underscores.
 * <br/>
 * User: dcheryasov
 * Date: 11/11/10 4:24 PM
 */
@ExtensionImpl
public class PythonCompletionWeigher extends CompletionWeigher {
  @NonNls private static final String DOUBLE_UNDER = "__";

  @Override
  public Comparable weigh(@Nonnull final LookupElement element, @Nonnull final CompletionLocation location) {
    if (!PsiUtilCore.findLanguageFromElement(location.getCompletionParameters().getPosition()).isKindOf(PythonLanguage.getInstance())) {
      return 0;
    }

    final String name = element.getLookupString();
    final LookupElementPresentation presentation = LookupElementPresentation.renderElement(element);
    // move dict keys to the top
    if ("dict key".equals(presentation.getTypeText())) {
      return element.getLookupString().length();
    }
    if (name.startsWith(DOUBLE_UNDER)) {
      if (name.endsWith(DOUBLE_UNDER)) return -10; // __foo__ is lowest
      else return -5; // __foo is lower than normal
    }
    return 0; // default
  }
}
