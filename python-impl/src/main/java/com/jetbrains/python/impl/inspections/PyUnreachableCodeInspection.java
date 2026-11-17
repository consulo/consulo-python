// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.impl.codeInsight.controlflow.Reachability;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.PyStatementList;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.controlFlow.ControlFlow;
import consulo.language.controlFlow.ControlFlowUtil;
import consulo.language.controlFlow.Instruction;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.lang.ref.SimpleReference;

import static com.jetbrains.python.impl.codeInsight.controlflow.PyDataFlowKt.getReachabilityForInspection;

/**
 * Detects unreachable code using the control flow graph
 */
@ExtensionImpl
public class PyUnreachableCodeInspection extends PyInspection {
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameUnreachableCode();
    }

    @Override
    public LocalizeValue getGroupDisplayName() {
        return PyLocalize.inspGroupPythonControlFlowIssues();
    }

    @Override
    public PsiElementVisitor buildVisitor(ProblemsHolder holder,
                                          boolean isOnTheFly,
                                          LocalInspectionToolSession session,
                                          Object state) {
        TypeEvalContext context = PyInspectionVisitor.getContext(session);
        if (context.getUsesExternalTypeEngine()) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        return new PyInspectionVisitor(holder, context) {
            @Override
            public void visitPyStatementList(PyStatementList node) {
                if (getReachabilityForInspection(node.getParent(), myTypeEvalContext) == Reachability.UNREACHABLE) {
                    return;
                }
                if (getReachabilityForInspection(node, myTypeEvalContext) == Reachability.UNREACHABLE) {
                    registerProblem(node, PyLocalize.inspUnreachableCode().get());
                }
            }

            @Override
            public void visitPyStatement(PyStatement node) {
                if (getReachabilityForInspection(node.getParent(), myTypeEvalContext) == Reachability.UNREACHABLE) {
                    return;
                }
                if (getReachabilityForInspection(node, myTypeEvalContext) == Reachability.UNREACHABLE) {
                    registerProblem(node, PyLocalize.inspUnreachableCode().get());
                }
            }
        };
    }

    /**
     * Consulo: not present in JB anymore, still used by the Consulo copy of PyUnresolvedReferencesInspection
     * until it is synced with JB {@code PyUnresolvedReferencesVisitor}.
     */
    public static boolean hasAnyInterruptedControlFlowPaths(PsiElement element) {
        ScopeOwner owner = ScopeUtil.getScopeOwner(element);
        if (owner != null) {
            ControlFlow flow = ControlFlowCache.getControlFlow(owner);
            Instruction[] instructions = flow.getInstructions();
            int start = ControlFlowUtil.findInstructionNumberByElement(instructions, element);
            if (start >= 0) {
                SimpleReference<Boolean> resultRef = SimpleReference.create(false);
                ControlFlowUtil.iteratePrev(start, instructions, instruction -> {
                    if (instruction.allPred().isEmpty() && !isFirstInstruction(instruction)) {
                        resultRef.set(true);
                        return ControlFlowUtil.Operation.BREAK;
                    }
                    return ControlFlowUtil.Operation.NEXT;
                });
                return resultRef.get();
            }
        }
        return false;
    }

    private static boolean isFirstInstruction(Instruction instruction) {
        return instruction.num() == 0;
    }
}
