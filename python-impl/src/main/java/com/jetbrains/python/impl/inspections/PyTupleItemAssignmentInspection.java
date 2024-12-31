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
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.impl.PyBundle;
import com.jetbrains.python.impl.inspections.quickfix.PyReplaceTupleWithListQuickFix;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PySubscriptionExpression;
import com.jetbrains.python.impl.psi.types.PyTupleType;
import com.jetbrains.python.psi.types.PyType;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import org.jetbrains.annotations.Nls;

import jakarta.annotation.Nonnull;

/**
 * @author Alexey.Ivanov
 */
@ExtensionImpl
public class PyTupleItemAssignmentInspection extends PyInspection {
  @Nls
  @Nonnull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.tuple.item.assignment");
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

    public Visitor(final ProblemsHolder holder, LocalInspectionToolSession session) {
      super(holder, session);
    }

    @Override
    public void visitPyAssignmentStatement(PyAssignmentStatement node) {
      PyExpression[] targets = node.getTargets();
      if (targets.length == 1 && targets[0] instanceof PySubscriptionExpression) {
        PySubscriptionExpression subscriptionExpression = (PySubscriptionExpression)targets[0];
        if (subscriptionExpression.getOperand() instanceof PyReferenceExpression) {
          PyReferenceExpression referenceExpression = (PyReferenceExpression)subscriptionExpression.getOperand();
          PsiElement element = referenceExpression.followAssignmentsChain(getResolveContext()).getElement();
          if (element instanceof PyExpression) {
            PyExpression expression = (PyExpression)element;
            PyType type = myTypeEvalContext.getType(expression);
            if (type instanceof PyTupleType) {
              registerProblem(node, PyBundle.message("INSP.tuples.never.assign.items"), new PyReplaceTupleWithListQuickFix());
            }
          }
        }
      }
    }
  }
}
