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

package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.impl.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.PyDecorator;
import com.jetbrains.python.psi.PyDecoratorList;
import com.jetbrains.python.psi.PyFunction;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.psi.PsiElementVisitor;
import org.jetbrains.annotations.Nls;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Checks nested decorators, especially whatever comes after @classmethod.
 * <br/>
 * User: dcheryasov
 * Date: Sep 4, 2010 3:56:57 AM
 */
@ExtensionImpl
public class PyNestedDecoratorsInspection extends PyInspection {
  @Nls
  @Nonnull
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.nested.decorators");
  }

  @Nonnull
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.WEAK_WARNING;
  }

  @Nonnull
  @Override
  public PsiElementVisitor buildVisitor(@Nonnull ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @Nonnull LocalInspectionToolSession session,
                                        Object state) {
    return new Visitor(holder, session);
  }

  public static class Visitor extends PyInspectionVisitor {
    public Visitor(@Nullable ProblemsHolder holder, @Nonnull LocalInspectionToolSession session) {
      super(holder, session);
    }

    @Override
    public void visitPyFunction(final PyFunction node) {
      PyDecoratorList decolist = node.getDecoratorList();
      if (decolist != null) {
        PyDecorator[] decos = decolist.getDecorators();
        if (decos.length > 1) {
          for (int i = decos.length - 1; i >= 1; i -= 1) {
            PyDecorator deco = decos[i];
            String deconame = deco.getName();
            if ((PyNames.CLASSMETHOD.equals(deconame) || PyNames.STATICMETHOD.equals(deconame)) && deco.isBuiltin()) {
              registerProblem(
                decos[i - 1],
                PyBundle.message("INSP.decorator.receives.unexpected.builtin"),
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING
              );
            }
          }
        }
      }
    }
  }
}
