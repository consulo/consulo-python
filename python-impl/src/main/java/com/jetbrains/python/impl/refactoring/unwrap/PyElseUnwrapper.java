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

package com.jetbrains.python.impl.refactoring.unwrap;

import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import com.jetbrains.python.impl.PyBundle;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyIfStatement;
import com.jetbrains.python.psi.PyStatementWithElse;

import java.util.List;
import java.util.Set;

/**
 * User : ktisha
 */
public class PyElseUnwrapper extends PyElseUnwrapperBase {
  public PyElseUnwrapper() {
    super(PyBundle.message("unwrap.else"));
  }

  @Override
  public PsiElement collectAffectedElements(PsiElement e, List<PsiElement> toExtract) {
    super.collectAffectedElements(e, toExtract);
    return PsiTreeUtil.getParentOfType(e, PyStatementWithElse.class);
  }

  @Override
  public void collectElementsToIgnore(PsiElement element, Set<PsiElement> result) {
    PsiElement parent = element.getParent();

    while (parent instanceof PyIfStatement) {
      result.add(parent);
      parent = parent.getParent();
    }
  }

  @Override
  protected void unwrapElseBranch(PyElement branch, PsiElement parent, Context context) throws IncorrectOperationException {
    parent = PsiTreeUtil.getParentOfType(branch, PyStatementWithElse.class);
    context.extractPart(branch);
    context.delete(parent);
  }
}
