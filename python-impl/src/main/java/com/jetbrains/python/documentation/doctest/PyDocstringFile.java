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

package com.jetbrains.python.documentation.doctest;

import consulo.language.inject.InjectedLanguageManager;
import consulo.language.file.FileViewProvider;
import consulo.language.psi.PsiLanguageInjectionHost;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.impl.PyFileImpl;

/**
 * User: ktisha
 */
public class PyDocstringFile extends PyFileImpl {

  public PyDocstringFile(FileViewProvider viewProvider) {
    super(viewProvider, PyDocstringLanguageDialect.getInstance());
  }

  @Override
  public String toString() {
    return "DocstringFile:" + getName();
  }

  @Override
  public LanguageLevel getLanguageLevel() {
    final InjectedLanguageManager languageManager = InjectedLanguageManager.getInstance(getProject());
    final PsiLanguageInjectionHost host = languageManager.getInjectionHost(this);
    if (host != null) return LanguageLevel.forElement(host.getContainingFile());
    return super.getLanguageLevel();
  }
}