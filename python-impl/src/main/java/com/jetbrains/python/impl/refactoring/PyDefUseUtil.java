// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.refactoring;

import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.codeInsight.controlflow.CallInstruction;
import com.jetbrains.python.impl.codeInsight.controlflow.PyControlFlow;
import com.jetbrains.python.impl.codeInsight.controlflow.PyWithContextExitInstruction;
import com.jetbrains.python.impl.codeInsight.controlflow.ReadWriteInstruction;
import com.jetbrains.python.impl.psi.impl.PyAugAssignmentStatementNavigator;
import com.jetbrains.python.impl.psi.types.PyNarrowedType;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyImplicitImportNameDefiner;
import com.jetbrains.python.psi.PyImportedNameDefiner;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.PyTypedElement;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.application.progress.ProgressManager;
import consulo.language.controlFlow.ConditionalInstruction;
import consulo.language.controlFlow.ControlFlow;
import consulo.language.controlFlow.ControlFlowUtil;
import consulo.language.controlFlow.Instruction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.QualifiedName;
import consulo.util.lang.Comparing;
import consulo.util.lang.ref.Ref;
import consulo.util.collection.primitive.ints.IntStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static com.jetbrains.python.impl.codeInsight.controlflow.ControlFlowCache.getControlFlow;
import static com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil.getScopeOwner;

/**
 * @author Dennis.Ushakov
 */
public final class PyDefUseUtil {
    // For very large control flows we keep using a lighter, project-cached context for the narrowing-detection lookup
    // below to avoid blowing the stack when re-entering type inference from inside a deep CFG walk (PY-73958).
    private static final int MAX_CONTROL_FLOW_SIZE = 200;

    private PyDefUseUtil() {
    }

    public static LatestDefsResult getLatestDefs(ScopeOwner block,
                                                 String varName,
                                                 PsiElement anchor,
                                                 boolean acceptTypeAssertions,
                                                 boolean acceptImplicitImports,
                                                 TypeEvalContext context) {
        return getLatestDefs(
            getControlFlow(block), block, varName, anchor, acceptTypeAssertions, acceptImplicitImports,
            context
        );
    }

    public static LatestDefsResult getLatestDefs(PyControlFlow controlFlow,
                                                 ScopeOwner scopeOwner,
                                                 String varName,
                                                 PsiElement anchor,
                                                 boolean acceptTypeAssertions,
                                                 boolean acceptImplicitImports,
                                                 TypeEvalContext context) {
        Instruction[] instructions = controlFlow.getInstructions();
        int startNum = findStartInstructionId(anchor, controlFlow, scopeOwner);
        if (startNum < 0) {
            return LatestDefsResult.EMPTY;
        }

        QualifiedName varQname = QualifiedName.fromDottedString(varName);

        Collection<Instruction> result = new LinkedHashSet<>();
        Map<@Nullable PyCallExpression, @Nullable ConditionalInstruction> pendingTypeGuard = new HashMap<>();
        Ref<Boolean> foundPrefixWrite = new Ref<>(false);
        Ref<Boolean> foundPrefixCall = new Ref<>(false);
        iteratePrev(startNum, controlFlow, instruction -> {
            if (instruction instanceof PyWithContextExitInstruction withContextExitInstruction) {
                if (!withContextExitInstruction.isSuppressingExceptions(context)) {
                    return ControlFlowUtil.Operation.CONTINUE;
                }
            }
            if (acceptTypeAssertions && instruction instanceof CallInstruction callInstruction) {
                ConditionalInstruction typeGuardInstruction = pendingTypeGuard.get(callInstruction.getElement());
                if (typeGuardInstruction != null) {
                    result.add(typeGuardInstruction);
                    return ControlFlowUtil.Operation.CONTINUE;
                }
                if (isNotBackEdge(callInstruction.num(), startNum) &&
                    context.getOrigin() == callInstruction.getElement().getContainingFile()) {
                    TypeEvalContext narrowingContext = chooseNarrowingContext(context, instructions.length);
                    if (callInstruction.isNoReturnCall(narrowingContext)) {
                        return ControlFlowUtil.Operation.CONTINUE;
                    }
                }
            }
            if (isNotBackEdge(instruction.num(), startNum)
                && acceptTypeAssertions && instruction instanceof ConditionalInstruction conditionalInstruction) {
                PsiElement typedElement = conditionalInstruction.getCondition();
                if (typedElement instanceof PyTypedElement pyTypedElement &&
                    context.getOrigin() == typedElement.getContainingFile()) {
                    TypeEvalContext narrowingContext = chooseNarrowingContext(context, instructions.length);
                    PyType narrowedType = narrowingContext.getType(pyTypedElement);
                    if (narrowedType instanceof PyNarrowedType pyNarrowedType && pyNarrowedType.isBound()) {
                        String narrowedQname = pyNarrowedType.getQname();
                        if (narrowedQname != null) {
                            if (isQualifiedBy(varQname, narrowedQname)) {
                                foundPrefixWrite.set(true);
                                return ControlFlowUtil.Operation.BREAK;
                            }

                            if (narrowedQname.equals(varName)) {
                                pendingTypeGuard.put(pyNarrowedType.getOriginal(), conditionalInstruction);
                            }
                        }
                    }
                }
            }
            // A call with prefix as receiver or argument (e.g. self.reset() or foo(self)) may mutate attributes, so soft-invalidate narrowing (PY-88265)
            if (instruction instanceof CallInstruction callInstruction) {
                if (isCallOnPrefix(callInstruction, varQname)) {
                    foundPrefixCall.set(true);
                }
            }
            if (instruction instanceof ReadWriteInstruction readWriteInstruction) {
                ReadWriteInstruction.ACCESS access = readWriteInstruction.getAccess();
                if (access.isWriteAccess() ||
                    acceptTypeAssertions && access.isAssertTypeAccess() && isNotBackEdge(readWriteInstruction.num(), startNum)) {
                    String name = readWriteInstruction.getName();

                    if (name != null && isQualifiedBy(varQname, name)) {
                        foundPrefixWrite.set(true);
                        return ControlFlowUtil.Operation.BREAK;
                    }

                    if (Comparing.strEqual(name, varName)) {
                        result.add(readWriteInstruction);
                        return ControlFlowUtil.Operation.CONTINUE;
                    }
                }
            }
            else if (acceptImplicitImports && instruction.getElement() instanceof PyImplicitImportNameDefiner implicit) {
                if (!implicit.multiResolveName(varName).isEmpty()) {
                    result.add(instruction);
                    return ControlFlowUtil.Operation.CONTINUE;
                }
            }
            return ControlFlowUtil.Operation.NEXT;
        });
        if (foundPrefixWrite.get()) {
            return LatestDefsResult.EMPTY;
        }
        return new LatestDefsResult(new ArrayList<>(result), foundPrefixCall.get());
    }

