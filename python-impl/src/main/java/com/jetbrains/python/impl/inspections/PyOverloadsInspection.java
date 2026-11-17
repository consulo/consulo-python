// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.ast.PyAstFunction;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.codeInsight.typing.PyProtocolsKt;
import com.jetbrains.python.impl.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.impl.documentation.PythonDocumentationProvider;
import com.jetbrains.python.impl.psi.PyKnownDecorator;
import com.jetbrains.python.impl.psi.PyKnownDecoratorUtil;
import com.jetbrains.python.impl.psi.impl.PyClassImpl;
import com.jetbrains.python.impl.psi.types.PyCallableParameterListTypeImpl;
import com.jetbrains.python.impl.psi.types.PyTypeChecker;
import com.jetbrains.python.impl.pyi.PyiFile;
import com.jetbrains.python.impl.pyi.PyiUtil;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyDecoratable;
import com.jetbrains.python.psi.PyDecorator;
import com.jetbrains.python.psi.PyDecoratorList;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.types.PyCallableParameter;
import com.jetbrains.python.psi.types.PyCallableType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.util.HtmlBuilder;
import consulo.application.util.HtmlChunk;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.lang.xml.XmlStringUtil;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

@ExtensionImpl
public class PyOverloadsInspection extends PyInspection {
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameOverloadsInRegularPythonFiles();
    }

    @Override
    public LocalizeValue getGroupDisplayName() {
        return PyLocalize.inspGroupPythonTypeHinting();
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
        private static final int OVERLOADS_ANALYSIS_LIMIT = 30;

        Visitor(ProblemsHolder holder, TypeEvalContext context) {
            super(holder, context);
        }

        @Override
        @RequiredReadAction
        public void visitPyClass(PyClass node) {
            processScope(node, processor -> node.visitMethods(processor, false, myTypeEvalContext));
        }

        @Override
        @RequiredReadAction
        public void visitPyFile(PyFile node) {
            processScope(node, processor -> node.getTopLevelFunctions().forEach(processor::test));
        }

        @RequiredReadAction
        private void processScope(ScopeOwner owner, Consumer<GroupingFunctionsByNameProcessor> processorUsage) {
            GroupingFunctionsByNameProcessor processor = new GroupingFunctionsByNameProcessor();
            processorUsage.accept(processor);
            processor.getResult().values().forEach(it -> processSameNameFunctions(owner, it));
        }

        @RequiredReadAction
        private void processSameNameFunctions(ScopeOwner owner, List<PyFunction> functions) {
            List<PyFunction> overloads = new ArrayList<>();
            List<PyFunction> implementations = new ArrayList<>();
            for (PyFunction function : functions) {
                if (PyiUtil.isOverload(function, myTypeEvalContext)) {
                    overloads.add(function);
                }
                else {
                    implementations.add(function);
                }
            }

            if (overloads.isEmpty()) {
                return;
            }

            if (overloads.size() == 1) {
                registerProblem(overloads.get(0).getNameIdentifier(),
                                PyLocalize.inspOverloadsAtLeastTwoOverloadsMustBePresent(owner instanceof PyClass ? 1 : 0).get());
            }

            PyFunction implementation = implementations.isEmpty() ? null : implementations.get(implementations.size() - 1);

            checkClassMethodAndStaticMethodConsistency(overloads, implementation);

            checkOverrideAndFinal(overloads, implementation);

            checkOverloadsOverlapping(overloads);

            boolean requiresImplementation = true;
            if (owner.getContainingFile() instanceof PyiFile) {
                requiresImplementation = false;
            }
            else if (owner instanceof PyClass pyClass) {
                if (PyProtocolsKt.isProtocol(pyClass, myTypeEvalContext)) {
                    requiresImplementation = false;
                }
                else {
                    if (PyClassImpl.canHaveAbstractMethods(pyClass, myTypeEvalContext)) {
                        if (overloads.stream().allMatch(it -> PyKnownDecoratorUtil.hasAbstractDecorator(it, myTypeEvalContext))) {
                            requiresImplementation = false;
                        }
                    }
                }
            }

            PyFunction lastFunction = functions.get(functions.size() - 1);
            if (requiresImplementation && implementation != lastFunction) {
                PyFunction problemElement = implementation == null ? functions.get(0) : lastFunction;
                registerProblem(problemElement.getNameIdentifier(),
                                PyLocalize.inspOverloadsSeriesOverloadsShouldAlwaysBeFollowedByImplementation(
                                    owner instanceof PyClass ? 1 : 0).get());
            }

            if (implementation != null) {
                PyCallableParameterListTypeImpl implementationInputSignature =
                    new PyCallableParameterListTypeImpl(implementation.getParameters(myTypeEvalContext));
                for (PyFunction overload : overloads) {
                    PyCallableParameterListTypeImpl overloadInputSignature =
                        new PyCallableParameterListTypeImpl(overload.getParameters(myTypeEvalContext));
                    if (!PyTypeChecker.match(overloadInputSignature, implementationInputSignature, myTypeEvalContext)) {
                        String message = PyLocalize.inspOverloadsThisOverloadSignatureNotCompatibleWithImplementation(
                            owner instanceof PyClass ? 1 : 0).get();
                        String diff = PyTypeDiff.paramsDiffTooltip(overload.getParameters(myTypeEvalContext),
                                                                   implementation.getParameters(myTypeEvalContext),
                                                                   HtmlChunk.text(message), myTypeEvalContext);
                        registerSignatureMismatch(overload.getNameIdentifier(), overloadInputSignature, implementationInputSignature,
                                                  new PyInspectionMessages.ProblemMessage(message, diff));
                    }
                }
            }
        }

        /**
         * Registers an overload-incompatibility problem: the aligned parameter diff ({@code message}'s tooltip) with the
         * breakdown ({@link PyTypeChecker#explainMismatch}) appended below it as a separate on-the-fly HTML tooltip, while
         * keeping the one-line description flat.
         */
        @RequiredReadAction
        private void registerSignatureMismatch(@Nullable PsiElement element,
                                               @Nullable PyType expected,
                                               @Nullable PyType actual,
                                               PyInspectionMessages.ProblemMessage message) {
            if (element == null) {
                return;
            }
            registerProblemWithTooltip(element, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, LocalQuickFix.EMPTY_ARRAY,
                                       () -> PyTypeCheckerInspectionProblemRegistrar.breakdownTooltip(message, expected, actual,
                                                                                                     myTypeEvalContext, element));
        }

        @RequiredReadAction
        private void checkClassMethodAndStaticMethodConsistency(List<PyFunction> overloads, @Nullable PyFunction implementation) {
            Set<PyAstFunction.Modifier> modifiers = EnumSet.noneOf(PyAstFunction.Modifier.class);
            for (PyFunction overload : overloads) {
                PyAstFunction.Modifier modifier = overload.getModifier();
                if (modifier != null) {
                    modifiers.add(modifier);
                }
            }
            List<PyFunction> functions = new ArrayList<>(overloads);
            if (implementation != null) {
                functions.add(implementation);
            }
            for (PyFunction function : functions) {
                PyAstFunction.Modifier modifier = function.getModifier();
                if (modifiers.contains(PyAstFunction.Modifier.CLASSMETHOD) && modifier != PyAstFunction.Modifier.CLASSMETHOD) {
                    registerProblem(function.getNameIdentifier(), PyLocalize.inspOverloadsUseClassmethodInconsistently().get());
                }
                if (modifiers.contains(PyAstFunction.Modifier.STATICMETHOD) && modifier != PyAstFunction.Modifier.STATICMETHOD) {
                    registerProblem(function.getNameIdentifier(), PyLocalize.inspOverloadsUseStaticmethodInconsistently().get());
                }
            }
        }

        @RequiredReadAction
        private void checkOverrideAndFinal(List<PyFunction> overloads, @Nullable PyFunction implementation) {
            if (implementation == null) {
                for (PyFunction overload : overloads.subList(1, overloads.size())) {
                    if (isOverride(overload)) {
                        registerProblem(overload.getNameIdentifier(),
                                        PyLocalize.inspOverloadsOverrideShouldBePlacedOnlyOnTheFirstOverload().get());
                    }
                    if (PyTypingTypeProvider.isFinal((PyDecoratable) overload, myTypeEvalContext)) {
                        registerProblem(overload.getNameIdentifier(),
                                        PyLocalize.inspOverloadsFinalShouldBePlacedOnlyOnTheFirstOverload().get());
                    }
                }
            }
            else {
                for (PyFunction overload : overloads) {
                    if (isOverride(overload)) {
                        registerProblem(overload.getNameIdentifier(),
                                        PyLocalize.inspOverloadsOverrideShouldBePlacedOnTheImplementation().get());
                    }
                    if (PyTypingTypeProvider.isFinal((PyDecoratable) overload, myTypeEvalContext)) {
                        registerProblem(overload.getNameIdentifier(),
                                        PyLocalize.inspOverloadsFinalShouldBePlacedOnTheImplementation().get());
                    }
                }
            }
        }

        private boolean isOverride(PyFunction function) {
            PyDecoratorList decoratorList = function.getDecoratorList();
            if (decoratorList == null) {
                return false;
            }
            for (PyDecorator decorator : decoratorList.getDecorators()) {
                if (PyKnownDecoratorUtil.knownDecoratorsOf(decorator, myTypeEvalContext)
                    .containsAny(PyKnownDecorator.TYPING_OVERRIDE, PyKnownDecorator.TYPING_EXTENSIONS_OVERRIDE)) {
                    return true;
                }
            }
            return false;
        }

        @RequiredReadAction
        private void checkOverloadsOverlapping(List<PyFunction> overloads) {
            // This analysis is N^2 so limited to a reasonable number of overloads to prevent performance issues
            if (overloads.size() < 2 || overloads.size() > OVERLOADS_ANALYSIS_LIMIT) {
                return;
            }

            // __get__ method should be special-cased
            if (PyNames.DUNDER_GET.equals(overloads.get(0).getName())) {
                return;
            }

            for (int i = 0; i < overloads.size(); i++) {
                PyFunction current = overloads.get(i);
                if (!(myTypeEvalContext.getType(current) instanceof PyCallableType currCallableType)) {
                    continue;
                }
                List<PyCallableParameter> currParams = currCallableType.getParameters(myTypeEvalContext);
                if (currParams == null) {
                    continue;
                }
                PyType currReturnType = currCallableType.getReturnType(myTypeEvalContext);
                PyCallableParameterListTypeImpl currInputSignature = new PyCallableParameterListTypeImpl(currParams);

                for (int j = i + 1; j < overloads.size(); j++) {
                    PyFunction next = overloads.get(j);
                    if (!(myTypeEvalContext.getType(next) instanceof PyCallableType nextCallableType)) {
                        continue;
                    }
                    List<PyCallableParameter> nextParams = nextCallableType.getParameters(myTypeEvalContext);
                    if (nextParams == null) {
                        continue;
                    }
                    PyType nextReturnType = nextCallableType.getReturnType(myTypeEvalContext);

                    PyCallableParameterListTypeImpl nextOverloadInputSignature = new PyCallableParameterListTypeImpl(nextParams);

                    if (PyTypeChecker.match(nextOverloadInputSignature, currInputSignature, myTypeEvalContext)) {
                        String signatureRepresentation = PythonDocumentationProvider.getTypeName(nextCallableType, myTypeEvalContext);
                        String message =
                            buildMessage(PyLocalize.inspOverloadsOverloadOverlappedByABroaderType(i + 1).get(),
                                         signatureRepresentation);
                        registerProblem(next.getNameIdentifier(), message);
                    }
                    else if (PyTypeChecker.match(currInputSignature, nextOverloadInputSignature, myTypeEvalContext)) {
                        if (!PyTypeChecker.match(nextReturnType, currReturnType, myTypeEvalContext)) {
                            String signatureRepresentation = PythonDocumentationProvider.getTypeName(currCallableType, myTypeEvalContext);
                            String headline = PyLocalize.inspOverloadsOverloadOverlapsWithIncompatibleReturnType(j + 1).get();
                            String message = buildMessage(headline, signatureRepresentation);
                            String diff = PyTypeDiff.diffTooltip(nextCallableType, currCallableType, HtmlChunk.text(headline), myTypeEvalContext);
                            if (diff != null) {
                                registerProblem(current.getNameIdentifier(), new PyInspectionMessages.ProblemMessage(message, diff));
                            }
                            else {
                                registerProblem(current.getNameIdentifier(), message);
                            }
                        }
                    }
                }
            }
        }

        private static String buildMessage(String msg, String signature) {
            return XmlStringUtil.wrapInHtml(new HtmlBuilder()
                                                .append(msg)
                                                .append(HtmlChunk.br())
                                                .append(PyLocalize.inspOverloadsOverloadConflictingSignature(signature).get())
                                                .toFragment()
                                                .toString());
        }
    }

    private static class GroupingFunctionsByNameProcessor implements Predicate<PyFunction> {
        private static final Comparator<PyFunction> BY_OFFSET = (f1, f2) -> f1.getTextOffset() - f2.getTextOffset();

        private final Map<String, List<PyFunction>> result = new HashMap<>();

        Map<String, List<PyFunction>> getResult() {
            return result;
        }

        @Override
        public boolean test(@Nullable PyFunction t) {
            String name = t != null ? t.getName() : null;
            if (name != null) {
                // JB: SortedList ordered by text offset (consulo SortedList is not exported) - keep the list sorted on insertion
                List<PyFunction> list = result.computeIfAbsent(name, k -> new ArrayList<>());
                int index = 0;
                while (index < list.size() && BY_OFFSET.compare(list.get(index), t) <= 0) {
                    index++;
                }
                list.add(index, t);
            }

            return true;
        }
    }
}
