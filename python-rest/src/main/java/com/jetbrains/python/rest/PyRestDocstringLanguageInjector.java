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

package com.jetbrains.python.rest;

import com.jetbrains.python.impl.documentation.doctest.PyDocstringLanguageDialect;
import com.jetbrains.rest.psi.RestLine;
import consulo.annotation.component.ExtensionImpl;
import consulo.document.util.TextRange;
import consulo.language.inject.InjectedLanguagePlaces;
import consulo.language.inject.LanguageInjector;
import consulo.language.psi.PsiLanguageInjectionHost;
import consulo.util.lang.StringUtil;

import jakarta.annotation.Nonnull;
import java.util.List;

/**
 * User: ktisha
 */
@ExtensionImpl
public class PyRestDocstringLanguageInjector implements LanguageInjector {
  @Override
  public void injectLanguages(@Nonnull final PsiLanguageInjectionHost host, @Nonnull final InjectedLanguagePlaces injectionPlacesRegistrar) {
    if (host instanceof RestLine) {
      int start = 0;
      int end = host.getTextLength() - 1;
      final String text = host.getText();
      final List<String> strings = StringUtil.split(text, "\n", false);

      boolean gotExample = false;

      int currentPosition = 0;
      boolean endsWithSlash = false;
      for (String string : strings) {
        final String trimmedString = string.trim();
        if (!trimmedString.startsWith(">>>") && !trimmedString.startsWith("...") && gotExample && start < end) {
          gotExample = false;
          if (!endsWithSlash)
            injectionPlacesRegistrar.addPlace(PyDocstringLanguageDialect.getInstance(), TextRange.create(start, end),  null, null);
        }
        if (endsWithSlash) {
          endsWithSlash = false;
          injectionPlacesRegistrar.addPlace(PyDocstringLanguageDialect.getInstance(),
                                            TextRange.create(start, getEndOffset(currentPosition, string)),  null, null);
        }

        if (trimmedString.startsWith(">>>")) {
          if (trimmedString.endsWith("\\"))
            endsWithSlash = true;

          if (!gotExample)
            start = currentPosition;

          gotExample = true;
          end = getEndOffset(currentPosition, string);
        }
        else if (trimmedString.startsWith("...") && gotExample) {
          if (trimmedString.endsWith("\\"))
            endsWithSlash = true;

          end = getEndOffset(currentPosition, string);
        }
        currentPosition += string.length();
      }
      if (gotExample && start < end)
        injectionPlacesRegistrar.addPlace(PyDocstringLanguageDialect.getInstance(), TextRange.create(start, end),  null, null);
    }
  }

  private int getEndOffset(int start, String s) {
    int length = s.length();
    int end = start + length;
    if (s.endsWith("\n"))
      end -= 1;
    return end;
  }
}
