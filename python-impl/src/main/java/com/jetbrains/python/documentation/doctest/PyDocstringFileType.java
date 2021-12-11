
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

import com.jetbrains.python.PythonFileType;
import consulo.localize.LocalizeValue;

import javax.annotation.Nonnull;

/**
 * User : ktisha
 */
public class PyDocstringFileType extends PythonFileType {
  public static PythonFileType INSTANCE = new PyDocstringFileType();

  protected PyDocstringFileType() {
    super(new PyDocstringLanguageDialect());
  }

  @Nonnull
  @Override
  public String getId() {
    return "PyDocstring";
  }

  @Nonnull
  @Override
  public LocalizeValue getDescription() {
    return LocalizeValue.localizeTODO("python docstring");
  }

  @Nonnull
  @Override
  public String getDefaultExtension() {
    return "docstring";
  }
}
