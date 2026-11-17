// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.hierarchy.call;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.findUsages.PyClassFindUsagesHandler;
import com.jetbrains.python.impl.findUsages.PyFunctionFindUsagesHandler;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.search.PySuperMethodsSearch;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyLambdaExpression;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.PyParameterList;
import com.jetbrains.python.psi.PyParenthesizedExpression;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.PyRecursiveElementVisitor;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.util.function.CommonProcessors;
import consulo.find.FindUsagesHandler;
import consulo.find.FindUsagesOptions;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.usage.UsageInfo;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.MultiMap;
import one.util.streamex.StreamEx;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author novokrest
 */
public final class PyStaticCallHierarchyUtil {
    @RequiredReadAction
    public static Map<PsiElement, Collection<PsiElement>> getCallees(PyElement element) {
        MultiMap<PsiElement, PsiElement> callees = MultiMap.createOrderedSet();

        PyResolveContext resolveContext =
            PyResolveContext.implicitContext(TypeEvalContext.userInitiated(element.getProject(), element.getContainingFile()));

        PyRecursiveElementVisitor visitor = new PyRecursiveElementVisitor() {
            @Override
            public void visitPyParameterList(PyParameterList node) {
            }

            @Override
            public void visitPyLambdaExpression(PyLambdaExpression node) {
            }

            @Override
            public void visitPyFunction(PyFunction innerFunction) {
                for (PyParameter parameter : innerFunction.getParameterList().getParameters()) {
                    PsiElement defaultValue = parameter.getDefaultValue();
                    if (defaultValue != null) {
                        defaultValue.accept(this);
                    }
                }
            }

            @Override
            public void visitPyCallExpression(PyCallExpression node) {
                super.visitPyCallExpression(node);

                StreamEx
                    .of(node.multiResolveCalleeFunction(resolveContext))
                    .select(PyFunction.class)
                    .forEach(function -> callees.putValue(function, node));
            }
        };

        visitor.visitElement(element);

        return callees.freezeValues();
    }

    @RequiredReadAction
    public static Map<PsiElement, Collection<PsiElement>> getCallers(PyElement pyElement) {
        MultiMap<PsiElement, PsiElement> callers = MultiMap.createOrderedSet();
        Collection<UsageInfo> usages = findUsages(pyElement);

        for (UsageInfo usage : usages) {
            PsiElement element = usage.getElement();
            if (element == null) {
                continue;
            }

            element = element.getParent();
            while (element instanceof PyParenthesizedExpression) {
                element = element.getParent();
            }

            if (element instanceof PyCallExpression callExpr) {
                PyExpression receiver = callExpr.getCallee() instanceof PyQualifiedExpression callee ? callee.getQualifier() : null;
                if (receiver instanceof PyCallExpression && ((PyCallExpression) receiver).isCalleeText(PyNames.SUPER)) {
                    continue;
                }
                PsiElement caller = PsiTreeUtil.getParentOfType(element, PyParameterList.class, PyFunction.class);
                if (caller instanceof PyFunction) {
                    callers.putValue(caller, element);
                }
                else if (caller instanceof PyParameterList) {
                    PsiElement innerFunction = PsiTreeUtil.getParentOfType(caller, PyFunction.class);
                    PsiElement outerFunction = PsiTreeUtil.getParentOfType(innerFunction, PyFunction.class);
                    if (innerFunction != null && outerFunction != null) {
                        callers.putValue(outerFunction, element);
                    }
                }
            }
        }

        return callers.freezeValues();
    }

    private static Collection<UsageInfo> findUsages(PsiElement element) {
        FindUsagesHandler handler = createFindUsageHandler(element);
        if (handler == null) {
            return new ArrayList<>();
        }
        CommonProcessors.CollectProcessor<UsageInfo> processor = new CommonProcessors.CollectProcessor<>();
        PsiElement[] psiElements = ArrayUtil.mergeArrays(handler.getPrimaryElements(), handler.getSecondaryElements());
        FindUsagesOptions options = handler.getFindUsagesOptions(null);
        for (PsiElement psiElement : psiElements) {
            handler.processElementUsages(psiElement, processor, options);
        }
        return processor.getResults();
    }

    /**
     * @see com.jetbrains.python.impl.findUsages.PyFindUsagesHandlerFactory#createFindUsagesHandler(PsiElement, boolean)
     */
    private static @Nullable FindUsagesHandler createFindUsageHandler(PsiElement element) {
        if (element instanceof PyFunction) {
            TypeEvalContext context = TypeEvalContext.userInitiated(element.getProject(), null);
            Collection<PsiElement> superMethods = PySuperMethodsSearch.search((PyFunction) element, true, context).findAll();
            if (!superMethods.isEmpty()) {
                PsiElement next = superMethods.iterator().next();
                if (next instanceof PyFunction && !isInObject((PyFunction) next)) {
                    List<PsiElement> allMethods = new ArrayList<>();
                    allMethods.add(element);
                    allMethods.addAll(superMethods);

                    return new PyFunctionFindUsagesHandler(element, allMethods);
                }
            }
            return new PyFunctionFindUsagesHandler(element);
        }
        if (element instanceof PyClass) {
            return new PyClassFindUsagesHandler((PyClass) element);
        }
        return null;
    }

    /**
     * @see com.jetbrains.python.impl.findUsages.PyPsiFindUsagesHandlerFactory#isInObject(PyFunction)
     */
    private static boolean isInObject(PyFunction fun) {
        PyClass containingClass = fun.getContainingClass();
        if (containingClass == null) {
            return false;
        }
        return PyUtil.isObjectClass(containingClass);
    }
}
