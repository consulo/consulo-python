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

import com.google.common.collect.Lists;
import com.jetbrains.python.impl.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.inspections.quickfix.PyChangeBaseClassQuickFix;
import com.jetbrains.python.impl.inspections.quickfix.PyConvertToNewStyleQuickFix;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.PyClassType;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.ast.ASTNode;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nls;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * User: catherine
 * <p>
 * Inspection to detect occurrences of new-style class features in old-style classes
 */
@ExtensionImpl
public class PyOldStyleClassesInspection extends PyInspection {
  @Nls
  @Nonnull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.oldstyle.class");
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
    public void visitPyClass(final PyClass node) {
      final List<PyClassLikeType> expressions = node.getSuperClassTypes(myTypeEvalContext);
      List<LocalQuickFix> quickFixes = Lists.<LocalQuickFix>newArrayList(new PyConvertToNewStyleQuickFix());
      if (!expressions.isEmpty()) {
        quickFixes.add(new PyChangeBaseClassQuickFix());
      }
      if (!node.isNewStyleClass(myTypeEvalContext)) {
        for (PyTargetExpression attr : node.getClassAttributes()) {
          if (PyNames.SLOTS.equals(attr.getName())) {
            registerProblem(attr,
                            PyBundle.message("INSP.oldstyle.class.slots"),
                            ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                            null,
                            quickFixes.toArray(new LocalQuickFix[quickFixes.size
                              ()]));
          }
        }
        for (PyFunction attr : node.getMethods()) {
          if (PyNames.GETATTRIBUTE.equals(attr.getName())) {
            final ASTNode nameNode = attr.getNameNode();
            assert nameNode != null;
            registerProblem(nameNode.getPsi(),
                            PyBundle.message("INSP.oldstyle.class.getattribute"),
                            ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                            null,
                            quickFixes.toArray(new
                                                 LocalQuickFix[quickFixes.size()]));
          }
        }
      }
    }

    @Override
    public void visitPyCallExpression(final PyCallExpression node) {
      PyClass klass = PsiTreeUtil.getParentOfType(node, PyClass.class);
      if (klass != null && !klass.isNewStyleClass(myTypeEvalContext)) {
        final List<PyClassLikeType> types = klass.getSuperClassTypes(myTypeEvalContext);
        for (PyClassLikeType type : types) {
          if (type == null) {
            return;
          }
          final String qName = type.getClassQName();
          if (qName != null && qName.contains("PyQt")) {
            return;
          }
          if (!(type instanceof PyClassType)) {
            return;
          }
        }
        List<LocalQuickFix> quickFixes = Lists.<LocalQuickFix>newArrayList(new PyConvertToNewStyleQuickFix());
        if (!types.isEmpty()) {
          quickFixes.add(new PyChangeBaseClassQuickFix());
        }

        if (PyUtil.isSuperCall(node)) {
          final PyExpression callee = node.getCallee();
          if (callee != null) {
            registerProblem(callee,
                            PyBundle.message("INSP.oldstyle.class.super"),
                            ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                            null,
                            quickFixes.toArray(quickFixes.toArray(new
                                                                    LocalQuickFix[quickFixes.size()])));
          }
        }
      }
    }
  }
}
