// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.codeFragment;

import com.jetbrains.python.ast.PyAstFunction;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.codeInsight.PyPsiIndexUtil;
import com.jetbrains.python.impl.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.impl.codeInsight.controlflow.ReadWriteInstruction;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.Scope;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.impl.documentation.PythonDocumentationProvider;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.types.PyLiteralType;
import com.jetbrains.python.impl.psi.types.PyNoneTypeKt;
import com.jetbrains.python.impl.psi.types.PyTupleType;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyBreakStatement;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyContinueStatement;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyExceptPart;
import com.jetbrains.python.psi.PyFinallyPart;
import com.jetbrains.python.psi.PyFromImportStatement;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.PyLoopStatement;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.PyParameterList;
import com.jetbrains.python.psi.PyReturnStatement;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.PyTypedElement;
import com.jetbrains.python.psi.PyYieldExpression;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.types.PyAnyType;
import com.jetbrains.python.impl.psi.types.PyStructuralType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.language.controlFlow.ConditionalInstruction;
import consulo.language.controlFlow.ControlFlow;
import consulo.language.controlFlow.Instruction;
import consulo.language.editor.codeFragment.CannotCreateCodeFragmentException;
import consulo.language.editor.codeFragment.CodeFragmentUtil;
import consulo.language.editor.codeFragment.Position;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiNamedElement;
import consulo.language.psi.PsiPolyVariantReference;
import consulo.language.psi.PsiReference;
import consulo.language.psi.ResolveResult;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.Pair;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.jetbrains.python.impl.psi.types.PyTypeUtilKt.isUnknown;

public final class PyCodeFragmentUtil {
    private PyCodeFragmentUtil() {
    }

    public static PyCodeFragment createCodeFragment(ScopeOwner owner,
                                                   PsiElement startInScope,
                                                   PsiElement endInScope,
                                                   @Nullable PsiElement singleExpression)
        throws CannotCreateCodeFragmentException {

        int start = startInScope.getTextOffset();
        int end = endInScope.getTextOffset() + endInScope.getTextLength();
        ControlFlow flow = ControlFlowCache.getControlFlow(owner);
        List<Instruction> graph = Arrays.asList(flow.getInstructions());
        List<Instruction> subGraph = getFragmentSubGraph(graph, start, end);
        AnalysisResult subGraphAnalysis = analyseSubGraph(subGraph, start, end);
        if ((subGraphAnalysis.regularExits > 0 && subGraphAnalysis.returns > 0) ||
                subGraphAnalysis.outerLoopBreaks > 0) {
            throw new CannotCreateCodeFragmentException(PyLocalize.refactoringExtractMethodErrorInterruptedExecutionFlow().get());
        }
        if (subGraphAnalysis.starImports > 0) {
            throw new CannotCreateCodeFragmentException(PyLocalize.refactoringExtractMethodErrorStarImport().get());
        }

        Set<String> globalWrites = getGlobalWrites(subGraph, owner);
        Set<String> nonlocalWrites = getNonlocalWrites(subGraph, owner);

        TypeEvalContext context = TypeEvalContext.userInitiated(startInScope.getProject(), startInScope.getContainingFile());
        Set<String> inputNames = new LinkedHashSet<>();
        Map<String, Pair<String, PyType>> inputTypes = new HashMap<>();
        for (PsiElement element : filterElementsInScope(getInputElements(subGraph, graph), owner)) {
            // Ignore "self" and "cls", they are generated automatically when extracting any method fragment
            if (resolvesToBoundMethodParameter(element)) {
                continue;
            }
            Pair<String, PyType> variable = getVariable(globalWrites, nonlocalWrites, element, inputNames, context);
            if (variable != null && !isUnknown(variable.second)) {
                String typeName = PythonDocumentationProvider.getTypeHint(variable.second, context);
                inputTypes.put(variable.first, Pair.create(typeName, variable.second));
            }
        }

        Set<String> outputNames = new LinkedHashSet<>();
        List<PyType> outputTypes = new ArrayList<>();
        for (PsiElement element : getOutputElements(subGraph, graph)) {
            Pair<String, PyType> variable = getVariable(globalWrites, nonlocalWrites, element, outputNames, context);
            if (variable != null) {
                outputTypes.add(variable.second);
            }
        }
        if (singleExpression != null) {
            PyType returnType = getType(singleExpression, context);
            outputTypes.add(returnType);
        }
        else if (PsiTreeUtil.getParentOfType(endInScope, PyStatement.class) instanceof PyReturnStatement returnStatement
                          && returnStatement.getExpression() != null) {
            PyType returnType = getType(returnStatement.getExpression(), context);
            outputTypes.add(returnType);
        }
        String outputTypeName = getOutputTypeName(startInScope, outputTypes, context);

        boolean yieldsFound = subGraphAnalysis.yieldExpressions > 0;
        if (yieldsFound && LanguageLevel.forElement(owner).isPython2()) {
            throw new CannotCreateCodeFragmentException(PyLocalize.refactoringExtractMethodErrorYield().get());
        }
        boolean isAsync = owner instanceof PyFunction && ((PyFunction)owner).isAsync();

        return new PyCodeFragment(inputNames, outputNames, inputTypes, outputTypeName, new LinkedHashSet<>(outputTypes),
                                  globalWrites, nonlocalWrites, subGraphAnalysis.returns > 0, yieldsFound, isAsync);
    }

