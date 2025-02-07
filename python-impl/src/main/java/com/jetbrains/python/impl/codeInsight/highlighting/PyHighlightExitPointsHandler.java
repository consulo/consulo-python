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

package com.jetbrains.python.impl.codeInsight.highlighting;

import com.jetbrains.python.impl.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import consulo.codeEditor.Editor;
import consulo.externalService.statistic.FeatureUsageTracker;
import consulo.ide.impl.idea.codeInsight.highlighting.HighlightUsagesHandler;
import consulo.language.controlFlow.ControlFlow;
import consulo.language.controlFlow.Instruction;
import consulo.language.editor.CodeInsightBundle;
import consulo.language.editor.highlight.usage.HighlightUsagesHandlerBase;
import consulo.language.editor.util.ProductivityFeatureNames;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * @author oleg
 */
public class PyHighlightExitPointsHandler extends HighlightUsagesHandlerBase<PsiElement> {
  private final PsiElement myTarget;

  public PyHighlightExitPointsHandler(final Editor editor, final PsiFile file, final PsiElement target) {
    super(editor, file);
    myTarget = target;
  }

  public List<PsiElement> getTargets() {
    return Collections.singletonList(myTarget);
  }

  protected void selectTargets(final List<PsiElement> targets, final Consumer<List<PsiElement>> selectionConsumer) {
    selectionConsumer.accept(targets);
  }

  public void computeUsages(final List<PsiElement> targets) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed(ProductivityFeatureNames.CODEASSISTS_HIGHLIGHT_RETURN);

    final PsiElement parent = myTarget.getParent();
    if (!(parent instanceof PyReturnStatement)) {
      return;
    }

    final PyFunction function = PsiTreeUtil.getParentOfType(myTarget, PyFunction.class);
    if (function == null) {
      return;
    }

    highlightExitPoints((PyReturnStatement)parent, function);
  }

  @Nullable
  private static PsiElement getExitTarget(PsiElement exitStatement) {
    if (exitStatement instanceof PyReturnStatement) {
      return PsiTreeUtil.getParentOfType(exitStatement, PyFunction.class);
    }
    else if (exitStatement instanceof PyBreakStatement) {
      return ((PyBreakStatement)exitStatement).getLoopStatement();
    }
    else if (exitStatement instanceof PyContinueStatement) {
      return ((PyContinueStatement)exitStatement).getLoopStatement();
    }
    else if (exitStatement instanceof PyRaiseStatement) {
      // TODO[oleg]: Implement better logic here!
      return null;
    }
    return null;
  }

  private void highlightExitPoints(final PyReturnStatement statement,
                                   final PyFunction function) {
    final ControlFlow flow = ControlFlowCache.getControlFlow(function);
    final Collection<PsiElement> exitStatements = findExitPointsAndStatements(flow);
    if (!exitStatements.contains(statement)) {
      return;
    }

    final PsiElement originalTarget = getExitTarget(statement);
    for (PsiElement exitStatement : exitStatements) {
      if (getExitTarget(exitStatement) == originalTarget) {
        addOccurrence(exitStatement);
      }
    }
    myStatusText = CodeInsightBundle.message("status.bar.exit.points.highlighted.message",
                                             exitStatements.size(),
                                             HighlightUsagesHandler.getShortcutText());
  }

  private static Collection<PsiElement> findExitPointsAndStatements(final ControlFlow flow) {
    final List<PsiElement> statements = new ArrayList<PsiElement>();
    final Instruction[] instructions = flow.getInstructions();
    for (Instruction instruction : instructions[instructions.length - 1].allPred()){
      final PsiElement element = instruction.getElement();
      if (element == null){
        continue;
      }
      final PsiElement statement = PyPsiUtils.getStatement(element);
      if (statement != null){
        statements.add(statement);
      }
    }
    return statements; 
  }
}
