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
import consulo.annotation.access.RequiredReadAction;
import consulo.codeEditor.Editor;
import consulo.externalService.statistic.FeatureUsageTracker;
import consulo.ide.impl.idea.codeInsight.highlighting.HighlightUsagesHandler;
import consulo.language.controlFlow.ControlFlow;
import consulo.language.controlFlow.Instruction;
import consulo.language.editor.highlight.usage.HighlightUsagesHandlerBase;
import consulo.language.editor.localize.CodeInsightLocalize;
import consulo.language.editor.util.ProductivityFeatureNames;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import org.jspecify.annotations.Nullable;

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

    public PyHighlightExitPointsHandler(Editor editor, PsiFile file, PsiElement target) {
        super(editor, file);
        myTarget = target;
    }

    @Override
    @RequiredReadAction
    public List<PsiElement> getTargets() {
        return Collections.singletonList(myTarget);
    }

    @Override
    protected void selectTargets(List<PsiElement> targets, Consumer<List<PsiElement>> selectionConsumer) {
        selectionConsumer.accept(targets);
    }

    @Override
    @RequiredReadAction
    public void computeUsages(List<PsiElement> targets) {
        FeatureUsageTracker.getInstance().triggerFeatureUsed(ProductivityFeatureNames.CODEASSISTS_HIGHLIGHT_RETURN);

        PsiElement parent = myTarget.getParent();
        if (!(parent instanceof PyReturnStatement)) {
            return;
        }

        PyFunction function = PsiTreeUtil.getParentOfType(myTarget, PyFunction.class);
        if (function == null) {
            return;
        }

        highlightExitPoints((PyReturnStatement) parent, function);
    }

    @Nullable
    private static PsiElement getExitTarget(PsiElement exitStatement) {
        if (exitStatement instanceof PyReturnStatement returnStmt) {
            return PsiTreeUtil.getParentOfType(returnStmt, PyFunction.class);
        }
        else if (exitStatement instanceof PyBreakStatement breakStmt) {
            return breakStmt.getLoopStatement();
        }
        else if (exitStatement instanceof PyContinueStatement continueStmt) {
            return continueStmt.getLoopStatement();
        }
        else if (exitStatement instanceof PyRaiseStatement) {
            // TODO[oleg]: Implement better logic here!
            return null;
        }
        return null;
    }

    @RequiredReadAction
    private void highlightExitPoints(PyReturnStatement statement, PyFunction function) {
        ControlFlow flow = ControlFlowCache.getControlFlow(function);
        Collection<PsiElement> exitStatements = findExitPointsAndStatements(flow);
        if (!exitStatements.contains(statement)) {
            return;
        }

        PsiElement originalTarget = getExitTarget(statement);
        for (PsiElement exitStatement : exitStatements) {
            if (getExitTarget(exitStatement) == originalTarget) {
                addOccurrence(exitStatement);
            }
        }
        myStatusText = CodeInsightLocalize.statusBarExitPointsHighlightedMessage(
            exitStatements.size(),
            HighlightUsagesHandler.getShortcutText()
        );
    }

    private static Collection<PsiElement> findExitPointsAndStatements(ControlFlow flow) {
        List<PsiElement> statements = new ArrayList<>();
        Instruction[] instructions = flow.getInstructions();
        for (Instruction instruction : instructions[instructions.length - 1].allPred()) {
            PsiElement element = instruction.getElement();
            if (element == null) {
                continue;
            }
            PsiElement statement = PyPsiUtils.getStatement(element);
            if (statement != null) {
                statements.add(statement);
            }
        }
        return statements;
    }
}
