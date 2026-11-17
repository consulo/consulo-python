// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.dataflow;

import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.codeInsight.controlflow.CallInstruction;
import com.jetbrains.python.impl.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.impl.codeInsight.controlflow.FlowContext;
import com.jetbrains.python.impl.codeInsight.controlflow.Reachability;
import com.jetbrains.python.impl.codeInsight.controlflow.ReadWriteInstruction;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeVariable;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.impl.ScopeVariableImpl;
import com.jetbrains.python.impl.psi.impl.PyExceptPartNavigator;
import com.jetbrains.python.psi.PyExceptPart;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyTypeDeclarationStatement;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.language.controlFlow.Instruction;
import consulo.language.dataFlow.map.DFAMap;
import consulo.language.dataFlow.map.DfaMapInstance;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import org.jspecify.annotations.Nullable;

import java.util.Map;

public class PyReachingDefsDfaInstance implements DfaMapInstance<ScopeVariable> {
    // Use this its own map, because check in PyReachingDefsDfaSemilattice is important
    public static final DFAMap<ScopeVariable> INITIAL_MAP = new DFAMap<>();
    public static final DFAMap<ScopeVariable> UNREACHABLE_MARKER = new DFAMap<>();

    private final TypeEvalContext myContext;

    public PyReachingDefsDfaInstance(TypeEvalContext typeEvalContext) {
        myContext = typeEvalContext;
    }

    @Override
    public DFAMap<ScopeVariable> fun(DFAMap<ScopeVariable> map, Instruction instruction) {
        if (map == UNREACHABLE_MARKER) {
            return map;
        }
        PsiElement element = instruction.getElement();
        if (element == null || ((PyFile) element.getContainingFile()).getLanguageLevel().isPython2()) {
            return processReducedMap(map, instruction, element);
        }
        FlowContext context = new FlowContext(myContext, true);
        ScopeOwner scopeOwner = ScopeUtil.getScopeOwner(element);
        if (scopeOwner != null && ControlFlowCache.getDataFlow(scopeOwner, context).getReachability(instruction) != Reachability.REACHABLE) {
            return UNREACHABLE_MARKER;
        }
        if (instruction instanceof CallInstruction callInstruction) {
            if (callInstruction.isNoReturnCall(myContext)) {
                return UNREACHABLE_MARKER;
            }
        }
        // Scope reduction
        DFAMap<ScopeVariable> reducedMap = new DFAMap<>();
        for (Map.Entry<String, ScopeVariable> entry : map.entrySet()) {
            ScopeVariable value = entry.getValue();
            // Support PEP-3110. (PY-1408)
            if (value.isParameter()) {
                PsiElement declaration = value.getDeclarations().iterator().next();
                PyExceptPart exceptPart = PyExceptPartNavigator.getPyExceptPartByTarget(declaration);
                if (exceptPart != null) {
                    if (!PsiTreeUtil.isAncestor(exceptPart, element, false)) {
                        continue;
                    }
                }
            }
            reducedMap.put(entry.getKey(), value);
        }

        return processReducedMap(reducedMap, instruction, element);
    }

    private static DFAMap<ScopeVariable> processReducedMap(DFAMap<ScopeVariable> map,
                                                           Instruction instruction,
                                                           @Nullable PsiElement element) {
        if (element != null && element.getParent() instanceof PyTypeDeclarationStatement) {
            return map;
        }

        DFAMap<ScopeVariable> result = map;
        String name = null;
        // Process readwrite instruction
        if (instruction instanceof ReadWriteInstruction rwInstruction) {
            if (rwInstruction.getAccess().isWriteAccess()) {
                name = rwInstruction.getName();
            }
            else if (rwInstruction.getAccess().isDeleteAccess() && instruction.getElement() instanceof PyReferenceExpression) {
                result = result.asWritable();
                result.remove(rwInstruction.getName());
                return result;
            }
        }
        // Processing PyFunction
        else if (element instanceof PyFunction function) {
            name = function.getName();
        }
        if (name == null) {
            return result;
        }
        ScopeVariable variable = result.get(name);

        // Parameter case
        PsiElement parameterScope = ScopeUtil.getParameterScope(element);
        if (parameterScope != null) {
            ScopeVariable scopeVariable = new ScopeVariableImpl(name, true, element);
            result = result.asWritable();
            result.put(name, scopeVariable);
        }
        // Local variable case
        else {
            ScopeVariableImpl scopeVariable;
            boolean isParameter = variable != null && variable.isParameter();
            if (variable == null) {
                scopeVariable = new ScopeVariableImpl(name, isParameter, element);
            }
            else {
                scopeVariable = new ScopeVariableImpl(name, isParameter, variable.getDeclarations());
            }
            result = result.asWritable();
            result.put(name, scopeVariable);
        }
        return result;
    }

    @Override
    public DFAMap<ScopeVariable> initial() {
        return INITIAL_MAP;
    }

    // TODO consulo: DfaInstance without isForward() missing, platform still declares it
    @Override
    public boolean isForward() {
        return true;
    }
}