    private static @Nullable Pair<String, PyType> getVariable(Set<String> globalWrites,
                                                              Set<String> nonlocalWrites,
                                                              PsiElement element,
                                                              Set<String> variableNames,
                                                              TypeEvalContext context) {
        String name = getName(element);
        if (name == null || globalWrites.contains(name) || nonlocalWrites.contains(name) || variableNames.contains(name)) {
            return null;
        }
        PyType type = getType(element, context);
        variableNames.add(name);
        return Pair.create(name, type);
    }

    private static @Nullable PyType getType(PsiElement element, TypeEvalContext context) {
        if (element instanceof PyTypedElement typedElement) {
            PyType type = PyLiteralType.upcastLiteralToClass(context.getType(typedElement));
            if (!isUnknown(type) && !(type instanceof PyStructuralType) && !PyNoneTypeKt.isNoneType(type)) {
                return type;
            }
        }
        return PyAnyType.getUnknown();
    }

    private static @Nullable String getOutputTypeName(PsiElement startInScope,
                                                              List<PyType> outputTypes,
                                                              TypeEvalContext context) {

        return switch (outputTypes.size()) {
            case 0 -> null;
            case 1 -> PythonDocumentationProvider.getTypeHint(outputTypes.get(0), context);
            default -> {
                PyType returnType = PyTupleType.create(startInScope, outputTypes);
                yield PythonDocumentationProvider.getTypeHint(returnType, context);
            }
        };
    }

