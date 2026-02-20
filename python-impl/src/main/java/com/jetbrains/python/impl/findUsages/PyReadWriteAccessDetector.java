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

package com.jetbrains.python.impl.findUsages;

import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.highlight.ReadWriteAccessDetector;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import com.jetbrains.python.psi.PyAugAssignmentStatement;
import com.jetbrains.python.psi.PyDelStatement;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.impl.psi.impl.PyAugAssignmentStatementNavigator;

/**
 * @author yole
 */
@ExtensionImpl
public class PyReadWriteAccessDetector extends ReadWriteAccessDetector {
  @Override
  public boolean isReadWriteAccessible(PsiElement element) {
    return element instanceof PyTargetExpression || element instanceof PyReferenceExpression;
  }

  @Override
  public boolean isDeclarationWriteAccess(PsiElement element) {
    return element instanceof PyTargetExpression || element.getParent() instanceof PyDelStatement;
  }

  @Override
  public Access getReferenceAccess(PsiElement referencedElement, PsiReference reference) {
    return getExpressionAccess(reference.getElement());
  }

  @Override
  public Access getExpressionAccess(PsiElement expression) {
    if (isDeclarationWriteAccess(expression)) {
      return Access.Write;
    }
    if (expression instanceof PyReferenceExpression) {
      PyAugAssignmentStatement statement = PyAugAssignmentStatementNavigator.getStatementByTarget(expression);
      if (statement != null) {
        return Access.ReadWrite;
      }
    }
    return Access.Read;
  }
}
