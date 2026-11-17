// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.controlflow;

import com.jetbrains.python.impl.psi.types.PyTypeUtil;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.types.PyNeverType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.language.controlFlow.ConditionalInstruction;
import consulo.language.controlFlow.ControlFlow;
import consulo.language.controlFlow.Instruction;
import consulo.language.psi.PsiElement;
import consulo.util.lang.ThreeState;
import consulo.util.lang.ref.Ref;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

public class PyDataFlow implements ControlFlow {
    private final PyControlFlow myControlFlow;
    private final Reachability[] myReachability;

    public PyDataFlow(PyControlFlow controlFlow, FlowContext context) {
        myControlFlow = controlFlow;
        Instruction[] instructions = getInstructions();
        boolean[] reachabilityStrict = computeReachability(instructions, false, context);
        boolean[] reachabilitySoft = computeReachability(instructions, true, context);

        myReachability = new Reachability[instructions.length];
        for (int i = 0; i < instructions.length; i++) {
            if (reachabilityStrict[i]) {
                assert reachabilitySoft[i];
                myReachability[i] = Reachability.REACHABLE;
            }
            else if (reachabilitySoft[i]) {
                myReachability[i] = Reachability.UNREACHABLE_FOR_TYPE_CHECKING;
            }
            else {
                myReachability[i] = Reachability.UNREACHABLE;
            }
        }
    }

    @Override
    public Instruction[] getInstructions() {
        return myControlFlow.getInstructions();
    }

    private boolean[] computeReachability(Instruction[] instructions,
                                          boolean includeUnreachableForTypeChecking,
                                          FlowContext context) {
        boolean[] reachability = new boolean[instructions.length];
        Deque<Instruction> stack = new ArrayDeque<>();
        stack.push(instructions[0]);

        while (!stack.isEmpty()) {
            Instruction instruction = stack.pop();
            int instructionNum = instruction.num();

            if (reachability[instructionNum]) {
                continue; // Already visited
            }

            reachability[instructionNum] = true;

            for (Instruction successor : getReachableSuccessors(instruction, includeUnreachableForTypeChecking, context)) {
                if (!reachability[successor.num()]) {
                    stack.push(successor);
                }
            }
        }
        return reachability;
    }

    private Collection<Instruction> getReachableSuccessors(Instruction instruction,
                                                           boolean includeUnreachableForTypeChecking,
                                                           FlowContext context) {
        if (context.getCheckNoReturnCalls() &&
            instruction instanceof CallInstruction callInstruction &&
            callInstruction.isNoReturnCall(context.getTypeEvalContext())) {
            return Collections.emptyList();
        }
        if (instruction instanceof PyWithContextExitInstruction withContextExitInstruction &&
            !withContextExitInstruction.isSuppressingExceptions(context.getTypeEvalContext())) {
            return Collections.emptyList();
        }
        if (context.getEvaluateConditionTypes() &&
            instruction instanceof ConditionalInstruction conditionalInstruction &&
            isContradictedByConditionType(conditionalInstruction, context.getTypeEvalContext())) {
            return Collections.emptyList();
        }
        List<Instruction> result = new ArrayList<>();
        for (Instruction it : instruction.allSucc()) {
            if (it instanceof PyUnreachableInstruction unreachableInstruction) {
                if (includeUnreachableForTypeChecking && unreachableInstruction.isUnreachableForTypeChecking()) {
                    result.add(it);
                }
                continue;
            }
            if (it instanceof ReadWriteInstruction readWriteInstruction && readWriteInstruction.getAccess().isAssertTypeAccess()) {
                Ref<PyType> type = readWriteInstruction.getType(context.getTypeEvalContext(), null);
                if (!(type != null && type.get() instanceof PyNeverType)) {
                    result.add(it);
                }
                continue;
            }
            result.add(it);
        }
        return result;
    }

    /**
     * Returns {@code true} if the static type of the condition guarantees that the branch corresponding to
     * {@code instruction} can never be taken, e.g. the {@code then} branch of {@code if a:} where {@code a} is always falsy
     * (its {@code __bool__} returns a false literal).
     */
    private static boolean isContradictedByConditionType(ConditionalInstruction instruction, TypeEvalContext context) {
        if (!(instruction.getCondition() instanceof PyExpression condition)) {
            return false;
        }
        ThreeState truthiness = PyTypeUtil.getTypeTruthiness(context.getType(condition), condition, context);
        if (truthiness == ThreeState.UNSURE) {
            return false;
        }
        return instruction.getResult() != (truthiness == ThreeState.YES);
    }

    public Reachability getReachability(Instruction instruction) {
        if (instruction.num() >= myReachability.length) {
            return Reachability.REACHABLE;
        }
        return myReachability[instruction.num()];
    }

    public int getInstruction(PsiElement element) {
        return myControlFlow.getInstruction(element);
    }
}
