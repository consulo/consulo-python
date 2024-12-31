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
package com.jetbrains.python.impl.documentation.docstrings;

import consulo.language.psi.PsiElement;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.ObjectUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public enum DocStringFormat {
  /**
   * @see DocStringUtil#ensureNotPlainDocstringFormat(PsiElement)
   */
  PLAIN("Plain", ""),
  EPYTEXT("Epytext", "epytext"),
  REST("reStructuredText", "rest"),
  NUMPY("NumPy", "numpy"),
  GOOGLE("Google", "google");

  public static final List<String> ALL_NAMES = getAllNames();

  @Nonnull
  private static List<String> getAllNames() {
    return Collections.unmodifiableList(ContainerUtil.map(values(), format -> format.getName()));
  }

  public static final List<String> ALL_NAMES_BUT_PLAIN = getAllNamesButPlain();

  @Nonnull
  private static List<String> getAllNamesButPlain() {
    return Collections.unmodifiableList(ContainerUtil.mapNotNull(values(), format -> format == PLAIN ? null : format.getName()));
  }

  @Nullable
  public static DocStringFormat fromName(@Nonnull String name) {
    for (DocStringFormat format : values()) {
      if (format.getName().equalsIgnoreCase(name)) {
        return format;
      }
    }
    return null;
  }

  @Nonnull
  public static DocStringFormat fromNameOrPlain(@Nonnull String name) {
    return ObjectUtil.notNull(fromName(name), PLAIN);
  }

  private final String myName;
  private final String myFormatterCommand;

  DocStringFormat(@Nonnull String name, @Nonnull String formatterCommand) {
    myName = name;
    myFormatterCommand = formatterCommand;
  }

  @Nonnull
  public String getName() {
    return myName;
  }

  @Nonnull
  public String getFormatterCommand() {
    return myFormatterCommand;
  }
}
