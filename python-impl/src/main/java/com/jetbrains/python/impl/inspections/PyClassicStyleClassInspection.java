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
import com.jetbrains.python.impl.inspections.quickfix.TransformClassicClassQuickFix;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyExpression;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.ast.ASTNode;
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
public class PyClassicStyleClassInspection extends PyInspection {
  @Nls
  @Nonnull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.classic.class.usage");
  }

  @Override
  public boolean isEnabledByDefault() {
    return false;
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
    public void visitPyClass(PyClass node) {
      final ASTNode nameNode = node.getNameNode();
      if (!node.isNewStyleClass(myTypeEvalContext) && nameNode != null) {
        PyExpression[] superClassExpressions = node.getSuperClassExpressions();
        if (superClassExpressions.length == 0) {
          registerProblem(nameNode.getPsi(),
                          PyBundle.message("INSP.classic.class.usage.old.style.class"),
                          new TransformClassicClassQuickFix());
        }
        else {
          registerProblem(nameNode.getPsi(), PyBundle.message("INSP.classic.class.usage.old.style.class.ancestors"));
        }
      }
    }
  }
}
