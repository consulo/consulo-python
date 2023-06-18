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

package com.jetbrains.python.impl.documentation.doctest;

import consulo.annotation.component.ExtensionImpl;
import consulo.language.ast.TokenSet;
import com.jetbrains.python.PythonDialectsTokenSetContributorBase;
import javax.annotation.Nonnull;

/**
 * User : ktisha
 */
@ExtensionImpl
public class PyDocstringTokenSetContributor extends PythonDialectsTokenSetContributorBase {
  public static final TokenSet DOCSTRING_REFERENCE_EXPRESSIONS = TokenSet.create(PyDocstringTokenTypes.DOC_REFERENCE);

  @Nonnull
  @Override
  public TokenSet getExpressionTokens() {
    return DOCSTRING_REFERENCE_EXPRESSIONS;
  }

  @Nonnull
  @Override
  public TokenSet getReferenceExpressionTokens() {
    return DOCSTRING_REFERENCE_EXPRESSIONS;
  }
}
