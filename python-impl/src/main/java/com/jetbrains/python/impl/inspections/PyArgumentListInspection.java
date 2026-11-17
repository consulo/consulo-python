// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import consulo.localize.LocalizeValue;
import consulo.annotation.component.ExtensionImpl;
import com.google.common.collect.Lists;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.ast.ASTNode;
import consulo.project.Project;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.util.collection.ContainerUtil;
import consulo.python.impl.localize.PyLocalize;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.impl.PythonUiService;
import com.jetbrains.python.impl.codeInsight.PyPsiIndexUtil;
import com.jetbrains.python.impl.inspections.quickfix.PyRemoveArgumentQuickFix;
import com.jetbrains.python.impl.inspections.quickfix.PyRenameArgumentQuickFix;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyCallable;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyDecorator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyKeywordArgument;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.PyStarArgument;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.PyCallExpressionHelper;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.PyCallableParameter;
import com.jetbrains.python.psi.types.PyCallableType;
import com.jetbrains.python.impl.psi.types.PyTupleType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import one.util.streamex.StreamEx;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@ExtensionImpl
public final class PyArgumentListInspection extends PyInspection {
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameIncorrectCallArguments();
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

        Visitor(ProblemsHolder holder,
                TypeEvalContext context) {
            super(holder, context);
        }

        @Override
        protected ProblemsHolder getHolder() {
            //noinspection ConstantConditions, see Visitor#Visitor(ProblemsHolder, LocalInspectionToolSession)
            return super.getHolder();
        }

        @Override
        public void visitPyArgumentList(PyArgumentList node) {
            inspectPyArgumentList(node, getHolder(), getResolveContext());
        }

        @Override
        public void visitPyStarArgument(PyStarArgument node) {
            if (node.isKeyword()) {
                return;
            }
            checkKnownSizeTupleSpreadInCall(node, getHolder(), getResolveContext());
        }

        @Override
        public void visitPyDecorator(PyDecorator deco) {
            if (deco.hasArgumentList()) {
                return;
            }
            PyCallableType callableType =
                ContainerUtil.getFirstItem(deco.multiResolveCallee(getResolveContext()));
            if (callableType != null) {
                PyCallable callable = callableType.getCallable();
                if (callable == null) {
                    return;
                }
                List<PyCallableParameter> params = callableType.getParameters(myTypeEvalContext);
                if (params == null) {
                    return;
                }

                PyCallableParameter allegedFirstParam = params.isEmpty() ? null : params.get(0) /* TODO consulo: ContainerUtil.getOrElse missing */;
                if (allegedFirstParam == null || allegedFirstParam.isKeywordContainer()) {
                    // no parameters left to pass function implicitly, or wrong param type
                    registerProblem(deco, PyInspectionMessages.problemMessage(msgArgs -> PyLocalize.inspFunctionLacksPositionalArgument(msgArgs[0]), callable.getName())); // TODO: better names for anon lambdas
                }
                else { // possible unfilled params
                    for (int i = 1; i < params.size(); i++) {
                        PyCallableParameter parameter = params.get(i);
                        if (parameter.isKeywordOnlySeparator() || parameter.isPositionOnlySeparator()) {
                            continue;
                        }
                        // param tuples, non-starred or non-default won't do
                        if (!parameter.isKeywordContainer() && !parameter.isPositionalContainer() && !parameter.hasDefaultValue()) {
                            String parameterName = parameter.getName();
                            registerProblem(deco, PyInspectionMessages.problemMessage(msgArgs -> PyLocalize.inspParameterUnfilled(msgArgs[0]), parameterName == null ? "(...)" : parameterName));
                        }
                    }
                }
            }
            // else: this case is handled by arglist visitor
        }

