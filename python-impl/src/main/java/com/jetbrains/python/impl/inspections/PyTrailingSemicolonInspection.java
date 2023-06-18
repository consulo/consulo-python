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
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.impl.inspections.quickfix.RemoveTrailingSemicolonQuickFix;
import com.jetbrains.python.psi.PyStatement;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.ast.ASTNode;
import consulo.language.ast.TokenSet;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiWhiteSpace;
import org.jetbrains.annotations.Nls;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author Alexey.Ivanov
 */
@ExtensionImpl
public class PyTrailingSemicolonInspection extends PyInspection {
  @Nls
  @Nonnull
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.trailing.semicolon");
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
    public void visitPyStatement(final PyStatement statement) {
      ASTNode statementNode = statement.getNode();
      if (statementNode != null) {
        ASTNode[] nodes = statementNode.getChildren(TokenSet.create(PyTokenTypes.SEMICOLON));
        for (ASTNode node : nodes) {
          ASTNode nextNode = statementNode.getTreeNext();
          while ((nextNode instanceof PsiWhiteSpace) && (!nextNode.textContains('\n'))) {
            nextNode = nextNode.getTreeNext();
          }
          if (nextNode == null || nextNode.textContains('\n')) {
            registerProblem(node.getPsi(), "Trailing semicolon in the statement", new RemoveTrailingSemicolonQuickFix());
          }
        }
      }
    }
  }
}
