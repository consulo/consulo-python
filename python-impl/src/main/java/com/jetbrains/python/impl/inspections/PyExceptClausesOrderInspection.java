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
import com.jetbrains.python.impl.inspections.quickfix.PyMoveExceptQuickFix;
import com.jetbrains.python.psi.*;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import org.jetbrains.annotations.Nls;

import jakarta.annotation.Nonnull;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Alexey.Ivanov
 */
@ExtensionImpl
public class PyExceptClausesOrderInspection extends PyInspection {
  @Nls
  @Nonnull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.bad.except.clauses.order");
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
    public void visitPyTryExceptStatement(PyTryExceptStatement node) {
      PyExceptPart[] exceptParts = node.getExceptParts();
      if (exceptParts.length > 1) {
        Set<PyClass> exceptClasses = new HashSet<>();
        for (PyExceptPart exceptPart : exceptParts) {
          PyExpression exceptClass = exceptPart.getExceptClass();
          if (exceptClass instanceof PyReferenceExpression) {
            PsiElement element = ((PyReferenceExpression)exceptClass).followAssignmentsChain(getResolveContext()).getElement();
            if (element instanceof PyClass) {
              PyClass pyClass = (PyClass)element;
              if (exceptClasses.contains(pyClass)) {
                registerProblem(exceptClass, PyBundle.message("INSP.class.$0.already.caught", pyClass.getName()));
              }
              else {
                for (PyClass superClass : pyClass.getSuperClasses(null)) {
                  if (exceptClasses.contains(superClass)) {
                    registerProblem(exceptClass,
                                    PyBundle.message("INSP.class.$0.superclass.$1.already.caught", superClass.getName(), pyClass.getName()),
                                    new PyMoveExceptQuickFix
                                      ());
                  }
                }
              }
              exceptClasses.add(pyClass);
            }
          }
        }
      }
    }
  }
}
