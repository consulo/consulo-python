// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl.references;

import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.collection.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.ast.PyAstFunction;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyCallable;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyKeywordArgument;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PySlashParameter;
import com.jetbrains.python.psi.PyStarArgument;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PySubscriptionExpression;
import com.jetbrains.python.psi.PyTypedElement;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.PyCallExpressionHelper;
import com.jetbrains.python.psi.impl.PyKeywordArgumentProvider;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.QualifiedResolveResult;
import com.jetbrains.python.impl.psi.search.PySuperMethodsSearch;
import com.jetbrains.python.psi.types.PyCallableParameter;
import com.jetbrains.python.psi.types.PyCallableType;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.impl.psi.types.PyOverloadType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.impl.psi.types.PyTypeUtil;
import com.jetbrains.python.psi.types.TypeEvalContext;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.jetbrains.python.impl.psi.PyUtil.as;
import static com.jetbrains.python.impl.psi.types.PyTypeUtilKt.isUnknown;

public final class KeywordArgumentCompletionUtil {
    public static void collectFunctionArgNames(PyElement element,
                                               List<? super LookupElement> ret,
                                               TypeEvalContext context,
                                               boolean addEquals) {
        PyCallExpression callExpr = PsiTreeUtil.getParentOfType(element, PyCallExpression.class);
        if (callExpr != null) {
            PyExpression callee = callExpr.getCallee();
            if (callee instanceof PyReferenceExpression && element.getParent() == callExpr.getArgumentList()) {
                PyType calleeType = PyCallExpressionHelper.getCalleeType(callee, PyResolveContext.defaultContext(context));
                if (isUnknown(calleeType)) {
                    PyTypedElement implicit = as(getElementByChain((PyReferenceExpression)callee, context), PyTypedElement.class);
                    if (implicit != null) {
                        calleeType = context.getType(implicit);
                    }
                }
                Set<String> namedArgsAlready = Stream.of(callExpr.getArgumentList().getArguments())
                    .filter(PyKeywordArgument.class::isInstance)
                    .map(PyKeywordArgument.class::cast)
                    .map(PyKeywordArgument::getKeyword)
                    .collect(Collectors.toSet());
                List<LookupElement> extra = PyTypeUtil.toStream(calleeType)
                    .<PyType>flatMap(type -> type instanceof PyOverloadType overloadType ? overloadType.getItems().stream() : Stream.of(type))
                    .filter(PyCallableType.class::isInstance)
                    .map(PyCallableType.class::cast)
                    .flatMap(type -> collectParameterNamesFromType(type, callExpr, context).stream())
                    .filter(it -> !namedArgsAlready.contains(it))
                    .map(name -> PyUtil.createNamedParameterLookup(name, element.getContainingFile(), addEquals))
                    .toList();

                ret.addAll(extra);
            }
        }
    }

    private static List<String> collectParameterNamesFromType(PyCallableType type,
                                                                       PyCallExpression callSite,
                                                                       TypeEvalContext context) {
        List<String> result = new ArrayList<>();
        if (type.isCallable()) {
            List<PyCallableParameter> parameters =
                PyCallExpressionHelper.expandParamSpecArgsKwargs(callSite, type, context).getUnpackedParameters(context);
            if (parameters != null) {
                int indexOfPySlashParameter = getIndexOfPySlashParameter(parameters);
                for (PyCallableParameter parameter : parameters.subList(indexOfPySlashParameter + 1, parameters.size())) {
                    if (parameter.isKeywordContainer() || parameter.isPositionalContainer()) {
                        continue;
                    }
                    ContainerUtil.addIfNotNull(result, parameter.getName());
                }
                PyFunction func = null;
                if (type.getCallable() instanceof PyFunction) {
                    func = as(type.getCallable(), PyFunction.class);
                }
                else if (type instanceof PyClassType) {
                    func = ((PyClassType)type).getPyClass().findInitOrNew(true, context);
                }
                if (func != null) {
                    addKeywordArgumentVariantsForFunction(callSite, func, result, new HashSet<>(), context);
                }
            }
        }
        return result;
    }

    private static PsiElement getElementByChain(PyReferenceExpression callee, TypeEvalContext context) {
        PyResolveContext resolveContext = PyResolveContext.implicitContext(context);
        QualifiedResolveResult result = callee.followAssignmentsChain(resolveContext);
        return result.getElement();
    }

    private static int getIndexOfPySlashParameter(List<PyCallableParameter> parameters) {
        return ContainerUtil.indexOf(parameters, parameter -> parameter.getParameter() instanceof PySlashParameter);
    }

