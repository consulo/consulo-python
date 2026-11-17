// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import consulo.localize.LocalizeValue;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.controlFlow.ControlFlow;
import consulo.language.controlFlow.ControlFlowUtil;
import consulo.language.controlFlow.Instruction;
import consulo.language.dataFlow.DFALimitExceededException;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.util.lang.ref.Ref;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiPolyVariantReference;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.python.impl.localize.PyLocalize;
import com.jetbrains.python.impl.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.impl.codeInsight.controlflow.PyDataFlowKt;
import com.jetbrains.python.impl.codeInsight.controlflow.ReadWriteInstruction;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.Scope;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeVariable;
import com.jetbrains.python.impl.inspections.quickfix.AddGlobalQuickFix;
import com.jetbrains.python.psi.PyAssignmentExpression;
import com.jetbrains.python.psi.PyAugAssignmentStatement;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyComprehensionElement;
import com.jetbrains.python.psi.PyConditionalStatementPart;
import com.jetbrains.python.psi.PyExpressionCodeFragment;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyImportStatementBase;
import com.jetbrains.python.psi.PyNonlocalStatement;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.PyTypeParameter;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.PyWithStatement;
import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import com.jetbrains.python.impl.psi.impl.PyDelStatementNavigator;
import com.jetbrains.python.impl.psi.impl.PyGlobalStatementNavigator;
import com.jetbrains.python.impl.psi.resolve.PyResolveUtil;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

@ExtensionImpl
public final class PyUnboundLocalVariableInspection extends PyInspection {
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameUnbound();
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
        return new Visitor(holder, context);
    }

    public static class Visitor extends PyInspectionVisitor {
        Set<ScopeOwner> LARGE_FUNCTIONS = new HashSet<>();

        Visitor(ProblemsHolder holder, TypeEvalContext context) {
            super(holder, context);
        }

        @Override
        public void visitPyReferenceExpression(PyReferenceExpression node) {
            if (PyResolveUtil.allowForwardReferences(node)) {
                return;
            }

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
            if (PyDataFlowKt.isUnreachableByControlFlow(node, myTypeEvalContext)) {
                return;
            }
            String name = node.getReferencedName();
            if (name == null) {
                return;
            }
            ScopeOwner owner = ScopeUtil.getDeclarationScopeOwner(node, name);
            Set<ScopeOwner> largeFunctions = LARGE_FUNCTIONS;
            assert largeFunctions != null;
            if (owner == null || largeFunctions.contains(owner)) {
                return;
            }
            // Check if it is nonlocal to search in appropriate scope later
            boolean isNonLocal = false;
            ScopeOwner currentScopeOwner = ScopeUtil.getScopeOwner(node);
            if (currentScopeOwner != null) {
                isNonLocal = ControlFlowCache.getScope(currentScopeOwner).isNonlocal(name);
            }
            if (owner != currentScopeOwner && !isNonLocal) {
                return;
            }
            Scope scope = ControlFlowCache.getScope(isNonLocal ? currentScopeOwner : owner);
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
                variable = scope.getDeclaredVariable(anchor, name, myTypeEvalContext);
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
                PsiElement resolved = ref.resolve();
                // type parameter list is not included in CFG
                if (resolved instanceof PyTypeParameter) {
                    return;
                }
                boolean isBuiltin = PyBuiltinCache.getInstance(node).isBuiltin(resolved);
                if (owner instanceof PyClass) {
                    if (isBuiltin || ScopeUtil.getDeclarationScopeOwner(owner, name) != null) {
                        return;
                    }
                }
                if (PyDelStatementNavigator.getDelStatementByTarget(node) != null) {
                    return;
                }
                if (resolvedUnderWithStatement(node, resolved) || resolvedUnderAssignmentExpressionAndCondition(node, resolved)) {
                    return;
                }
                if (owner instanceof PyFile) {
                    if (isBuiltin) {
                        return;
                    }
                    if (resolved != null && !PyUtil.inSameFile(node, resolved)) {
                        return;
                    }
                    registerProblem(node, PyInspectionMessages.problemMessage(msgArgs -> PyLocalize.inspUnboundNameUndefined(msgArgs[0]), name),
                                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
                }
                else if (scope.isGlobal(name)) {
                    registerProblem(node, PyInspectionMessages.problemMessage(msgArgs -> PyLocalize.inspUnboundNameUndefined(msgArgs[0]), name),
                                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
                }
                else if (isNonLocal) {
                    registerProblem(node, PyInspectionMessages.problemMessage(msgArgs -> PyLocalize.inspUnboundLocalVariable(msgArgs[0]), name),
                                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
                }
                else {
                    registerProblem(node, PyInspectionMessages.problemMessage(msgArgs -> PyLocalize.inspUnboundLocalVariable(msgArgs[0]), node.getName()),
                                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                    new AddGlobalQuickFix());
                }
            }
        }

        private static boolean resolvedUnderWithStatement(PyReferenceExpression node, @Nullable PsiElement resolved) {
            return resolved != null &&
                   PyUtil.inSameFile(node, resolved) &&
                   PsiTreeUtil.getParentOfType(resolved, PyWithStatement.class, true, ScopeOwner.class) != null;
        }

        private static boolean resolvedUnderAssignmentExpressionAndCondition(PyReferenceExpression node,
                                                                             @Nullable PsiElement resolved) {
            return resolved instanceof PyTargetExpression &&
                   PyUtil.inSameFile(node, resolved) &&
                   resolved.getParent() instanceof PyAssignmentExpression &&
                   PsiTreeUtil.getParentOfType(
                     PsiTreeUtil.getParentOfType(resolved.getParent(), PyComprehensionElement.class, true, PyConditionalStatementPart.class),
                     PyConditionalStatementPart.class
                   ) != null;
        }

        private boolean isFirstUnboundRead(PyReferenceExpression node, ScopeOwner owner) {
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
                if (instruction instanceof ReadWriteInstruction rwInstruction) {
                    String name = rwInstruction.getName();
                    PsiElement element = rwInstruction.getElement();
                    if (element != null && name != null && name.equals(nodeName) && instruction.num() < num) {
                        try {
                            if (scope.getDeclaredVariable(element, name, myTypeEvalContext) == null) {
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
                    registerProblem(var, PyInspectionMessages.problemMessage(msgArgs -> PyLocalize.inspUnboundNonlocalVariable(msgArgs[0]), name),
                                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
                }
            }
        }

        private void registerLargeFunction(ScopeOwner owner) {
            registerProblem((owner instanceof PyFunction) ? ((PyFunction)owner).getNameIdentifier() : owner,
                            PyInspectionMessages.problemMessage(msgArgs -> PyLocalize.inspUnboundFunctionTooLarge(msgArgs[0]), owner.getName()),
                            ProblemHighlightType.WEAK_WARNING);
        }
    }
}
