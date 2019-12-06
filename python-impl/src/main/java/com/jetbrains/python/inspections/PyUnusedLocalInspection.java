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

package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import consulo.util.dataholder.Key;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.PyBundle;
import org.jetbrains.annotations.Nls;
import javax.annotation.Nonnull;

import javax.swing.*;

/**
 * @author oleg
 */
public class PyUnusedLocalInspection extends PyInspection {
  private static Key<PyUnusedLocalInspectionVisitor> KEY = Key.create("PyUnusedLocal.Visitor");

  public boolean ignoreTupleUnpacking = true;
  public boolean ignoreLambdaParameters = true;
  public boolean ignoreLoopIterationVariables = true;

  @Nonnull
  @Nls
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.unused");
  }

  @Nonnull
  public PsiElementVisitor buildVisitor(@Nonnull ProblemsHolder holder,
                                        final boolean isOnTheFly,
                                        @Nonnull LocalInspectionToolSession session) {
    final PyUnusedLocalInspectionVisitor visitor = new PyUnusedLocalInspectionVisitor(holder,
                                                                                      session,
                                                                                      ignoreTupleUnpacking,
                                                                                      ignoreLambdaParameters,
                                                                                      ignoreLoopIterationVariables);
    // buildVisitor() will be called on injected files in the same session - don't overwrite if we already have one
    final PyUnusedLocalInspectionVisitor existingVisitor = session.getUserData(KEY);
    if (existingVisitor == null) {
      session.putUserData(KEY, visitor);
    }
    return visitor;
  }

  @Override
  public void inspectionFinished(@Nonnull LocalInspectionToolSession session, @Nonnull ProblemsHolder holder) {
    final PyUnusedLocalInspectionVisitor visitor = session.getUserData(KEY);
    if (visitor != null) {
      visitor.registerProblems();
      session.putUserData(KEY, null);
    }
  }

  @Override
  public JComponent createOptionsPanel() {
    MultipleCheckboxOptionsPanel panel = new MultipleCheckboxOptionsPanel(this);
    panel.addCheckbox("Ignore variables used in tuple unpacking", "ignoreTupleUnpacking");
    panel.addCheckbox("Ignore lambda parameters", "ignoreLambdaParameters");
    panel.addCheckbox("Ignore range iteration variables", "ignoreLoopIterationVariables");
    return panel;
  }
}