    /**
     * New analysis handles back edges separately.
     *
     * @see com.jetbrains.python.impl.psi.impl.PyReferenceExpressionImpl
     */
    private static boolean isNotBackEdge(int instNum, int startNum) {
        return instNum < startNum;
    }

    /**
     * Reuse the caller's context for narrowing detection so the AssumptionContext set up by the
     * fixpoint isn't dropped (PY-89245). Fall back to a lighter context for oversized CFGs to keep
     * the stack-overflow guard from PY-73958.
     */
    private static TypeEvalContext chooseNarrowingContext(TypeEvalContext context, int controlFlowSize) {
        if (controlFlowSize >= MAX_CONTROL_FLOW_SIZE) {
            PsiElement origin = context.getOrigin();
            if (origin != null) {
                return TypeEvalContext.codeInsightFallback(origin.getProject());
            }
        }
        return context;
    }

    private static boolean isCallOnPrefix(CallInstruction callInstr, QualifiedName varQname) {
        PyExpression receiver = callInstr.getElement().getCallee() instanceof PyQualifiedExpression qualifiedExpression
            ? qualifiedExpression.getQualifier()
            : null;
        if (isPrefixExpression(receiver, varQname)) {
            return true;
        }
        for (PyExpression arg : callInstr.getElement().getArguments()) {
            if (isPrefixExpression(arg, varQname)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPrefixExpression(@Nullable PyExpression expr, QualifiedName varQname) {
        if (expr instanceof PyQualifiedExpression qualifiedExpression) {
            QualifiedName exprQname = qualifiedExpression.asQualifiedName();
            return exprQname != null && varQname.getComponentCount() > exprQname.getComponentCount() && varQname.matchesPrefix(exprQname);
        }
        return false;
    }

    private static boolean isQualifiedBy(QualifiedName varQname, String qualifier) {
        QualifiedName elementQname = QualifiedName.fromDottedString(qualifier);
        return varQname.getComponentCount() > elementQname.getComponentCount() && varQname.matchesPrefix(elementQname);
    }

    private static int findStartInstructionId(PsiElement startAnchor, PyControlFlow flow, ScopeOwner scopeOwner) {
        PsiElement realCfgAnchor = startAnchor;
        PsiElement augAssignment = PyAugAssignmentStatementNavigator.getStatementByTarget(startAnchor);
        if (augAssignment != null) {
            realCfgAnchor = augAssignment;
        }
        int instr = -1;
        PsiElement element = realCfgAnchor;
        while (element != null && element != scopeOwner) {
            instr = flow.getInstruction(element);
            if (instr >= 0) {
                break;
            }
            element = element.getParent();
        }
        if (instr < 0) {
            return instr;
        }
        if (startAnchor instanceof PyTargetExpression) {
            Collection<Instruction> pred = flow.getInstructions()[instr].allPred();
            if (!pred.isEmpty()) {
                instr = pred.iterator().next().num();
            }
        }
        return instr;
    }

    /**
     * Modified copy of {@link ControlFlowUtil#iteratePrev} that uses
     * {@link PyControlFlow#getPrev} instead of {@link Instruction#allPred}
     */
    private static void iteratePrev(int startInstruction,
                                    PyControlFlow controlFlow,
                                    Function<Instruction, ControlFlowUtil.Operation> closure) {
        Instruction[] instructions = controlFlow.getInstructions();
        IntStack stack = new IntStack(instructions.length);
        boolean[] visited = new boolean[instructions.length];

        visited[startInstruction] = true;
        stack.push(startInstruction);
        int count = 0;
        while (!stack.isEmpty()) {
            count++;
            if (count % 512 == 0) {
                ProgressManager.checkCanceled();
            }
            int num = stack.pop();
            Instruction instr = instructions[num];
            ControlFlowUtil.Operation nextOperation = closure.apply(instr);
            // Just ignore previous instructions for the current node and move further
            if (nextOperation == ControlFlowUtil.Operation.CONTINUE) {
                continue;
            }
            // STOP iteration
            if (nextOperation == ControlFlowUtil.Operation.BREAK) {
                break;
            }
            // If we are here, we should process previous nodes in natural way
            assert nextOperation == ControlFlowUtil.Operation.NEXT;
            Collection<Instruction> nextToProcess = controlFlow.getPrev(instr);
            for (Instruction pred : nextToProcess) {
                int predNum = pred.num();
                if (!visited[predNum]) {
                    visited[predNum] = true;
                    stack.push(predNum);
                }
            }
        }
    }

    public static PsiElement[] getPostRefs(ScopeOwner block, PyTargetExpression var, @Nullable PyExpression anchor) {
        ControlFlow controlFlow = getControlFlow(block);
        Instruction[] instructions = controlFlow.getInstructions();
        int instr = ControlFlowUtil.findInstructionNumberByElement(instructions, anchor);
        if (instr < 0) {
            return PyElement.EMPTY_ARRAY;
        }
        boolean[] visited = new boolean[instructions.length];
        Collection<@Nullable PyElement> result = new HashSet<>();
        for (Instruction instruction : instructions[instr].allSucc()) {
            getPostRefs(var, instructions, instruction.num(), visited, result);
        }
        return result.toArray(PsiElement[]::new);
    }

    private static void getPostRefs(PyTargetExpression var,
                                    Instruction[] instructions,
                                    int instr,
                                    boolean[] visited,
                                    Collection<@Nullable PyElement> result) {
        // TODO: Use ControlFlowUtil.process() for forwards CFG traversal
        if (visited[instr]) {
            return;
        }
        visited[instr] = true;
        if (instructions[instr] instanceof ReadWriteInstruction instruction) {
            if (Comparing.strEqual(instruction.getName(), var.getName())) {
                ReadWriteInstruction.ACCESS access = instruction.getAccess();
                if (access.isWriteAccess()) {
                    return;
                }
                result.add((PyElement) instruction.getElement());
            }
        }
        for (Instruction instruction : instructions[instr].allSucc()) {
            getPostRefs(var, instructions, instruction.num(), visited, result);
        }
    }

    /**
     * Iterates back through instructions starting at the second element and looks for the first one.
     *
     * @return false for elements from different scopes, true if searched is defined/imported before target
     */
    public static boolean isDefinedBefore(PsiElement searched, PsiElement target) {
        ScopeOwner scopeOwner = getScopeOwner(searched);
        Ref<Boolean> definedBefore = new Ref<>(false);
        if (scopeOwner != null && scopeOwner == getScopeOwner(target)) {
            Instruction[] instructions = getControlFlow(scopeOwner).getInstructions();
            int index = ControlFlowUtil.findInstructionNumberByElement(instructions, target);
            if (index >= 0) {
                ControlFlowUtil.iteratePrev(index, instructions, instruction -> {
                    if (instruction.getElement() == searched) {
                        boolean isImport = searched instanceof PyImportedNameDefiner;
                        boolean isWriteAccess =
                            instruction instanceof ReadWriteInstruction readWriteInstruction && readWriteInstruction.getAccess().isWriteAccess();
                        if (isImport || isWriteAccess) {
                            definedBefore.set(true);
                            return ControlFlowUtil.Operation.BREAK;
                        }
                    }
                    return ControlFlowUtil.Operation.NEXT;
                });
            }
        }
        return definedBefore.get();
    }

    public record LatestDefsResult(List<Instruction> defs, boolean foundPrefixCall) {
        public static final LatestDefsResult EMPTY = new LatestDefsResult(List.of(), false);
    }

    public static class InstructionNotFoundException extends RuntimeException {
    }
}
