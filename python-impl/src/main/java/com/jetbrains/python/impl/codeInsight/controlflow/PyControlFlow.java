// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.controlflow;

import com.jetbrains.python.psi.PyImplicitImportNameDefiner;
import consulo.application.progress.ProgressManager;
import consulo.language.controlFlow.ConditionalInstruction;
import consulo.language.controlFlow.ControlFlow;
import consulo.language.controlFlow.Instruction;
import consulo.language.psi.PsiElement;
import consulo.util.collection.primitive.ints.IntList;
import consulo.util.collection.primitive.ints.IntLists;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PyControlFlow implements ControlFlow {
    private final Instruction[] myInstructions;
    private final Map<PsiElement, Integer> myElementToInstructionMap;
    private final Collection<Instruction>[] myPredecessors;

    public PyControlFlow(Instruction[] instructions) {
        myInstructions = instructions;
        myElementToInstructionMap = buildElementToInstructionMap(instructions);
        myPredecessors = buildPredecessors(instructions);
    }

    public int getInstruction(PsiElement element) {
        Integer index = myElementToInstructionMap.get(element);
        return index != null ? index : -1;
    }

    public Collection<Instruction> getPrev(Instruction instruction) {
        return myPredecessors[instruction.num()];
    }

    @Override
    public Instruction[] getInstructions() {
        return myInstructions;
    }

    private static Map<PsiElement, Integer> buildElementToInstructionMap(Instruction[] instructions) {
        Map<PsiElement, Integer> result = new HashMap<>();
        for (Instruction instruction : instructions) {
            result.putIfAbsent(instruction.getElement(), instruction.num());
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Collection<Instruction>[] buildPredecessors(Instruction[] instructions) {
        @Nullable IntList[] predecessors = new IntList[instructions.length];
        for (Instruction instruction : instructions) {
            ProgressManager.checkCanceled();
            if (skip(instruction)) {
                continue;
            }

            for (Instruction successor : instruction.allSucc()) {
                Instruction current = successor;
                while (true) {
                    addEdge(predecessors, instruction.num(), current.num());
                    if (!skip(current)) {
                        break;
                    }
                    Collection<Instruction> successors = current.allSucc();
                    if (successors.size() != 1) {
                        throw new IllegalArgumentException("Collection has " + successors.size() + " elements");
                    }
                    current = successors.iterator().next();
                }
            }
        }

        Collection<Instruction>[] result = new Collection[predecessors.length];
        for (int index = 0; index < predecessors.length; index++) {
            ProgressManager.checkCanceled();
            IntList list = predecessors[index];
            if (list != null) {
                list.sort();
                IntLists.reverse(list);
                List<Instruction> mapped = new ArrayList<>(list.size());
                for (int i = 0; i < list.size(); i++) {
                    mapped.add(instructions[list.get(i)]);
                }
                result[index] = mapped;
            }
            else {
                result[index] = Collections.emptyList();
            }
        }
        return result;
    }

    private static void addEdge(@Nullable IntList[] predecessors, int from, int to) {
        IntList list = predecessors[to];
        if (list == null) {
            list = IntLists.newArrayList(1);
            predecessors[to] = list;
        }
        list.add(from);
    }

    private static boolean skip(Instruction instruction) {
        if (instruction instanceof ConditionalInstruction) {
            return false;
        }
        if (instruction instanceof CallInstruction) {
            return false;
        }
        if (instruction instanceof PyFinallyFailExitInstruction) {
            return false;
        }
        if (instruction instanceof PyRaiseInstruction) {
            return false;
        }
        if (instruction instanceof PyWithContextExitInstruction) {
            return false;
        }
        if (instruction instanceof RefutablePatternInstruction) {
            return false;
        }
        if (instruction instanceof ReadWriteInstruction readWriteInstruction &&
            readWriteInstruction.getAccess() != ReadWriteInstruction.ACCESS.READ) {
            return false;
        }
        if (instruction.getElement() instanceof PyImplicitImportNameDefiner) {
            return false;
        }
        return instruction.allPred().size() == 1 && instruction.allSucc().size() == 1;
    }
}
