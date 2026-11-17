// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.controlflow;

import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.PyEvaluator;
import com.jetbrains.python.psi.PyAssertStatement;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyExpressionStatement;
import com.jetbrains.python.psi.PyRaiseStatement;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.PyStatementList;
import com.jetbrains.python.psi.types.PyNeverType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.language.controlFlow.Instruction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import org.jspecify.annotations.Nullable;

/**
 * Java counterpart of the top-level functions declared in JB {@code PyDataFlow.kt}.
 */
public final class PyDataFlowKt {
    private PyDataFlowKt() {
    }

    /**
     * Checks if inspections should flag a Python element as unreachable.
     * <p>
     * This method considers special cases where code might be technically unreachable
     * but should not be reported as an issue.
     * In particular, the first terminating statement in a sequence is considered valid
     * and should not be reported as unreachable.
     * <p>
     * Terminating statements include:
     * <ul>
     * <li>{@code raise} statements</li>
     * <li>{@code assert False}</li>
     * <li>calls to functions annotated with {@code NoReturn}</li>
     * </ul>
     */
    public static Reachability getReachabilityForInspection(PsiElement element, TypeEvalContext context) {
        return getReachabilityForInspection(element, new FlowContext(context, true, true));
    }

    private static Reachability getReachabilityForInspection(PsiElement element, FlowContext context) {
        return PyUtil.getParameterizedCachedValue(element, context, it -> getReachabilityForInspectionNoCache(element, it));
    }

    private static Reachability getReachabilityForInspectionNoCache(PsiElement element, FlowContext context) {
        PsiElement parent = element.getParent();
        if (parent instanceof PyElement) {
            Reachability reachability = getReachabilityForInspection(parent, context);
            if (reachability != Reachability.REACHABLE) {
                return reachability;
            }
        }
        Reachability reachability = getReachabilityByControlFlow(element, context);
        if (reachability == Reachability.REACHABLE) {
            return Reachability.REACHABLE;
        }
        boolean isIgnoredUnreachableStatement;
        if (element instanceof PyStatementList statementList) {
            PyStatement[] statements = statementList.getStatements();
            isIgnoredUnreachableStatement = statements.length > 0 ? isIgnoredUnreachableStatement(statements[0], context) : true;
        }
        else if (element instanceof PyStatement statement) {
            isIgnoredUnreachableStatement = isIgnoredUnreachableStatement(statement, context);
        }
        else {
            isIgnoredUnreachableStatement = true;
        }
        return isIgnoredUnreachableStatement ? Reachability.REACHABLE : reachability;
    }

    /**
     * Determines if the element is unreachable by control flow analysis.
     * If the element does not have corresponding instruction in CFG, searches for the nearest parent that has.
     */
    public static boolean isUnreachableByControlFlow(PsiElement element, TypeEvalContext context) {
        return getReachabilityByControlFlow(element, new FlowContext(context, true)) != Reachability.REACHABLE;
    }

    private static Reachability getReachabilityByControlFlow(PsiElement element, FlowContext context) {
        return PyUtil.getParameterizedCachedValue(element, context, it -> getReachabilityByControlFlowNoCache(element, it));
    }

    private static Reachability getReachabilityByControlFlowNoCache(PsiElement element, FlowContext context) {
        ScopeOwner scope = ScopeUtil.getScopeOwner(element);
        if (scope != null) {
            PyDataFlow flow = ControlFlowCache.getDataFlow(scope, context);
            Instruction[] instructions = flow.getInstructions();
            int idx = flow.getInstruction(element);
            if (idx < 0 || isAuxiliary(instructions[idx])) {
                PsiElement parent = element.getParent();
                if (parent == null) {
                    return Reachability.REACHABLE;
                }
                return getReachabilityByControlFlow(parent, context);
            }
            return flow.getReachability(instructions[idx]);
        }
        return Reachability.REACHABLE;
    }

    private static boolean isIgnoredUnreachableStatement(PyStatement statement, FlowContext context) {
        if (!(statement.getParent() instanceof PyStatementList parentBlock)) {
            return false;
        }
        if (parentBlock.getStatements()[0] != statement) {
            return false;
        }
        PyStatement parentCompoundStatement = PsiTreeUtil.getParentOfType(parentBlock, PyStatement.class);
        if (parentCompoundStatement == null) {
            return false;
        }
        return getReachabilityByControlFlow(parentCompoundStatement, context) == Reachability.REACHABLE &&
            isTerminatingStatement(statement, context.getTypeEvalContext());
    }

    private static boolean isTerminatingStatement(PsiElement element, TypeEvalContext context) {
        if (element instanceof PyRaiseStatement) {
            return true;
        }
        if (element instanceof PyAssertStatement assertStatement) {
            PyExpression[] arguments = assertStatement.getArguments();
            return arguments.length > 0 && Boolean.FALSE.equals(asBooleanNoResolve(arguments[0]));
        }
        if (element instanceof PyExpressionStatement expressionStatement) {
            PyExpression expression = expressionStatement.getExpression();
            return expression instanceof PyCallExpression && context.getType(expression) instanceof PyNeverType;
        }
        return false;
    }

    private static boolean isAuxiliary(Instruction instruction) {
        if (instruction instanceof PyRaiseInstruction) {
            return true;
        }
        if (instruction instanceof PyWithContextExitInstruction) {
            return true;
        }
        if (instruction instanceof PyFinallyFailExitInstruction) {
            return true;
        }
        if (instruction instanceof ReadWriteInstruction readWriteInstruction) {
            return readWriteInstruction.getAccess().isAssertTypeAccess();
        }
        return false;
    }

    private static @Nullable Boolean asBooleanNoResolve(PyExpression expression) {
        return PyEvaluator.evaluateAsBooleanNoResolve(expression);
    }
}