        @Override
        public void visitPyClass(PyClass node) {
            // A class definition implicitly calls `__init_subclass__` of its base classes with the
            // class-definition keyword arguments (e.g. `z="a"` in `class B(A, z="a")`).
            if (node.getArguments().isEmpty()) {
                return;
            }
            PyArgumentList argumentList = node.getSuperClassExpressionList();
            if (argumentList == null) {
                return;
            }
            List<PyCallExpression.PyArgumentsMapping> mappings = PyCallExpressionHelper.mapArguments(node, getResolveContext());
            highlightMappingProblems(argumentList, getHolder(), mappings, myTypeEvalContext);
        }
    }

    private static void inspectPyArgumentList(PyArgumentList node,
                                              ProblemsHolder holder,
                                              PyResolveContext resolveContext) {
        if (node.getParent() instanceof PyClass) return; // `(object)` in `class Foo(object)` is also an arg list, handled in `visitPyClass`
        PyCallExpression call = node.getCallExpression();
        if (call == null) {
            return;
        }

        TypeEvalContext context = resolveContext.getTypeEvalContext();
        List<PyCallExpression.PyArgumentsMapping> mappings = call.multiMapArguments(resolveContext);

        for (PyCallExpression.PyArgumentsMapping mapping : mappings) {
            PyCallableType callableType = mapping.getCallableType();
            if (callableType != null) {
                PyCallable callable = callableType.getCallable();
                if (callable instanceof PyFunction function) {
                    if (objectMethodCallViaSuper(call, function)) {
                        return;
                    }
                }
            }
        }

        highlightMappingProblems(node, holder, mappings, context);
    }

    /**
     * Highlights unexpected arguments, unfilled parameters or otherwise incorrect arguments described by {@code mappings}
     * on the given argument list {@code node} (a call's argument list or a class' base classes list).
     */
    private static void highlightMappingProblems(PyArgumentList node,
                                                 ProblemsHolder holder,
                                                 List<PyCallExpression.PyArgumentsMapping> mappings,
                                                 TypeEvalContext context) {
        if (mappings.isEmpty()) {
            return;
        }

        if (mappings.size() == 1) {
            // A single mapping is incomplete exactly when it has unmapped arguments or unmapped parameters,
            // so the two checks below cover every incomplete mapping.
            PyCallExpression.PyArgumentsMapping mapping = mappings.getFirst();
            if (!mapping.getUnmappedArguments().isEmpty()) {
                highlightUnexpectedArguments(node, holder, mapping);
            }
            if (!mapping.getUnmappedParameters().isEmpty()) {
                highlightUnfilledParameters(node, holder, mapping, context);
            }
        }
        else {
            if (ContainerUtil.all(mappings, mapping -> !mapping.isComplete())) {
                boolean anchorOnClosingParen = ContainerUtil.all(mappings, mapping -> !mapping.getUnmappedParameters().isEmpty()) &&
                                               ContainerUtil.exists(mappings, mapping -> mapping.getUnmappedArguments().isEmpty());
                if (anchorOnClosingParen) {
                    PsiElement closingParen = findClosingParen(node);
                    // An unterminated call is still being typed, so report nothing rather than falling back to the whole list
                    if (closingParen == null) {
                        return;
                    }
                    registerCallMismatchProblem(holder, closingParen, node, mappings, context);
                }
                else {
                    registerCallMismatchProblem(holder, node, node, mappings, context);
                }
            }
        }
    }

    private static @Nullable PsiElement findClosingParen(PyArgumentList node) {
        ASTNode astNode = node.getNode();
        if (astNode == null) {
            return null;
        }
        ASTNode rparNode = astNode.findChildByType(PyTokenTypes.RPAR);
        if (rparNode == null) {
            return null;
        }
        return rparNode.getPsi();
    }

    private static void checkKnownSizeTupleSpreadInCall(PyStarArgument node,
                                                        ProblemsHolder holder,
                                                        PyResolveContext resolveContext) {
        PyExpression expr = node.getExpression();
        if (expr == null) {
            return;
        }

        PyType type = resolveContext.getTypeEvalContext().getType(expr);
        if (!(type instanceof PyTupleType tupleType) || tupleType.isHomogeneous()) {
            return;
        }

        PsiElement parent = node.getParent();
        if (!(parent instanceof PyArgumentList argList)) {
            return;
        }
        if (!(argList.getParent() instanceof PyCallExpression callExpr)) {
            return;
        }

        int nonKeywordStarCount = 0;
        for (PyExpression arg : argList.getArguments()) {
            if (arg instanceof PyStarArgument sa && !sa.isKeyword()) {
                nonKeywordStarCount++;
            }
        }
        if (nonKeywordStarCount != 1) {
            return;
        }

        List<PyCallExpression.PyArgumentsMapping> mappings = callExpr.multiMapArguments(resolveContext);
        if (mappings.size() != 1) {
            return;
        }

        List<PyCallableParameter> variadicParams = mappings.get(0).getParametersMappedToVariadicPositionalArguments();
        if (variadicParams.isEmpty()) {
            return;
        }

        int positionalAfter = 0;
        boolean seenNode = false;
        for (PyExpression arg : argList.getArguments()) {
            if (arg == node) {
                seenNode = true;
                continue;
            }
            if (seenNode && !(arg instanceof PyKeywordArgument) && !(arg instanceof PyStarArgument)) {
                positionalAfter++;
            }
        }

        int filled = tupleType.getElementCount() + positionalAfter;
        if (filled >= variadicParams.size()) {
            return;
        }

        ASTNode argListNode = argList.getNode();
        if (argListNode == null) {
            return;
        }
        ASTNode rparNode = argListNode.findChildByType(PyTokenTypes.RPAR);
        if (rparNode == null) {
            return;
        }
        PsiElement rpar = rparNode.getPsi();

        for (int i = filled; i < variadicParams.size(); i++) {
            PyCallableParameter param = variadicParams.get(i);
            if (param.isPositionalContainer() || param.isKeywordContainer()) {
                break;
            }
            if (!param.hasDefaultValue()) {
                String name = param.getName();
                if (name != null) {
                    registerProblem(holder, rpar, PyInspectionMessages.problemMessage(msgArgs -> PyLocalize.inspParameterUnfilled(msgArgs[0]), name));
                }
            }
        }
    }

    private static void registerProblem(ProblemsHolder holder,
                                        PsiElement element,
                                        String message,
                                        LocalQuickFix ... fixes) {
        holder.registerProblem(element, message, fixes);
    }

    private static void registerProblem(ProblemsHolder holder,
                                        PsiElement element,
                                        PyInspectionMessages.ProblemMessage message) {
        // TODO consulo: ProblemsHolder.problem(...).tooltip(...) missing, the tooltip is not shown
        holder.registerProblem(element, message.description());
    }

    private static boolean objectMethodCallViaSuper(PyCallExpression call, PyFunction function) {
        /*
        Class could be designed to be used in cooperative multiple inheritance
        so `super()` could be resolved to some non-object class that is able to receive passed arguments.

        Example:

            class Shape(object):
                def __init__(self, shapename, **kwds):
                    self.shapename = shapename
                    # in case of ColoredShape the call below will be executed on Colored
                    # so warning should not be raised
                    super(Shape, self).__init__(**kwds)


            class Colored(object):
                def __init__(self, color, **kwds):
                    self.color = color
                    super(Colored, self).__init__(**kwds)


            class ColoredShape(Shape, Colored):
                pass
         */

        PyClass receiverClass = function.getContainingClass();
        if (receiverClass != null && PyUtil.isObjectClass(receiverClass)) {
            PyExpression receiver = call.getCallee() instanceof PyQualifiedExpression callee ? callee.getQualifier() : null;
            if (receiver instanceof PyCallExpression && PyUtil.isSuperCall((PyCallExpression)receiver)) {
                return true;
            }
        }

        return false;
    }

    private static Set<String> getDuplicateKeywordArguments(PyArgumentList node) {
        Set<String> keywordArgumentNames = new HashSet<>();
        Set<String> results = new HashSet<>();
        for (PyExpression argument : node.getArguments()) {
            if (argument instanceof PyKeywordArgument) {
                String keyword = ((PyKeywordArgument)argument).getKeyword();
                if (keywordArgumentNames.contains(keyword)) {
                    results.add(keyword);
                }
                keywordArgumentNames.add(keyword);
            }
        }
        return results;
    }

    private static void highlightUnexpectedArguments(PyArgumentList node,
                                                     ProblemsHolder holder,
                                                     PyCallExpression.PyArgumentsMapping mapping) {
        Set<String> duplicateKeywords = getDuplicateKeywordArguments(node);

        if (holder.isOnTheFly() && !mapping.getUnmappedArguments().isEmpty() && mapping.getUnmappedParameters().isEmpty()) {
            PyCallableType callableType = mapping.getCallableType();
            if (callableType != null) {
                PyCallable callable = callableType.getCallable();
                Project project = node.getProject();
                if (callable instanceof PyFunction && !PyPsiIndexUtil.isNotUnderSourceRoot(project, callable.getContainingFile())) {
                    String message = PyLocalize.inspUnexpectedArgs().get();
                    holder.registerProblem(node, message, ProblemHighlightType.INFORMATION,
                                           PythonUiService.getInstance().createPyChangeSignatureQuickFixForMismatchedCall(mapping));
                }
            }
        }

        for (PyExpression argument : mapping.getUnmappedArguments()) {
            List<LocalQuickFix> quickFixes = Lists.newArrayList(new PyRemoveArgumentQuickFix());
            if (argument instanceof PyKeywordArgument) {
                if (duplicateKeywords.contains(((PyKeywordArgument)argument).getKeyword())) {
                    continue;
                }
                quickFixes.add(new PyRenameArgumentQuickFix());
            }
            registerProblem(holder, argument,
                            PyLocalize.inspUnexpectedArg().get(),
                            quickFixes.toArray(new LocalQuickFix[quickFixes.size() - 1]));
        }
    }

    private static void highlightUnfilledParameters(PyArgumentList node,
                                                    ProblemsHolder holder,
                                                    PyCallExpression.PyArgumentsMapping mapping,
                                                    TypeEvalContext context) {
        PsiElement psi = findClosingParen(node);
        if (psi == null) {
            return;
        }

        if (ContainerUtil.exists(mapping.getUnmappedParameters(), parameter -> parameter.getName() == null)) {
            registerCallMismatchProblem(holder, psi, node, List.of(mapping), context);
        }
        else {
            StreamEx
                .of(mapping.getUnmappedParameters())
                .map(PyCallableParameter::getName)
                .filter(Objects::nonNull)
                .forEach(name -> registerProblem(holder, psi, PyInspectionMessages.problemMessage(msgArgs -> PyLocalize.inspParameterUnfilled(msgArgs[0]), name)));
        }
    }

    /**
     * Reports a call that matches none of several candidate signatures, using the same model and messaging as
     * {@link PyTypeCheckerInspectionProblemRegistrar} via {@link PyMismatchTooltips}: a header naming the
     * common callee, an "Argument types" row built from the provided arguments (a keyword argument shows as
     * {@code keyword=type}; an argument that maps to no candidate stands out), and one "Expected one of" row
     * per candidate built from its parameters (an unfilled parameter stands out).
     *
     * @param element the element to highlight (the argument list, or its closing parenthesis for unfilled params)
     * @param node    the argument list whose arguments populate the "Argument types" row
     */
    private static void registerCallMismatchProblem(ProblemsHolder holder,
                                                    PsiElement element,
                                                    PyArgumentList node,
                                                    List<PyCallExpression.PyArgumentsMapping> mappings,
                                                    TypeEvalContext context) {
        List<PyCallable> callables = ContainerUtil.map(mappings, mapping -> {
            PyCallableType callableType = mapping.getCallableType();
            return callableType == null ? null : callableType.getCallable();
        });

        List<PyMismatchTooltips.Slot> argumentSlots = new ArrayList<>();
        for (PyExpression argument : node.getArguments()) {
            boolean matched = ContainerUtil.exists(mappings, mapping -> !containsIdentity(mapping.getUnmappedArguments(), argument));
            argumentSlots.add(PyMismatchTooltips.Slot.argument(argument, context.getType(argument), context, matched));
        }

        List<List<PyMismatchTooltips.Slot>> expectedRows = new ArrayList<>();
        for (PyCallExpression.PyArgumentsMapping mapping : mappings) {
            List<PyMismatchTooltips.Slot> row = new ArrayList<>();
            PyCallableType callableType = mapping.getCallableType();
            List<PyCallableParameter> parameters = callableType == null ? null : callableType.getParameters(context);
            if (parameters != null) {
                for (PyCallableParameter parameter : parameters) {
                    if (parameter.isPositionOnlySeparator() || parameter.isKeywordOnlySeparator()) {
                        continue;
                    }
                    // An unfilled parameter is wholly missing: highlight its name AND type, not just the type — the whole
                    // parameter is the incompatibility (mirrors the surplus/missing-parameter convention of the structural diff).
                    boolean missing = containsIdentity(mapping.getUnmappedParameters(), parameter);
                    row.add(PyMismatchTooltips.Slot.parameter(parameter, context, !missing, missing));
                }
            }
            expectedRows.add(row);
        }

        PyInspectionMessages.ProblemMessage header = PyMismatchTooltips.header(callables);
        String description = PyMismatchTooltips.description(header, argumentSlots, expectedRows);
        if (holder.isOnTheFly()) {
            // TODO consulo: ProblemBuilder.tooltip(String) missing, PyMismatchTooltips.tooltip(header, argumentSlots, expectedRows) is not shown
            holder.newProblem(LocalizeValue.of(description)).range(element).highlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
                .create();
        }
        else {
            holder.newProblem(LocalizeValue.of(description)).range(element).highlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
                .create();
        }
    }

    private static boolean containsIdentity(List<?> list, Object element) {
        return ContainerUtil.exists(list, candidate -> candidate == element);
    }
}
