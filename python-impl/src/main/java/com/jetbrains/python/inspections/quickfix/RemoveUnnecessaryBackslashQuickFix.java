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

package com.jetbrains.python.inspections.quickfix;

import javax.annotation.Nonnull;

import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.project.Project;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.collection.Stack;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.editor.PythonEnterHandler;
import com.jetbrains.python.psi.*;

/**
 * User: catherine
 *
 * QuickFix to remove all unnecessary backslashes in expression
 */
public class RemoveUnnecessaryBackslashQuickFix implements LocalQuickFix {
  @Nonnull
  public String getName() {
    return PyBundle.message("QFIX.remove.unnecessary.backslash");
  }

  @Nonnull
  public String getFamilyName() {
    return getName();
  }

  public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
    PsiElement problemElement = descriptor.getPsiElement();
    if (problemElement != null) {
      PsiElement parent = PsiTreeUtil.getParentOfType(problemElement, PythonEnterHandler.IMPLICIT_WRAP_CLASSES);
      removeBackSlash(parent);
    }
  }
  
  public static void removeBackSlash(PsiElement parent) {
    if (parent != null) {
      Stack<PsiElement> stack = new Stack<PsiElement>();
      if (parent instanceof PyParenthesizedExpression)
        stack.push(((PyParenthesizedExpression)parent).getContainedExpression());
      else
        stack.push(parent);
      while (!stack.isEmpty()) {
        PsiElement el = stack.pop();
        PsiWhiteSpace[] children = PsiTreeUtil.getChildrenOfType(el, PsiWhiteSpace.class);
        if (children != null) {
          for (PsiWhiteSpace ws : children) {
            if (ws.getText().contains("\\")) {
              ws.delete();
            }
          }
        }
        for (PsiElement psiElement : el.getChildren()) {
          stack.push(psiElement);
        }
      }
    }
  } 
}
