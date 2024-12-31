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

package com.jetbrains.python.impl.psi.impl.references;

import jakarta.annotation.Nonnull;

import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementResolveResult;
import consulo.language.psi.ResolveResult;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.collection.ArrayUtil;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.resolve.PyResolveContext;

/**
 * @author yole
 */
public class PyTargetReference extends PyReferenceImpl {
  public PyTargetReference(PyQualifiedExpression element, PyResolveContext context) {
    super(element, context);
  }

  @Nonnull
  @Override
  public ResolveResult[] multiResolve(boolean incompleteCode) {
    final ResolveResult[] results = super.multiResolve(incompleteCode);
    boolean shadowed = false;
    for (ResolveResult result : results) {
      final PsiElement element = result.getElement();
      if (element != null && (element.getContainingFile() != myElement.getContainingFile() ||
                              element instanceof PyFunction || element instanceof PyClass)) {
        shadowed = true;
        break;
      }
    }
    if (results.length > 0 && !shadowed) {
      return results;
    }
    // resolve to self if no other target found
    return new ResolveResult[] { new PsiElementResolveResult(myElement) };
  }

  @Nonnull
  @Override
  public Object[] getVariants() {
    final PyImportElement importElement = PsiTreeUtil.getParentOfType(myElement, PyImportElement.class);
    // reference completion is useless in 'as' part of import statement (PY-2384)
    if (importElement != null && myElement == importElement.getAsNameElement()) {
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }
    return super.getVariants();
  }
}
