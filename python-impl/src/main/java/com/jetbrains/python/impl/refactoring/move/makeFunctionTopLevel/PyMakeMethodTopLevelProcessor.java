// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.refactoring.move.makeFunctionTopLevel;

import com.google.common.collect.Lists;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.refactoring.PyRefactoringUtil;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.PyTargetExpression;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import consulo.usage.UsageInfo;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.MultiMap;
import consulo.util.lang.StringUtil;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.jetbrains.python.impl.psi.PyUtil.as;

/**
 * @author Mikhail Golubev
 */
public class PyMakeMethodTopLevelProcessor extends PyBaseMakeFunctionTopLevelProcessor {

    private final Map<String, String> myAttributeToParameterName = new LinkedHashMap<>();
    private final MultiMap<String, PyReferenceExpression> myAttributeReferences = MultiMap.create();
    private final Set<PsiElement> myReadsOfSelfParam = new HashSet<>();

    public PyMakeMethodTopLevelProcessor(PyFunction targetFunction, String destination) {
        super(targetFunction, destination);
    }

    @Override
    protected LocalizeValue getRefactoringName() {
        return PyLocalize.refactoringMakeMethodTopLevelDialogTitle();
    }

    @Override
    protected void updateUsages(Collection<String> newParamNames, UsageInfo[] usages) {
        // Field usages
        for (String attrName : myAttributeReferences.keySet()) {
            Collection<PyReferenceExpression> reads = myAttributeReferences.get(attrName);
            String paramName = myAttributeToParameterName.get(attrName);
            if (!attrName.equals(paramName)) {
                for (PyReferenceExpression read : reads) {
                    read.replace(myGenerator.createExpressionFromText(LanguageLevel.forElement(read), paramName));
                }
            }
            else {
                for (PyReferenceExpression read : reads) {
                    removeQualifier(read);
                }
            }
        }

        // Function usages
        Collection<String> attrNames = myAttributeToParameterName.keySet();
        for (UsageInfo usage : usages) {
            PsiElement usageElem = usage.getElement();
            if (usageElem == null) {
                continue;
            }

            if (usageElem instanceof PyReferenceExpression usageReference) {
                PyExpression qualifier = usageReference.getQualifier();
                PyCallExpression callExpr = as(usageElem.getParent(), PyCallExpression.class);
                if (qualifier != null && callExpr != null && callExpr.getArgumentList() != null) {
                    PyExpression instanceExpr = qualifier;
                    PyArgumentList argumentList = callExpr.getArgumentList();

                    // Class.method(instance) -> method(instance.attr)
                    if (resolvesToClass(qualifier)) {
                        PyExpression[] arguments = argumentList.getArguments();
                        if (arguments.length > 0) {
                            instanceExpr = arguments[0];
                            instanceExpr.delete();
                        }
                        else {
                            // It's not clear how to handle usages like Class.method(), since there is no suitable instance
                            instanceExpr = null;
                        }
                    }

                    if (instanceExpr != null) {
                        // module.inst.method() -> method(module.inst.foo, module.inst.bar)
                        if (isPureReferenceExpression(instanceExpr)) {
                            // recursive call inside the method
                            if (myReadsOfSelfParam.contains(instanceExpr)) {
                                addArguments(argumentList, newParamNames);
                            }
                            else {
                                String instanceExprText = instanceExpr.getText();
                                addArguments(argumentList, ContainerUtil.map(attrNames, attribute -> instanceExprText + "." + attribute));
                            }
                        }
                        // Class().method() -> method(Class().foo)
                        else if (newParamNames.size() == 1) {
                            addArguments(
                                argumentList,
                                Collections.singleton(instanceExpr.getText() + "." + ContainerUtil.getFirstItem(attrNames))
                            );
                        }
                        // Class().method() -> inst = Class(); method(inst.foo, inst.bar)
                        else if (!newParamNames.isEmpty()) {
                            PyStatement anchor = PsiTreeUtil.getParentOfType(callExpr, PyStatement.class);
                            //noinspection ConstantConditions
                            String className = StringUtil.notNullize(myFunction.getContainingClass().getName(), PyNames.OBJECT);
                            String targetName = PyRefactoringUtil.selectUniqueNameFromType(className, usageElem);
                            String assignmentText = targetName + " = " + instanceExpr.getText();
                            PyAssignmentStatement assignment = myGenerator.createFromText(
                                LanguageLevel.forElement(callExpr),
                                PyAssignmentStatement.class,
                                assignmentText
                            );
                            //noinspection ConstantConditions
                            anchor.getParent().addBefore(assignment, anchor);
                            addArguments(argumentList, ContainerUtil.map(attrNames, attribute -> targetName + "." + attribute));
                        }
                    }
                }

                // Will replace/invalidate entire expression
                removeQualifier(usageReference);
            }
        }
    }

