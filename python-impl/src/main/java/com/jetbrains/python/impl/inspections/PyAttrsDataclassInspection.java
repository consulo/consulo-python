// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.codeInsight.PyDataclassFieldParameters;
import com.jetbrains.python.impl.codeInsight.PyDataclassParameters;
import com.jetbrains.python.impl.codeInsight.PyDataclassesKt;
import com.jetbrains.python.impl.codeInsight.stdlib.PyAttrsDataclassType;
import com.jetbrains.python.impl.codeInsight.stdlib.PyDataclassNames.Attrs;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.PyEvaluator;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyDecorator;
import com.jetbrains.python.psi.PyDecoratorList;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyParameterList;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiNameIdentifierOwner;
import consulo.language.psi.util.QualifiedName;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.lang.Pair;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ExtensionImpl
public class PyAttrsDataclassInspection extends PyInspection {
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameAttrsDataclassDefinitionAndUsages();
    }

    @Override
    public LocalizeValue getGroupDisplayName() {
        return PyLocalize.inspGroupPythonDataclasses();
    }

    /**
     * JB registration: suppressId="PyDataclass"
     */
    @Override
    public String getID() {
        return "PyDataclass";
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

    public static class Visitor extends PyDataclassVisitor {
        public Visitor(@Nullable ProblemsHolder holder, TypeEvalContext context) {
            super(holder, context);
        }

        @Override
        public void visitPyClass(PyClass node) {
            PyDataclassParameters dataclassParameters = PyDataclassesKt.parseDataclassParameters(node, myTypeEvalContext);
            if (dataclassParameters == null || dataclassParameters.getType() != PyAttrsDataclassType.INSTANCE) {
                return;
            }

            processAttrsParameters(node, dataclassParameters);

            PyFunction postInit = node.findMethodByName(Attrs.DUNDER_POST_INIT, false, myTypeEvalContext);
            if (postInit != null) {
                processAttrsPostInitDefinition(postInit, dataclassParameters);
            }

            processAttrsDefaultThroughDecorator(dataclassParameters, node);
            processAttrsInitializersAndValidators(node);
            processAttrIbFunctionCalls(dataclassParameters, node);
        }

        private void processAttrsParameters(PyClass cls, PyDataclassParameters dataclassParameters) {
            PyFunction initMethod = null;
            PyFunction reprMethod = null;
            PyFunction strMethod = null;
            List<PyFunction> cmpMethods = new ArrayList<>();
            List<PyFunction> mutatingMethods = new ArrayList<>();
            PsiNameIdentifierOwner hashMethod = null;

            for (PyFunction it : cls.getMethods()) {
                String name = it.getName();
                if (PyNames.INIT.equals(name)) {
                    initMethod = it;
                }
                else if ("__repr__".equals(name)) {
                    reprMethod = it;
                }
                else if ("__str__".equals(name)) {
                    strMethod = it;
                }
                else if ("__eq__".equals(name) || name != null && PyDataclassVisitorKt.ORDER_OPERATORS.contains(name)) {
                    cmpMethods.add(it);
                }
                else if ("__setattr__".equals(name) || "__delattr__".equals(name)) {
                    mutatingMethods.add(it);
                }
                else if (PyNames.HASH.equals(name)) {
                    hashMethod = it;
                }
            }

            if (hashMethod == null) {
                hashMethod = cls.findClassAttribute(PyNames.HASH, false, myTypeEvalContext);
            }

            // element to register problem and corresponding attr.s parameter
            List<Pair<@Nullable PsiNameIdentifierOwner, String>> problems = new ArrayList<>();

            if (dataclassParameters.getInit() && initMethod != null) {
                problems.add(Pair.create(initMethod, "init"));
            }

            if (dataclassParameters.getRepr() && reprMethod != null) {
                problems.add(Pair.create(reprMethod, "repr"));
            }

            if (PyEvaluator.evaluateAsBoolean(PyUtil.peelArgument(dataclassParameters.getOthers().get("str")), false) && strMethod != null) {
                problems.add(Pair.create(strMethod, "str"));
            }

            if (dataclassParameters.getOrder() && !cmpMethods.isEmpty()) {
                for (PyFunction it : cmpMethods) {
                    problems.add(Pair.create(it, "cmp/order"));
                }
            }

            if (Boolean.TRUE.equals(dataclassParameters.getFrozen()) && !mutatingMethods.isEmpty()) {
                for (PyFunction it : mutatingMethods) {
                    problems.add(Pair.create(it, "frozen"));
                }
            }

            if (dataclassParameters.getUnsafeHash() && hashMethod != null) {
                problems.add(Pair.create(hashMethod, "hash"));
            }

            for (Pair<@Nullable PsiNameIdentifierOwner, String> it : problems) {
                PsiNameIdentifierOwner owner = it.first;
                if (owner != null) {
                    registerProblem(owner.getNameIdentifier(),
                                    PyInspectionMessages.problemMessage(
                                        a -> PyLocalize.inspDataclassesMethodIsIgnoredIfClassAlreadyDefinesParameter(a[0], a[1]),
                                        owner.getName(), it.second),
                                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
                }
            }

            if (dataclassParameters.getOrder() && Boolean.TRUE.equals(dataclassParameters.getFrozen()) && hashMethod != null) {
                registerProblem(hashMethod.getNameIdentifier(),
                                PyLocalize.inspDataclassesHashIgnoredIfClassAlreadyDefinesCmpOrOrderOrFrozenParameters().get(),
                                ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
            }
        }

        private void processAttrsDefaultThroughDecorator(PyDataclassParameters dataclassParameters, PyClass cls) {
            Map<String, List<PyFunction>> initializers = new LinkedHashMap<>();

            for (PyFunction method : cls.getMethods()) {
                PyDecoratorList decoratorList = method.getDecoratorList();
                PyDecorator[] decorators = decoratorList != null ? decoratorList.getDecorators() : null;

                if (decorators != null) {
                    String name = null;
                    for (PyDecorator decorator : decorators) {
                        QualifiedName qualifiedName = decorator.getQualifiedName();
                        if (qualifiedName != null && qualifiedName.getComponentCount() == 2 && qualifiedName.endsWith("default")) {
                            String firstComponent = qualifiedName.getFirstComponent();
                            if (firstComponent != null) {
                                name = firstComponent;
                                break;
                            }
                        }
                    }
                    if (name != null) {
                        PyTargetExpression attribute = cls.findClassAttribute(name, false, myTypeEvalContext);
                        if (attribute != null) {
                            initializers.computeIfAbsent(name, k -> new ArrayList<>()).add(method);

                            PyDataclassFieldParameters fieldParams =
                                PyDataclassesKt.resolveDataclassFieldParameters(cls, dataclassParameters, attribute, myTypeEvalContext);
                            if (fieldParams != null && (fieldParams.getHasDefault() || fieldParams.getHasDefaultFactory())) {
                                registerProblem(method.getNameIdentifier(),
                                                PyInspectionMessages.problemMessage(a -> PyLocalize.inspDataclassesAttributeDefaultSetUsingMethod(a[0]),
                                                                                    attribute.getCalleeName() + "()"),
                                                ProblemHighlightType.GENERIC_ERROR);
                            }
                        }
                    }
                }
            }

            for (List<PyFunction> sameAttrInitializers : initializers.values()) {
                PyFunction first = sameAttrInitializers.get(0);

                for (PyFunction it : sameAttrInitializers.subList(1, sameAttrInitializers.size())) {
                    registerProblem(it.getNameIdentifier(),
                                    PyInspectionMessages.problemMessage(a -> PyLocalize.inspDataclassesAttributeDefaultSetUsingMethod(a[0]),
                                                                        first.getName()),
                                    ProblemHighlightType.GENERIC_ERROR);
                }
            }
        }

        private void processAttrsInitializersAndValidators(PyClass cls) {
            cls.visitMethods(
                method -> {
                    PyDecoratorList decoratorList = method.getDecoratorList();
                    PyDecorator[] decorators = decoratorList != null ? decoratorList.getDecorators() : null;

                    if (decorators != null) {
                        for (PyDecorator decorator : decorators) {
                            QualifiedName qualifiedName = decorator.getQualifiedName();
                            if (qualifiedName == null || qualifiedName.getComponentCount() != 2) {
                                continue;
                            }
                            String lastComponent = qualifiedName.getLastComponent();
                            if (lastComponent == null) {
                                continue;
                            }
                            int expectedParameters;
                            if ("default".equals(lastComponent)) {
                                expectedParameters = 1;
                            }
                            else if ("validator".equals(lastComponent)) {
                                expectedParameters = 3;
                            }
                            else {
                                continue;
                            }

                            PyParameterList actualParameters = method.getParameterList();
                            if (actualParameters.getParameters().length != expectedParameters) {
                                PyInspectionMessages.ProblemMessage message =
                                    PyInspectionMessages.problemMessage(a -> PyLocalize.inspDataclassesMethodShouldTakeOnlyNParameter(a[0], a[1]),
                                                                        method.getName(), expectedParameters);

                                registerProblem(actualParameters, message, ProblemHighlightType.GENERIC_ERROR);
                            }
                        }
                    }

                    return true;
                },
                false,
                myTypeEvalContext
            );
        }

        private void processAttrIbFunctionCalls(PyDataclassParameters dataclassParameters, PyClass dataclass) {
            dataclass.processClassLevelDeclarations((element, state) -> {
                if (element instanceof PyTargetExpression targetExpression) {
                    PyDataclassFieldParameters fieldParams =
                        PyDataclassesKt.resolveDataclassFieldParameters(dataclass, dataclassParameters, targetExpression, myTypeEvalContext);
                    PyCallExpression call = targetExpression.findAssignedValue() instanceof PyCallExpression callExpression ? callExpression : null;

                    if (call != null && fieldParams != null) {
                        if (fieldParams.getHasDefaultFactory()) {
                            if (fieldParams.getHasDefault()) {
                                registerProblem(call.getArgumentList(), PyLocalize.inspDataclassesCannotSpecifyBothDefaultAndFactory().get(),
                                                ProblemHighlightType.GENERIC_ERROR);
                            }
                            else {
                                // at least covers the following case: `attr.ib(default=attr.Factory(...), factory=...)`

                                PyExpression defaultArgument = call.getKeywordArgument("default");
                                PyExpression factory = call.getKeywordArgument("factory");

                                if (defaultArgument != null && factory != null &&
                                    !PyDataclassesKt.resolvesToOmittedDefault(PyAttrsDataclassType.INSTANCE.getResolver(), defaultArgument)) {
                                    registerProblem(call.getArgumentList(), PyLocalize.inspDataclassesCannotSpecifyBothDefaultAndFactory().get(),
                                                    ProblemHighlightType.GENERIC_ERROR);
                                }
                            }
                        }
                    }
                }

                return true;
            });
        }

        private void processAttrsPostInitDefinition(PyFunction postInit, PyDataclassParameters dataclassParameters) {
            if (!dataclassParameters.getInit()) {
                registerProblem(postInit.getNameIdentifier(),
                                PyLocalize.inspDataclassesAttrsPostInitWouldNotBeCalledUntilInitParameterSetToTrue().get(),
                                ProblemHighlightType.LIKE_UNUSED_SYMBOL);
            }

            if (postInit.getParameters(myTypeEvalContext).size() != 1) {
                registerProblem(postInit.getParameterList(),
                                PyLocalize.inspDataclassesAttrsPostInitShouldNotTakeAnyParametersExceptSelf().get(),
                                ProblemHighlightType.GENERIC_ERROR);
            }
        }
    }
}
