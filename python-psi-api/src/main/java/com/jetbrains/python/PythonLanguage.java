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

package com.jetbrains.python;

import com.jetbrains.python.psi.LanguageLevel;
import consulo.language.Language;
import consulo.language.version.LanguageVersion;
import consulo.python.language.PythonLanguageVersion;
import jakarta.annotation.Nonnull;

/**
 * @author yole
 */
public class PythonLanguage extends Language {
  public static final PythonLanguage INSTANCE = new PythonLanguage();

  @Deprecated
  public static PythonLanguage getInstance() {
    return INSTANCE;
  }

  private final LanguageVersion[] myVersions;

  private PythonLanguage() {
    super("Python");
    LanguageLevel[] levels = LanguageLevel.values();
    myVersions = new LanguageVersion[levels.length];
    for (int i = 0; i < levels.length; i++) {
      LanguageLevel level = levels[i];
      myVersions[i] = new PythonLanguageVersion(level, this);
    }
  }

  @Override
  public boolean isCaseSensitive() {
    return true;
  }

  @Nonnull
  @Override
  protected LanguageVersion[] findVersions() {
    return myVersions;
  }

  @Nonnull
  public LanguageVersion getVersion(LanguageLevel languageLevel) {
    return myVersions[languageLevel.ordinal()];
  }
}