    private boolean resolvesToClass(PyExpression qualifier) {
        for (PsiElement element : PyUtil.multiResolveTopPriority(qualifier, myResolveContext)) {
            if (element == myFunction.getContainingClass()) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPureReferenceExpression(PyExpression expr) {
        if (!(expr instanceof PyReferenceExpression referenceExpression)) {
            return false;
        }
        PyExpression qualifier = referenceExpression.getQualifier();
        return qualifier == null || isPureReferenceExpression(qualifier);
    }

    private PyReferenceExpression removeQualifier(PyReferenceExpression expr) {
        if (!expr.isQualified()) {
            return expr;
        }
        PyExpression newExpression = myGenerator.createExpressionFromText(LanguageLevel.forElement(expr), expr.getLastChild().getText());
        return (PyReferenceExpression) expr.replace(newExpression);
    }

    @Override
    protected PyFunction createNewFunction(Collection<String> newParams) {
        PyFunction copied = (PyFunction) myFunction.copy();
        PyParameter[] params = copied.getParameterList().getParameters();
        if (params.length > 0) {
            params[0].delete();
        }
        addParameters(copied.getParameterList(), newParams);
        return copied;
    }

    @Override
    protected List<String> collectNewParameterNames() {
        Set<String> attributeNames = new LinkedHashSet<>();
        for (ScopeOwner owner : PsiTreeUtil.collectElementsOfType(myFunction, ScopeOwner.class)) {
            AnalysisResult result = analyseScope(owner);
            if (!result.nonlocalWritesToEnclosingScope.isEmpty()) {
                throw new IncorrectOperationException(PyLocalize.refactoringMakeFunctionTopLevelErrorNonlocalWrites());
            }
            if (!result.readsOfSelfParametersFromEnclosingScope.isEmpty()) {
                throw new IncorrectOperationException(PyLocalize.refactoringMakeFunctionTopLevelErrorSelfReads());
            }
            if (!result.readsFromEnclosingScope.isEmpty()) {
                throw new IncorrectOperationException(PyLocalize.refactoringMakeFunctionTopLevelErrorOuterScopeReads());
            }
            if (!result.writesToSelfParameter.isEmpty()) {
                throw new IncorrectOperationException(PyLocalize.refactoringMakeFunctionTopLevelErrorSpecialUsageOfSelf());
            }
            myReadsOfSelfParam.addAll(result.readsOfSelfParameter);
            for (PsiElement usage : result.readsOfSelfParameter) {
                if (usage.getParent() instanceof PyTargetExpression) {
                    throw new IncorrectOperationException(PyLocalize.refactoringMakeFunctionTopLevelErrorAttributeWrites());
                }
                PyReferenceExpression parentReference = as(usage.getParent(), PyReferenceExpression.class);
                if (parentReference != null) {
                    String attrName = parentReference.getName();
                    if (attrName != null && PyUtil.isClassPrivateName(attrName)) {
                        throw new IncorrectOperationException(PyLocalize.refactoringMakeFunctionTopLevelErrorPrivateAttributes());
                    }
                    if (parentReference.getParent() instanceof PyCallExpression) {
                        if (!(Objects.equals(myFunction.getName(), parentReference.getName()))) {
                            throw new IncorrectOperationException(PyLocalize.refactoringMakeFunctionTopLevelErrorMethodCalls());
                        }
                        else {
                            // do not add method itself to its parameters
                            continue;
                        }
                    }
                    attributeNames.add(attrName);
                    myAttributeReferences.putValue(attrName, parentReference);
                }
                else {
                    throw new IncorrectOperationException(PyLocalize.refactoringMakeFunctionTopLevelErrorSpecialUsageOfSelf());
                }
            }
        }
        for (String name : attributeNames) {
            Collection<PyReferenceExpression> reads = myAttributeReferences.get(name);
            PsiElement anchor = ContainerUtil.getFirstItem(reads);
            //noinspection ConstantConditions
            if (!PyRefactoringUtil.isValidNewName(name, anchor)) {
                String indexedName = PyRefactoringUtil.appendNumberUntilValid(name, anchor, PyRefactoringUtil::isValidNewName);
                myAttributeToParameterName.put(name, indexedName);
            }
            else {
                myAttributeToParameterName.put(name, name);
            }
        }
        return Lists.newArrayList(myAttributeToParameterName.values());
    }

    @Override
    protected String getRefactoringId() {
        return "refactoring.python.make.method.top.level";
    }
}