    private static boolean resolvesToBoundMethodParameter(PsiElement element) {
        if (PyPsiUtils.isMethodContext(element)) {
            PyFunction function = PsiTreeUtil.getParentOfType(element, PyFunction.class);
            if (function != null) {
                PsiReference reference = element.getReference();
                if (reference != null) {
                    PsiElement resolved = reference.resolve();
                    if (resolved instanceof PyParameter) {
                        PyParameterList parameterList = PsiTreeUtil.getParentOfType(resolved, PyParameterList.class);
                        if (parameterList != null) {
                            PyParameter[] parameters = parameterList.getParameters();
                            if (parameters.length > 0) {
                                if (resolved == parameters[0]) {
                                    PyFunction.Modifier modifier = function.getModifier();
                                    if (modifier == null || modifier == PyAstFunction.Modifier.CLASSMETHOD) {
                                        return true;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    private static @Nullable String getName(PsiElement element) {
        if (element instanceof PsiNamedElement) {
            return ((PsiNamedElement)element).getName();
        }
        else if (element instanceof PyImportElement) {
            return ((PyImportElement)element).getVisibleName();
        }
        else if (element instanceof PyElement) {
            return ((PyElement)element).getName();
        }
        return null;
    }

    private static List<Instruction> getFragmentSubGraph(List<Instruction> graph, int start, int end) {
        List<Instruction> instructions = new ArrayList<>();
        for (Instruction instruction : graph) {
            PsiElement element;
            if (instruction instanceof ConditionalInstruction conditionalInstruction) {
                element = conditionalInstruction.getCondition();
            }
            else {
                element = instruction.getElement();
            }
            if (element != null) {
                if (CodeFragmentUtil.getPosition(element, start, end) == Position.INSIDE) {
                    instructions.add(instruction);
                }
            }
        }
        // Hack for including inner assert type instructions that can point to elements outside of the selected scope
        for (Instruction instruction : graph) {
            if (instruction instanceof ReadWriteInstruction readWriteInstruction) {
                if (readWriteInstruction.getAccess().isAssertTypeAccess()) {
                    boolean innerAssertType = true;
                    for (Instruction next : readWriteInstruction.allSucc()) {
                        if (!instructions.contains(next)) {
                            innerAssertType = false;
                            break;
                        }
                    }
                    if (innerAssertType && !instructions.contains(instruction)) {
                        instructions.add(instruction);
                    }
                }
            }
        }
        return instructions;
    }

    private static class AnalysisResult {
        private final int starImports;
        private final int regularExits;
        private final int returns;
        private final int outerLoopBreaks;
        private final int yieldExpressions;

        AnalysisResult(int starImports, int returns, int regularExits, int outerLoopBreaks, int yieldExpressions) {
            this.starImports = starImports;
            this.regularExits = regularExits;
            this.returns = returns;
            this.outerLoopBreaks = outerLoopBreaks;
            this.yieldExpressions = yieldExpressions;
        }
    }

    private static AnalysisResult analyseSubGraph(List<Instruction> subGraph, int start, int end) {
        int returnSources = 0;
        int regularSources = 0;
        int starImports = 0;
        int outerLoopBreaks = 0;
        int yieldExpressions = 0;

        for (Pair<Instruction, Instruction> edge : getOutgoingEdges(subGraph)) {
            Instruction sourceInstruction = edge.getFirst();
            Instruction targetInstruction = edge.getSecond();
            PsiElement source = sourceInstruction.getElement();
            PsiElement target = targetInstruction.getElement();

            PyReturnStatement returnStatement = PsiTreeUtil.getParentOfType(source, PyReturnStatement.class, false);
            boolean isExceptTarget = target instanceof PyExceptPart || target instanceof PyFinallyPart;

            if (returnStatement != null && CodeFragmentUtil.getPosition(returnStatement, start, end) == Position.INSIDE) {
                returnSources++;
            }
            else if (!isExceptTarget) {
                regularSources++;
            }
        }

        Set<PsiElement> subGraphElements = getSubGraphElements(subGraph);
        for (PsiElement element : subGraphElements) {
            if (element instanceof PyFromImportStatement fromImportStatement) {
                if (fromImportStatement.getStarImportElement() != null) {
                    starImports++;
                }
            }
            if (element instanceof PyContinueStatement || element instanceof PyBreakStatement) {
                PyLoopStatement loopStatement = PyUtil.getCorrespondingLoop(element);
                if (loopStatement != null && !subGraphElements.contains(loopStatement)) {
                    outerLoopBreaks++;
                }
            }
            if (element instanceof PyYieldExpression) {
                yieldExpressions++;
            }
        }

        return new AnalysisResult(starImports, returnSources, regularSources, outerLoopBreaks, yieldExpressions);
    }

    private static Set<Pair<Instruction, Instruction>> getOutgoingEdges(Collection<Instruction> subGraph) {
        Set<Pair<Instruction, Instruction>> outgoing = new HashSet<>();
        for (Instruction instruction : subGraph) {
            for (Instruction next : instruction.allSucc()) {
                if (!subGraph.contains(next)) {
                    outgoing.add(Pair.create(instruction, next));
                }
            }
        }
        return outgoing;
    }

    public static List<PsiElement> getInputElements(List<Instruction> subGraph, List<Instruction> graph) {
        List<PsiElement> result = new ArrayList<>();
        Set<PsiElement> subGraphElements = getSubGraphElements(subGraph);
        for (Instruction instruction : getReadInstructions(subGraph)) {
            PsiElement element = instruction.getElement();
            if (element != null) {
                PsiReference reference = element.getReference();
                if (reference != null) {
                    for (PsiElement resolved : multiResolve(reference)) {
                        if (!subGraphElements.contains(resolved)) {
                            result.add(element);
                            break;
                        }
                    }
                }
            }
        }
        List<PsiElement> outputElements = getOutputElements(subGraph, graph);
        for (Instruction instruction : getWriteInstructions(subGraph)) {
            PsiElement element = instruction.getElement();
            if (element != null) {
                PsiReference reference = element.getReference();
                if (reference != null) {
                    for (PsiElement resolved : multiResolve(reference)) {
                        if (!subGraphElements.contains(resolved) && outputElements.contains(element)) {
                            result.add(element);
                        }
                    }
                }
            }
        }
        return result;
    }

    private static List<PsiElement> getOutputElements(List<Instruction> subGraph, List<Instruction> graph) {
        List<PsiElement> result = new ArrayList<>();
        List<Instruction> outerGraph = new ArrayList<>();
        for (Instruction instruction : graph) {
            if (!subGraph.contains(instruction)) {
                outerGraph.add(instruction);
            }
        }
        Set<PsiElement> subGraphElements = getSubGraphElements(subGraph);
        for (Instruction instruction : getReadInstructions(outerGraph)) {
            PsiElement element = instruction.getElement();
            if (element != null) {
                PsiReference reference = element.getReference();
                if (reference != null) {
                    for (PsiElement resolved : multiResolve(reference)) {
                        if (subGraphElements.contains(resolved)) {
                            result.add(resolved);
                        }
                    }
                }
            }
        }
        return result;
    }

    private static List<PsiElement> filterElementsInScope(Collection<PsiElement> elements, ScopeOwner owner) {
        List<PsiElement> result = new ArrayList<>();
        for (PsiElement element : elements) {
            PsiReference reference = element.getReference();
            if (reference != null) {
                PsiElement resolved = reference.resolve();
                if (resolved != null && ScopeUtil.getScopeOwner(resolved) == owner && !(owner instanceof PsiFile)) {
                    result.add(element);
                }
            }
        }
        return result;
    }

    private static Set<PsiElement> getSubGraphElements(List<Instruction> subGraph) {
        Set<PsiElement> result = new HashSet<>();
        for (Instruction instruction : subGraph) {
            PsiElement element = instruction.getElement();
            if (element != null) {
                result.add(element);
            }
        }
        return result;
    }

    private static Set<String> getGlobalWrites(List<Instruction> instructions, ScopeOwner owner) {
        Scope scope = ControlFlowCache.getScope(owner);
        Set<String> globalWrites = new LinkedHashSet<>();
        for (Instruction instruction : getWriteInstructions(instructions)) {
            if (instruction instanceof ReadWriteInstruction) {
                String name = ((ReadWriteInstruction)instruction).getName();
                PsiElement element = instruction.getElement();
                if (scope.isGlobal(name) ||
                        (owner instanceof PsiFile && element instanceof PsiNamedElement && isUsedOutside((PsiNamedElement)element, instructions))) {
                    globalWrites.add(name);
                }
            }
        }
        return globalWrites;
    }

    private static boolean isUsedOutside(PsiNamedElement element, List<Instruction> subGraph) {
        Set<PsiElement> subGraphElements = getSubGraphElements(subGraph);
        return ContainerUtil.exists(PyPsiIndexUtil.findUsages(element, false),
                                    usageInfo -> !subGraphElements.contains(usageInfo.getElement()));
    }

    private static Set<String> getNonlocalWrites(List<Instruction> instructions, ScopeOwner owner) {
        Scope scope = ControlFlowCache.getScope(owner);
        Set<String> nonlocalWrites = new LinkedHashSet<>();
        for (Instruction instruction : getWriteInstructions(instructions)) {
            if (instruction instanceof ReadWriteInstruction) {
                String name = ((ReadWriteInstruction)instruction).getName();
                if (scope.isNonlocal(name)) {
                    nonlocalWrites.add(name);
                }
            }
        }
        return nonlocalWrites;
    }

    private static List<PsiElement> multiResolve(PsiReference reference) {
        if (reference instanceof PsiPolyVariantReference) {
            ResolveResult[] results = ((PsiPolyVariantReference)reference).multiResolve(false);
            List<PsiElement> resolved = new ArrayList<>();
            for (ResolveResult result : results) {
                PsiElement element = result.getElement();
                if (element != null) {
                    resolved.add(element);
                }
            }
            for (PsiElement element : resolved) {
                if (element instanceof PyClass) {
                    return Collections.singletonList(element);
                }
            }
            return resolved;
        }
        PsiElement element = reference.resolve();
        if (element != null) {
            return Collections.singletonList(element);
        }
        return Collections.emptyList();
    }

    private static List<Instruction> getReadInstructions(List<Instruction> subGraph) {
        List<Instruction> result = new ArrayList<>();
        for (Instruction instruction : subGraph) {
            if (instruction instanceof ReadWriteInstruction readWriteInstruction) {
                if (readWriteInstruction.getAccess().isReadAccess()) {
                    result.add(readWriteInstruction);
                }
            }
        }
        return result;
    }

    private static List<Instruction> getWriteInstructions(List<Instruction> subGraph) {
        List<Instruction> result = new ArrayList<>();
        for (Instruction instruction : subGraph) {
            if (instruction instanceof ReadWriteInstruction readWriteInstruction) {
                if (readWriteInstruction.getAccess().isWriteAccess()) {
                    result.add(readWriteInstruction);
                }
            }
        }
        return result;
    }
}