    private static void addKeywordArgumentVariantsForFunction(PyCallExpression callExpr,
                                                              PyFunction function,
                                                              List<String> ret,
                                                              Set<PyCallable> visited,
                                                              TypeEvalContext context) {
        if (visited.contains(function)) {
            return;
        }

        boolean needSelf = function.getContainingClass() != null && function.getModifier() != PyAstFunction.Modifier.STATICMETHOD;
        KwArgParameterCollector collector = new KwArgParameterCollector(needSelf, ret);

        List<PyCallableParameter> parameters = function.getParameters(context);
        int indexOfPySlashParameter = getIndexOfPySlashParameter(parameters);

        parameters.stream()
            .skip(indexOfPySlashParameter + 1)
            .map(PyCallableParameter::getParameter)
            .filter(Objects::nonNull)
            .forEach(parameter -> parameter.accept(collector));

        if (collector.hasKwArgs()) {
            for (PyKeywordArgumentProvider provider : PyKeywordArgumentProvider.EP_NAME.getExtensionList()) {
                ret.addAll(provider.getKeywordArguments(function, callExpr));
            }
            KwArgFromStatementCallCollector fromStatementCallCollector = new KwArgFromStatementCallCollector(ret, collector.getKwArgs());
            function.getStatementList().acceptChildren(fromStatementCallCollector);

            //if (collector.hasOnlySelfAndKwArgs()) {
            // nothing interesting besides self and **kwargs, let's look at superclass (PY-778)
            if (fromStatementCallCollector.isKwArgsTransit()) {

                PyFunction superMethod = as(PySuperMethodsSearch.search(function, context).findFirst(), PyFunction.class);
                if (superMethod != null) {
                    addKeywordArgumentVariantsForFunction(callExpr, superMethod, ret, visited, context);
                }
            }
        }
    }

    public static class KwArgParameterCollector extends PyElementVisitor {
        private int myCount;
        private final boolean myNeedSelf;
        private final List<? super String> myRet;
        private boolean myHasSelf = false;
        private boolean myHasKwArgs = false;
        private PyParameter kwArgsParam = null;

        public KwArgParameterCollector(boolean needSelf, List<? super String> ret) {
            myNeedSelf = needSelf;
            myRet = ret;
        }

        @Override
        public void visitPyParameter(PyParameter par) {
            myCount++;
            if (myCount == 1 && myNeedSelf) {
                myHasSelf = true;
                return;
            }
            PyNamedParameter namedParam = par.getAsNamed();
            if (namedParam != null) {
                if (!namedParam.isKeywordContainer() && !namedParam.isPositionalContainer()) {
                    myRet.add(namedParam.getName());
                }
                else if (namedParam.isKeywordContainer()) {
                    myHasKwArgs = true;
                    kwArgsParam = namedParam;
                }
            }
        }

        public PyParameter getKwArgs() {
            return kwArgsParam;
        }

        public boolean hasKwArgs() {
            return myHasKwArgs;
        }
    }

    public static class KwArgFromStatementCallCollector extends PyElementVisitor {
        private final List<String> myRet;
        private final PyParameter myKwArgs;
        private boolean kwArgsTransit = true;

        public KwArgFromStatementCallCollector(List<String> ret, PyParameter kwArgs) {
            myRet = ret;
            this.myKwArgs = kwArgs;
        }

        @Override
        public void visitPyElement(PyElement node) {
            node.acceptChildren(this);
        }

        @Override
        public void visitPySubscriptionExpression(PySubscriptionExpression node) {
            String operandName = node.getOperand().getName();
            processGet(operandName, node.getIndexExpression());
        }

        @Override
        public void visitPyCallExpression(PyCallExpression node) {
            if (node.isCalleeText("pop", "get", "getattr")) {
                PyReferenceExpression child = PsiTreeUtil.getChildOfType(node.getCallee(), PyReferenceExpression.class);
                if (child != null) {
                    String operandName = child.getName();
                    if (node.getArguments().length > 0) {
                        PyExpression argument = node.getArguments()[0];
                        processGet(operandName, argument);
                    }
                }
            }
            else if (node.isCalleeText("__init__")) {
                kwArgsTransit = false;
                for (PyExpression e : node.getArguments()) {
                    if (e instanceof PyStarArgument kw) {
                        if (Objects.equals(myKwArgs.getName(), kw.getFirstChild().getNextSibling().getText())) {
                            kwArgsTransit = true;
                            break;
                        }
                    }
                }
            }
            super.visitPyCallExpression(node);
        }

        private void processGet(String operandName, PyExpression argument) {
            if (Objects.equals(myKwArgs.getName(), operandName) &&
                argument instanceof PyStringLiteralExpression) {
                String name = ((PyStringLiteralExpression)argument).getStringValue();
                if (PyNames.isIdentifier(name)) {
                    myRet.add(name);
                }
            }
        }

        /**
         * is name of kwargs parameter the same as transmitted to __init__ call
         *
         */
        public boolean isKwArgsTransit() {
            return kwArgsTransit;
        }
    }
}
