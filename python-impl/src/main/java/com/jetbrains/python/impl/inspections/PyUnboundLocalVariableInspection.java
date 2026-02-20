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

import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.impl.codeInsight.controlflow.ReadWriteInstruction;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.Scope;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeVariable;
import com.jetbrains.python.impl.inspections.quickfix.AddGlobalQuickFix;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import com.jetbrains.python.impl.psi.impl.PyGlobalStatementNavigator;
import com.jetbrains.python.psi.*;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.controlFlow.ControlFlow;
import consulo.language.controlFlow.ControlFlowUtil;
import consulo.language.controlFlow.Instruction;
import consulo.language.dataFlow.DFALimitExceededException;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiPolyVariantReference;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.dataholder.Key;
import consulo.util.lang.ref.Ref;
import jakarta.annotation.Nonnull;

import java.util.HashSet;
import java.util.Set;

/**
 * @author oleg
 */
@ExtensionImpl
public class PyUnboundLocalVariableInspection extends PyInspection {
    private static Key<Set<ScopeOwner>> LARGE_FUNCTIONS_KEY = Key.create("PyUnboundLocalVariableInspection.LargeFunctions");

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameUnbound();
    }

    @Nonnull
    public PsiElementVisitor buildVisitor(
        @Nonnull ProblemsHolder holder,
        boolean isOnTheFly,
        @Nonnull LocalInspectionToolSession session
    ) {
        session.putUserData(LARGE_FUNCTIONS_KEY, new HashSet<>());
        return new Visitor(holder, session);
    }

    public static class Visitor extends PyInspectionVisitor {

        public Visitor(ProblemsHolder holder, LocalInspectionToolSession session) {
            super(holder, session);
        }

        @Override
        public void visitPyReferenceExpression(PyReferenceExpression node) {
            if (node.getContainingFile() instanceof PyExpressionCodeFragment) {
                return;
            }
            // Ignore global statements arguments
            if (PyGlobalStatementNavigator.getByArgument(node) != null) {
                return;
            }
            // Ignore qualifier inspections
            if (node.isQualified()) {
                return;
            }
            // Ignore import subelements
            if (PsiTreeUtil.getParentOfType(node, PyImportStatementBase.class) != null) {
                return;
            }
            String name = node.getReferencedName();
            if (name == null) {
                return;
            }
            ScopeOwner owner = ScopeUtil.getDeclarationScopeOwner(node, name);
            Set<ScopeOwner> largeFunctions = getSession().getUserData(LARGE_FUNCTIONS_KEY);
            assert largeFunctions != null;
            if (owner == null || largeFunctions.contains(owner)) {
                return;
            }
            // Ignore references declared in outer scopes
            if (owner != ScopeUtil.getScopeOwner(node)) {
                return;
            }
            Scope scope = ControlFlowCache.getScope(owner);
            // Ignore globals and if scope even doesn't contain such a declaration
            if (scope.isGlobal(name) || (!scope.containsDeclaration(name))) {
                return;
            }
            // Start DFA from the assignment statement in case of augmented assignments
            PsiElement anchor;
            PyAugAssignmentStatement augAssignment = PsiTreeUtil.getParentOfType(node, PyAugAssignmentStatement.class);
            if (augAssignment != null && name.equals(augAssignment.getTarget().getName())) {
                anchor = augAssignment;
            }
            else {
                anchor = node;
            }
            ScopeVariable variable;
            try {
                variable = scope.getDeclaredVariable(anchor, name);
            }
            catch (DFALimitExceededException e) {
                largeFunctions.add(owner);
                registerLargeFunction(owner);
                return;
            }
            if (variable == null) {
                if (!isFirstUnboundRead(node, owner)) {
                    return;
                }
                PsiPolyVariantReference ref = node.getReference(getResolveContext());
                if (ref == null) {
                    return;
                }
                PsiElement resolved = ref.resolve();
                boolean isBuiltin = PyBuiltinCache.getInstance(node).isBuiltin(resolved);
                if (owner instanceof PyClass) {
                    if (isBuiltin || ScopeUtil.getDeclarationScopeOwner(owner, name) != null) {
                        return;
                    }
                }
                if (PyUnreachableCodeInspection.hasAnyInterruptedControlFlowPaths(node)) {
                    return;
                }
                if (owner instanceof PyFile) {
                    if (isBuiltin) {
                        return;
                    }
                    if (resolved != null && !PyUtil.inSameFile(node, resolved)) {
                        return;
                    }
                    registerProblem(node, PyLocalize.inspUnboundNameNotDefined(name).get());
                }
                else {
                    registerProblem(
                        node,
                        PyLocalize.inspUnboundLocalVariable(node.getName()).get(),
                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                        null,
                        new AddGlobalQuickFix()
                    );
                }
            }
        }

        private static boolean isFirstUnboundRead(@Nonnull PyReferenceExpression node, @Nonnull ScopeOwner owner) {
            String nodeName = node.getReferencedName();
            Scope scope = ControlFlowCache.getScope(owner);
            ControlFlow flow = ControlFlowCache.getControlFlow(owner);
            Instruction[] instructions = flow.getInstructions();
            int num = ControlFlowUtil.findInstructionNumberByElement(instructions, node);
            if (num < 0) {
                return true;
            }
            Ref<Boolean> first = Ref.create(true);
            ControlFlowUtil.iteratePrev(num, instructions, instruction -> {
                if (instruction instanceof ReadWriteInstruction) {
                    ReadWriteInstruction rwInstruction = (ReadWriteInstruction) instruction;
                    String name = rwInstruction.getName();
                    PsiElement element = rwInstruction.getElement();
                    if (element != null && name != null && name.equals(nodeName) && instruction.num() != num) {
                        try {
                            if (scope.getDeclaredVariable(element, name) == null) {
                                ReadWriteInstruction.ACCESS access = rwInstruction.getAccess();
                                if (access.isReadAccess()) {
                                    first.set(false);
                                    return ControlFlowUtil.Operation.BREAK;
                                }
                            }
                        }
                        catch (DFALimitExceededException e) {
                            first.set(false);
                        }
                        return ControlFlowUtil.Operation.CONTINUE;
                    }
                }
                return ControlFlowUtil.Operation.NEXT;
            });
            return first.get();
        }

        @Override
        public void visitPyNonlocalStatement(PyNonlocalStatement node) {
            for (PyTargetExpression var : node.getVariables()) {
                String name = var.getName();
                ScopeOwner owner = ScopeUtil.getDeclarationScopeOwner(var, name);
                if (owner == null || owner instanceof PyFile) {
                    registerProblem(
                        var,
                        PyLocalize.inspUnboundNonlocalVariable(name).get(),
                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING
                    );
                }
            }
        }

        private void registerLargeFunction(ScopeOwner owner) {
            registerProblem(
                (owner instanceof PyFunction) ? ((PyFunction) owner).getNameIdentifier() : owner,
                PyLocalize.inspUnboundFunctionTooLarge(owner.getName()).get(),
                ProblemHighlightType.WEAK_WARNING
            );
        }
    }
}
