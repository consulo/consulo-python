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

package com.jetbrains.python.impl.codeInsight;

import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyReferenceOwner;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.TargetElementUtilExtender;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.util.PsiTreeUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;

/**
 * @author yole
 */
@ExtensionImpl
public class PyTargetElementUtilEx implements TargetElementUtilExtender {
  @Override
  public boolean includeSelfInGotoImplementation(@Nonnull PsiElement element) {
    return element.getLanguage() != PythonLanguage.getInstance();
  }

  @Nullable
  @Override
  public PsiElement getReferenceOrReferencedElement(@Nonnull PsiReference ref, @Nonnull Set<String> flags) {
    if (!flags.contains(ELEMENT_NAME_ACCEPTED)) {
      return null;
    }

    final PsiElement element = ref.getElement();
    PsiElement result = ref.resolve();
    Set<PsiElement> visited = new HashSet<>();
    visited.add(result);
    while (result instanceof PyReferenceOwner && (result instanceof PyReferenceExpression || result instanceof PyTargetExpression)) {
      PsiElement nextResult = ((PyReferenceOwner)result).getReference(PyResolveContext.noImplicits()).resolve();
      if (nextResult != null && !visited.contains(nextResult) &&
        PsiTreeUtil.getParentOfType(element, ScopeOwner.class) == PsiTreeUtil.getParentOfType(result, ScopeOwner.class) &&
        (nextResult instanceof PyReferenceExpression || nextResult instanceof PyTargetExpression || nextResult instanceof PyParameter)) {
        visited.add(nextResult);
        result = nextResult;
      }
      else {
        break;
      }
    }
    return result;
  }
}
