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
package com.jetbrains.python.inspections;

import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;

import jakarta.annotation.Nonnull;
import java.util.List;

/**
 * @author yole
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public abstract class PyInspectionExtension {
  public static final ExtensionPointName<PyInspectionExtension> EP_NAME = ExtensionPointName.create(PyInspectionExtension.class);

  public boolean ignoreUnused(PsiElement local) {
    return false;
  }

  public boolean ignoreMissingDocstring(PyDocStringOwner docStringOwner) {
    return false;
  }

  public List<String> getFunctionParametersFromUsage(PsiElement elt) {
    return null;
  }

  public boolean ignoreMethodParameters(@Nonnull PyFunction function) {
    return false;
  }

  public boolean ignorePackageNameInRequirements(@Nonnull PyQualifiedExpression importedExpression) {
    return false;
  }

  public boolean ignoreUnresolvedReference(@Nonnull PyElement node, @Nonnull PsiReference reference) {
    return false;
  }

  public boolean ignoreUnresolvedMember(@Nonnull PyType type, @Nonnull String name) {
    return false;
  }

  /**
   * Returns true if access to protected (the one started with "_") symbol should not be treated as violation.
   *
   * @param expression access expression i.e. "_foo"
   * @param context    type eval to be used
   * @return true if ignore
   */
  public boolean ignoreProtectedSymbol(@Nonnull PyReferenceExpression expression, @Nonnull TypeEvalContext context) {
    return false;
  }
}
