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
package com.jetbrains.python.psi;

import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.version.LanguageVersion;
import consulo.python.language.PythonLanguageVersion;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.List;

public interface PyFile extends PyElement, PsiFile, PyDocStringOwner, ScopeOwner {
  List<PyStatement> getStatements();

  List<PyClass> getTopLevelClasses();

  @Nonnull
  List<PyFunction> getTopLevelFunctions();

  List<PyTargetExpression> getTopLevelAttributes();

  @Nullable
  PyFunction findTopLevelFunction(String name);

  @Nullable
  PyClass findTopLevelClass(String name);

  @Nullable
  PyTargetExpression findTopLevelAttribute(String name);

  @RequiredReadAction
  @Nonnull
  default LanguageLevel getLanguageLevel() {
    LanguageVersion languageVersion = getLanguageVersion();
    if (languageVersion instanceof PythonLanguageVersion pythonLanguageVersion) {
      return pythonLanguageVersion.getLanguageLevel();
    }

    return LanguageLevel.getDefault();
  }

  /**
   * Return the list of all 'from ... import' statements in the top-level scope of the file.
   *
   * @return the list of 'from ... import' statements.
   */
  @Nonnull
  List<PyFromImportStatement> getFromImports();

  /**
   * Return an exported PSI element defined in the file with the given name.
   */
  @Nullable
  PsiElement findExportedName(String name);

  /**
   * Iterate over exported PSI elements defined in the file.
   */
  @Nonnull
  Iterable<PyElement> iterateNames();

  /**
   * Return the resolved exported elements.
   */
  @Nonnull
  List<RatedResolveResult> multiResolveName(@Nonnull String name);

  /**
   * @deprecated Use {@link #multiResolveName(String)} instead.
   */
  @Deprecated
  @Nullable
  PsiElement getElementNamed(String name);

  /**
   * Returns the list of import elements in all 'import xxx' statements in the top-level scope of the file.
   *
   * @return the list of import targets.
   */
  @Nonnull
  List<PyImportElement> getImportTargets();

  /**
   * Returns the list of names in the __all__ declaration, or null if there is no such declaration in the module.
   *
   * @return the list of names or null.
   */
  @Nullable
  List<String> getDunderAll();

  /**
   * Return true if the file contains a 'from __future__ import ...' statement with given feature.
   */
  boolean hasImportFromFuture(FutureFeature feature);

  /**
   * If the function raises a DeprecationWarning or a PendingDeprecationWarning, returns the explanation text provided for the warning.
   *
   * @return the deprecation message or null if the function is not deprecated.
   */
  String getDeprecationMessage();

  /**
   * Returns the sequential list of import statements in the beginning of the file.
   */
  List<PyImportStatementBase> getImportBlock();
}
