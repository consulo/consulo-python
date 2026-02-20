/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.google.common.collect.Sets;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.impl.documentation.PythonDocumentationProvider;
import com.jetbrains.python.impl.inspections.quickfix.PyMakeFunctionReturnTypeQuickFix;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.types.*;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElementVisitor;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.util.dataholder.Key;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.*;

/**
 * @author vlan
 */
@ExtensionImpl
public class PyTypeCheckerInspection extends PyInspection {
    private static final Logger LOG = Logger.getInstance(PyTypeCheckerInspection.class);
    private static Key<Long> TIME_KEY = Key.create("PyTypeCheckerInspection.StartTime");

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return LocalizeValue.localizeTODO("Type checker");
    }

    @Nonnull
    @Override
    public PsiElementVisitor buildVisitor(
        @Nonnull ProblemsHolder holder,
        boolean isOnTheFly,
        @Nonnull LocalInspectionToolSession session,
        Object state
    ) {
        if (LOG.isDebugEnabled()) {
            session.putUserData(TIME_KEY, System.nanoTime());
        }
        return new Visitor(holder, session);
    }

    public static class Visitor extends PyInspectionVisitor {
        public Visitor(@Nullable ProblemsHolder holder, @Nonnull LocalInspectionToolSession session) {
            super(holder, session);
        }

        // TODO: Visit decorators with arguments
        @Override
        public void visitPyCallExpression(PyCallExpression node) {
            checkCallSite(node);
        }

        @Override
        public void visitPyBinaryExpression(PyBinaryExpression node) {
            checkCallSite(node);
        }

        @Override
        public void visitPySubscriptionExpression(PySubscriptionExpression node) {
            // TODO: Support slice PySliceExpressions
            checkCallSite(node);
        }

        @Override
        public void visitPyForStatement(PyForStatement node) {
            checkIteratedValue(node.getForPart().getSource(), node.isAsync());
        }

        @Override
        public void visitPyReturnStatement(PyReturnStatement node) {
            ScopeOwner owner = ScopeUtil.getScopeOwner(node);
            if (owner instanceof PyFunction) {
                PyFunction function = (PyFunction) owner;
                PyAnnotation annotation = function.getAnnotation();
                String typeCommentAnnotation = function.getTypeCommentAnnotation();
                if (annotation != null || typeCommentAnnotation != null) {
                    PyExpression returnExpr = node.getExpression();
                    PyType actual = returnExpr != null ? myTypeEvalContext.getType(returnExpr) : PyNoneType.INSTANCE;
                    PyType expected = getExpectedReturnType(function);
                    if (!PyTypeChecker.match(expected, actual, myTypeEvalContext)) {
                        String expectedName = PythonDocumentationProvider.getTypeName(expected, myTypeEvalContext);
                        String actualName = PythonDocumentationProvider.getTypeName(actual, myTypeEvalContext);
                        PyMakeFunctionReturnTypeQuickFix localQuickFix =
                            new PyMakeFunctionReturnTypeQuickFix(function, actualName, myTypeEvalContext);
                        PyMakeFunctionReturnTypeQuickFix globalQuickFix =
                            new PyMakeFunctionReturnTypeQuickFix(function, null, myTypeEvalContext);
                        registerProblem(
                            returnExpr != null ? returnExpr : node,
                            String.format("Expected type '%s', got '%s' instead", expectedName, actualName),
                            localQuickFix,
                            globalQuickFix
                        );
                    }
                }
            }
        }

        @Nullable
        private PyType getExpectedReturnType(@Nonnull PyFunction function) {
            PyType returnType = myTypeEvalContext.getReturnType(function);

            if (returnType instanceof PyCollectionType && PyNames.FAKE_COROUTINE.equals(returnType.getName())) {
                return ((PyCollectionType) returnType).getIteratedItemType();
            }

            return returnType;
        }

        @Override
        public void visitPyFunction(PyFunction node) {
            PyAnnotation annotation = node.getAnnotation();
            String typeCommentAnnotation = node.getTypeCommentAnnotation();
            if (annotation != null || typeCommentAnnotation != null) {
                if (!PyUtil.isEmptyFunction(node)) {
                    PyStatementList statements = node.getStatementList();
                    ReturnVisitor visitor = new ReturnVisitor(node);
                    statements.accept(visitor);
                    if (!visitor.myHasReturns) {
                        PyType expected = myTypeEvalContext.getReturnType(node);
                        String expectedName = PythonDocumentationProvider.getTypeName(expected, myTypeEvalContext);
                        if (expected != null && !(expected instanceof PyNoneType)) {
                            registerProblem(
                                annotation != null ? annotation.getValue() : node.getTypeComment(),
                                String.format("Expected to return '%s', got no return", expectedName)
                            );
                        }
                    }
                }
            }
        }

        @Override
        public void visitPyComprehensionElement(PyComprehensionElement node) {
            super.visitPyComprehensionElement(node);

            for (PyComprehensionForComponent forComponent : node.getForComponents()) {
                checkIteratedValue(forComponent.getIteratedList(), forComponent.isAsync());
            }
        }

        private static class ReturnVisitor extends PyRecursiveElementVisitor {
            private final PyFunction myFunction;
            private boolean myHasReturns = false;

            public ReturnVisitor(PyFunction function) {
                myFunction = function;
            }

            @Override
            public void visitPyReturnStatement(PyReturnStatement node) {
                if (ScopeUtil.getScopeOwner(node) == myFunction) {
                    myHasReturns = true;
                }
            }
        }

        private void checkCallSite(@Nullable PyCallSiteExpression callSite) {
            List<PyTypeChecker.AnalyzeCallResults> resultsSet = PyTypeChecker.analyzeCallSite(callSite, myTypeEvalContext);
            List<Map<PyExpression, Pair<String, ProblemHighlightType>>> problemsSet = new ArrayList<>();
            for (PyTypeChecker.AnalyzeCallResults results : resultsSet) {
                problemsSet.add(checkMapping(results.getReceiver(), results.getArguments()));
            }
            if (!problemsSet.isEmpty()) {
                Map<PyExpression, Pair<String, ProblemHighlightType>> minProblems =
                    Collections.min(problemsSet, (o1, o2) -> o1.size() - o2.size());
                for (Map.Entry<PyExpression, Pair<String, ProblemHighlightType>> entry : minProblems.entrySet()) {
                    registerProblem(entry.getKey(), entry.getValue().getFirst(), entry.getValue().getSecond());
                }
            }
        }

        private void checkIteratedValue(@Nullable PyExpression iteratedValue, boolean isAsync) {
            if (iteratedValue != null) {
                PyType type = myTypeEvalContext.getType(iteratedValue);
                String iterableClassName = isAsync ? PyNames.ASYNC_ITERABLE : PyNames.ITERABLE;

                if (type != null && !PyTypeChecker.isUnknown(type) && !PyABCUtil.isSubtype(type, iterableClassName, myTypeEvalContext)) {
                    String typeName = PythonDocumentationProvider.getTypeName(type, myTypeEvalContext);

                    registerProblem(
                        iteratedValue,
                        String.format("Expected 'collections.%s', got '%s' instead", iterableClassName, typeName)
                    );
                }
            }
        }

        @Nonnull
        private Map<PyExpression, Pair<String, ProblemHighlightType>> checkMapping(
            @Nullable PyExpression receiver,
            @Nonnull Map<PyExpression, PyNamedParameter> mapping
        ) {
            Map<PyExpression, Pair<String, ProblemHighlightType>> problems = new HashMap<>();
            Map<PyGenericType, PyType> substitutions = new LinkedHashMap<>();
            boolean genericsCollected = false;
            for (Map.Entry<PyExpression, PyNamedParameter> entry : mapping.entrySet()) {
                PyNamedParameter param = entry.getValue();
                PyExpression arg = entry.getKey();
                if (param.isPositionalContainer() || param.isKeywordContainer()) {
                    continue;
                }
                PyType paramType = myTypeEvalContext.getType(param);
                if (paramType == null) {
                    continue;
                }
                PyType argType = myTypeEvalContext.getType(arg);
                if (!genericsCollected) {
                    substitutions.putAll(PyTypeChecker.unifyReceiver(receiver, myTypeEvalContext));
                    genericsCollected = true;
                }
                Pair<String, ProblemHighlightType> problem = checkTypes(paramType, argType, myTypeEvalContext, substitutions);
                if (problem != null) {
                    problems.put(arg, problem);
                }
            }
            return problems;
        }

        @Nullable
        private static Pair<String, ProblemHighlightType> checkTypes(
            @Nullable PyType expected,
            @Nullable PyType actual,
            @Nonnull TypeEvalContext context,
            @Nonnull Map<PyGenericType, PyType> substitutions
        ) {
            if (actual != null && expected != null) {
                if (!PyTypeChecker.match(expected, actual, context, substitutions)) {
                    String expectedName = PythonDocumentationProvider.getTypeName(expected, context);
                    String quotedExpectedName = String.format("'%s'", expectedName);
                    boolean hasGenerics = PyTypeChecker.hasGenerics(expected, context);
                    ProblemHighlightType highlightType = ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
                    if (hasGenerics) {
                        PyType substitute = PyTypeChecker.substitute(expected, substitutions, context);
                        if (substitute != null) {
                            quotedExpectedName = String.format(
                                "'%s' (matched generic type '%s')",
                                PythonDocumentationProvider.getTypeName(substitute, context),
                                expectedName
                            );
                            highlightType = ProblemHighlightType.WEAK_WARNING;
                        }
                    }
                    String actualName = PythonDocumentationProvider.getTypeName(actual, context);
                    String msg = String.format("Expected type %s, got '%s' instead", quotedExpectedName, actualName);
                    if (expected instanceof PyStructuralType) {
                        Set<String> expectedAttributes = ((PyStructuralType) expected).getAttributeNames();
                        Set<String> actualAttributes = getAttributes(actual, context);
                        if (actualAttributes != null) {
                            Sets.SetView<String> missingAttributes = Sets.difference(expectedAttributes, actualAttributes);
                            if (missingAttributes.size() == 1) {
                                msg = String.format(
                                    "Type '%s' doesn't have expected attribute '%s'",
                                    actualName,
                                    missingAttributes.iterator().next()
                                );
                            }
                            else {
                                msg = String.format(
                                    "Type '%s' doesn't have expected attributes %s",
                                    actualName,
                                    StringUtil.join(missingAttributes, s -> String.format("'%s'", s), ", ")
                                );
                            }
                        }
                    }
                    return Pair.create(msg, highlightType);
                }
            }
            return null;
        }
    }

    @Nullable
    private static Set<String> getAttributes(@Nonnull PyType type, @Nonnull TypeEvalContext context) {
        if (type instanceof PyStructuralType) {
            return ((PyStructuralType) type).getAttributeNames();
        }
        else if (type instanceof PyClassLikeType) {
            return ((PyClassLikeType) type).getMemberNames(true, context);
        }
        return null;
    }

    @Override
    public void inspectionFinished(@Nonnull LocalInspectionToolSession session, @Nonnull ProblemsHolder problemsHolder) {
        if (LOG.isDebugEnabled()) {
            Long startTime = session.getUserData(TIME_KEY);
            if (startTime != null) {
                LOG.debug(String.format(
                    "[%d] elapsed time: %d ms\n",
                    Thread.currentThread().getId(),
                    (System.nanoTime() - startTime) / 1000000
                ));
            }
        }
    }
}
