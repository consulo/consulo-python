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

import consulo.language.ast.TokenSet;

import jakarta.annotation.Nonnull;

/**
 * @author vlan
 */
public abstract class PythonDialectsTokenSetContributorBase implements PythonDialectsTokenSetContributor {
  @Nonnull
  @Override
  public TokenSet getStatementTokens() {
    return TokenSet.EMPTY;
  }

  @Nonnull
  @Override
  public TokenSet getExpressionTokens() {
    return TokenSet.EMPTY;
  }

  @Nonnull
  @Override
  public TokenSet getNameDefinerTokens() {
    return TokenSet.EMPTY;
  }

  @Nonnull
  @Override
  public TokenSet getKeywordTokens() {
    return TokenSet.EMPTY;
  }

  @Nonnull
  @Override
  public TokenSet getParameterTokens() {
    return TokenSet.EMPTY;
  }

  @Nonnull
  @Override
  public TokenSet getFunctionDeclarationTokens() {
    return TokenSet.EMPTY;
  }

  @Nonnull
  @Override
  public TokenSet getUnbalancedBracesRecoveryTokens() {
    return TokenSet.EMPTY;
  }

  @Nonnull
  @Override
  public TokenSet getReferenceExpressionTokens() {
    return TokenSet.EMPTY;
  }
}
