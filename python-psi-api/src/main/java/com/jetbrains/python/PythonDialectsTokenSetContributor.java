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

import javax.annotation.Nonnull;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import consulo.language.ast.TokenSet;

/**
 * Contributes element types of various kinds specific for a particular Python dialect.
 *
 * @author vlan
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface PythonDialectsTokenSetContributor {
  ExtensionPointName<PythonDialectsTokenSetContributor> EP_NAME = ExtensionPointName.create(PythonDialectsTokenSetContributor.class);

  /**
   * Returns element types that are subclasses of {@link com.jetbrains.python.psi.PyStatement}.
   */
  @Nonnull
  TokenSet getStatementTokens();

  /**
   * Returns element types that are subclasses of {@link com.jetbrains.python.psi.PyExpression}.
   */
  @Nonnull
  TokenSet getExpressionTokens();

  /**
   * Returns element types that are subclasses of {@link com.jetbrains.python.psi.NameDefiner}.
   */
  @Nonnull
  TokenSet getNameDefinerTokens();

  /**
   * Returns element types that are language keywords.
   */
  @Nonnull
  TokenSet getKeywordTokens();

  /**
   * Returns element types that are subclasses of {@link com.jetbrains.python.psi.PyParameter}.
   */
  @Nonnull
  TokenSet getParameterTokens();

  /**
   * Returns element types that are subclasses of {@link com.jetbrains.python.psi.PyFunction}.
   */
  @Nonnull
  TokenSet getFunctionDeclarationTokens();

  /**
   * Returns element types that can be used as unbalanced braces recovery tokens in the lexer.
   */
  @Nonnull
  TokenSet getUnbalancedBracesRecoveryTokens();

  /**
   * Returns element types that are subclasses of {@link com.jetbrains.python.psi.PyReferenceExpression}.
   */
  @Nonnull
  TokenSet getReferenceExpressionTokens();
}
