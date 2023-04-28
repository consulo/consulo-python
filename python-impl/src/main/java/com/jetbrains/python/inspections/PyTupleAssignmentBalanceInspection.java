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

import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyTupleType;
import com.jetbrains.python.psi.types.PyType;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElementVisitor;
import org.jetbrains.annotations.Nls;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author Alexey.Ivanov
 */
@ExtensionImpl
public class PyTupleAssignmentBalanceInspection extends PyInspection {
  @Nls
  @Nonnull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.incorrect.assignment");
  }

  @Nonnull
  @Override
  public PsiElementVisitor buildVisitor(@Nonnull ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @Nonnull LocalInspectionToolSession session,
                                        Object state) {
    return new Visitor(holder, session);
  }

  private static class Visitor extends PyInspectionVisitor {
    public Visitor(@Nullable ProblemsHolder holder, @Nonnull LocalInspectionToolSession session) {
      super(holder, session);
    }

    @Override
    public void visitPyAssignmentStatement(PyAssignmentStatement node) {
      PyExpression lhsExpression = node.getLeftHandSideExpression();
      PyExpression assignedValue = node.getAssignedValue();
      if (lhsExpression instanceof PyParenthesizedExpression)     // PY-4360
      {
        lhsExpression = ((PyParenthesizedExpression)lhsExpression).getContainedExpression();
      }

      if (assignedValue == null) {
        return;
      }
      PyType type = myTypeEvalContext.getType(assignedValue);
      if (assignedValue instanceof PyReferenceExpression && !(type instanceof PyTupleType)) {
        return;
      }
      if (lhsExpression instanceof PyTupleExpression && type != null) {
        int valuesLength = PyUtil.getElementsCount(assignedValue, myTypeEvalContext);
        if (valuesLength == -1) {
          return;
        }
        PyExpression[] elements = ((PyTupleExpression)lhsExpression).getElements();

        boolean containsStarExpression = false;
        if (LanguageLevel.forElement(node).isPy3K()) {
          for (PyExpression target : elements) {
            if (target instanceof PyStarExpression) {
              if (containsStarExpression) {
                registerProblem(target, "Only one starred expression allowed in assignment");
                return;
              }
              containsStarExpression = true;
              ++valuesLength;
            }
          }
        }

        int targetsLength = elements.length;
        if (targetsLength > valuesLength) {
          registerProblem(assignedValue, "Need more values to unpack");
        }
        else if (!containsStarExpression && targetsLength < valuesLength) {
          registerProblem(assignedValue, "Too many values to unpack");
        }
      }
    }
  }
}
