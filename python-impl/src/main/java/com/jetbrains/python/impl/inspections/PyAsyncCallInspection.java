// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.impl.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.impl.psi.PyKnownDecoratorUtil;
import com.jetbrains.python.impl.psi.types.PyABCUtil;
import com.jetbrains.python.impl.refactoring.PyReplaceExpressionUtil;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyCallable;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyExpressionStatement;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.PyCallableType;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

@ExtensionImpl
public class PyAsyncCallInspection extends PyInspection {
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspAsyncCall();
    }

    @Override
    public LocalizeValue getGroupDisplayName() {
        return PyLocalize.inspGroupPythonAsynchronousCode();
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

    private static class Visitor extends PyInspectionVisitor {
        Visitor(ProblemsHolder holder, TypeEvalContext context) {
            super(holder, context);
        }

        @Override
        public void visitPyExpressionStatement(PyExpressionStatement node) {
            PyExpression expr = node.getExpression();
            if (expr instanceof PyCallExpression callExpression && isAwaitableCall(callExpression) && !isIgnored(callExpression)) {
                AwaitableType awaitableType;
                if (isOuterFunctionAsync(callExpression)) {
                    awaitableType = AwaitableType.AWAITABLE;
                }
                else if (isOuterFunctionCoroutine(callExpression, myTypeEvalContext)) {
                    awaitableType = AwaitableType.COROUTINE;
                }
                else {
                    return;
                }
                // no need to check whether await or yield from is missed, because it's PyCallExpression already, not PyPrefixExpression
                String functionName = getCalledCoroutineName(callExpression, getResolveContext());
                if (functionName == null) {
                    return;
                }
                registerProblem(node, PyInspectionMessages.problemMessage(a -> PyLocalize.inspNameCoroutineIsNotAwaited(a[0]), functionName),
                                ProblemHighlightType.GENERIC_ERROR_OR_WARNING, new PyAddAwaitCallForCoroutineFix(awaitableType));
            }
        }

        private boolean isIgnored(PyCallExpression callExpr) {
            // ignore functions which return Task
            PyType type = myTypeEvalContext.getType(callExpr);
            if (type == null) {
                return true;
            }
            String typeQName = type instanceof PyClassLikeType classLikeType ? classLikeType.getClassQName() : null;
            if (IGNORE_RETURNED_TYPE.contains(typeQName)) {
                return true;
            }

            // ignore builtin functions which schedule function to run in a loop without `await`
            List<PyCallable> callables = callExpr.multiResolveCalleeFunction(getResolveContext());
            String qualifiedName = callables.isEmpty() ? null : callables.get(0).getQualifiedName();
            return IGNORE_BUILTIN_FUNCTIONS.contains(qualifiedName);
        }

        private boolean isAwaitableCall(PyCallExpression callExpr) {
            PyType type = myTypeEvalContext.getType(callExpr);
            if (type == null) {
                return false;
            }
            return PyABCUtil.isSubtype(type, PyNames.AWAITABLE, myTypeEvalContext) ||
                PyTypingTypeProvider.isGenerator(type) &&
                    PyKnownDecoratorUtil.isResolvedToGeneratorBasedCoroutine(callExpr, getResolveContext(), myTypeEvalContext);
        }
    }

    private static class PyAddAwaitCallForCoroutineFix implements LocalQuickFix {
        private final AwaitableType myType;

        PyAddAwaitCallForCoroutineFix(AwaitableType type) {
            myType = type;
        }

        @Override
        public LocalizeValue getName() {
            return PyLocalize.qfixCoroutineIsNotAwaited();
        }

        @Override
        public void applyFix(Project project, ProblemDescriptor descriptor) {
            PsiElement element = descriptor.getPsiElement();
            if (element instanceof PyExpressionStatement) {
                StringBuilder sb = new StringBuilder();
                switch (myType) {
                    case AWAITABLE -> sb.append("async def foo():\n    ").append(PyNames.AWAIT).append(" ").append(element.getText());
                    case COROUTINE -> sb.append("def foo():\n    ").append(PyNames.YIELD).append(" ").append(PyNames.FROM).append(" ")
                        .append(element.getText());
                }
                PyElementGenerator generator = PyElementGenerator.getInstance(project);
                PyFunction function = generator.createFromText(LanguageLevel.forElement(element), PyFunction.class, sb.toString());
                PyStatement[] statements = function.getStatementList().getStatements();
                PyStatement awaitedStatement = statements.length > 0 ? statements[0] : null;
                if (awaitedStatement != null) {
                    PyReplaceExpressionUtil.replaceExpression(element, awaitedStatement);
                }
            }
        }
    }

    private enum AwaitableType {
        AWAITABLE, COROUTINE
    }

    private static final List<String> IGNORE_RETURNED_TYPE = Arrays.asList(
        "asyncio.tasks.Task",
        "asyncio.Task",
        "_asyncio.Task"
    );
    private static final List<String> IGNORE_BUILTIN_FUNCTIONS = Arrays.asList("asyncio.events.AbstractEventLoop.run_in_executor",
                                                                         "asyncio.tasks.ensure_future",
                                                                         "asyncio.ensure_future");

    private static @Nullable String getCalledCoroutineName(PyCallExpression callExpression, PyResolveContext resolveContext) {
        if (!(callExpression.getCallee() instanceof PyReferenceExpression callee)) {
            return null;
        }
        List<PyCallableType> callableTypes = callExpression.multiResolveCallee(resolveContext);
        PyCallable callable = callableTypes.isEmpty() ? null : callableTypes.get(0).getCallable();
        PyFunction function = callable instanceof PyFunction pyFunction ? pyFunction : null;
        return function == null ? callee.getName() : function.getName();
    }

    private static boolean isOuterFunctionAsync(PyExpression node) {
        return ScopeUtil.getScopeOwner(node) instanceof PyFunction function && function.isAsync();
    }

    private static boolean isOuterFunctionCoroutine(PyExpression node, TypeEvalContext typeEvalContext) {
        if (!(ScopeUtil.getScopeOwner(node) instanceof PyFunction pyFunction)) {
            return false;
        }
        return PyKnownDecoratorUtil.hasGeneratorBasedCoroutineDecorator(pyFunction, typeEvalContext);
    }
}
