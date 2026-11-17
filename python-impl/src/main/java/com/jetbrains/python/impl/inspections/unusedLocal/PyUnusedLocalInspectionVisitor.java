// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.unusedLocal;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.impl.codeInsight.controlflow.ReadWriteInstruction;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.Scope;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.impl.codeInsight.functionTypeComments.PyFunctionTypeAnnotationDialect;
import com.jetbrains.python.impl.codeInsight.typeHints.PyTypeHintDialect;
import com.jetbrains.python.impl.inspections.PyConstructorSignatureUtil;
import com.jetbrains.python.impl.inspections.PyInspectionMessages;
import com.jetbrains.python.impl.inspections.PyInspectionMessages.CodifiedParam;
import com.jetbrains.python.impl.inspections.PyInspectionVisitor;
import com.jetbrains.python.impl.inspections.quickfix.AddFieldQuickFix;
import com.jetbrains.python.impl.inspections.quickfix.PyRemoveAssignmentStatementTargetQuickFix;
import com.jetbrains.python.impl.inspections.quickfix.PyRemoveExceptionTargetQuickFix;
import com.jetbrains.python.impl.inspections.quickfix.PyRemoveParameterQuickFix;
import com.jetbrains.python.impl.inspections.quickfix.PyRemoveStatementQuickFix;
import com.jetbrains.python.impl.inspections.quickfix.PyRemoveTypeParameterQuickFix;
import com.jetbrains.python.impl.inspections.quickfix.PyRemoveWithTargetQuickFix;
import com.jetbrains.python.impl.psi.PyKnownDecoratorUtil;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.PyAugAssignmentStatementNavigator;
import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import com.jetbrains.python.impl.psi.impl.PyExceptPartNavigator;
import com.jetbrains.python.impl.psi.impl.PyForStatementNavigator;
import com.jetbrains.python.impl.psi.impl.PyImportStatementNavigator;
import com.jetbrains.python.impl.psi.impl.PyTypeDeclarationStatementNavigator;
import com.jetbrains.python.impl.psi.search.PyOverridingMethodsSearch;
import com.jetbrains.python.impl.psi.search.PySuperMethodsSearch;
import com.jetbrains.python.impl.pyi.PyiUtil;
import com.jetbrains.python.inspections.PyInspectionExtension;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyAugAssignmentStatement;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyCallable;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyComprehensionElement;
import com.jetbrains.python.psi.PyDocStringOwner;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExceptPart;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyExpressionCodeFragment;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyForStatement;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyLambdaExpression;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.PyParameterList;
import com.jetbrains.python.psi.PyRecursiveElementVisitor;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PySingleStarParameter;
import com.jetbrains.python.psi.PyStarExpression;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.PyTupleExpression;
import com.jetbrains.python.psi.PyTypeAliasStatement;
import com.jetbrains.python.psi.PyTypeParameter;
import com.jetbrains.python.psi.PyTypeParameterList;
import com.jetbrains.python.psi.PyTypeParameterListOwner;
import com.jetbrains.python.psi.PyWithItem;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.document.util.TextRange;
import consulo.language.Language;
import consulo.language.controlFlow.ControlFlowUtil;
import consulo.language.controlFlow.Instruction;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.inject.InjectedLanguageManager;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiNamedElement;
import consulo.language.psi.ResolveResult;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.ContainerUtil;
import consulo.util.dataholder.Key;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import one.util.streamex.EntryStream;
import one.util.streamex.StreamEx;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class PyUnusedLocalInspectionVisitor extends PyInspectionVisitor {
    private final boolean myIgnoreTupleUnpacking;
    private final boolean myIgnoreLambdaParameters;
    private final boolean myIgnoreRangeIterationVariables;
    private final boolean myIgnoreVariablesStartingWithUnderscore;
    private final ReportTarget myReportTarget;
    private final SharedAnalysis mySharedAnalysis;

    /**
     * Which kind of unused element this visitor reports; each kind is exposed as a separate inspection.
     */
    public enum ReportTarget {
        LOCALS, PARAMETERS, FUNCTIONS
    }

    /**
     * Analysis state shared by the unused-locals, unused-parameter, and unused-function inspections. All three perform the same
     * control-flow analysis but report different element kinds; sharing this state through the {@link LocalInspectionToolSession}
     * keeps the heavy analysis running once per file instead of once per inspection.
     */
    public static final class SharedAnalysis {
        // Names defined directly in a scope. They all belong to this scope.
        private final Map<ScopeOwner, Set<PsiElement>> scopeWrites = new ConcurrentHashMap<>();
        // Names read directly in a scope. They might belong to some outer scope.
        private final Map<ScopeOwner, Set<PsiElement>> scopeReads = new ConcurrentHashMap<>();
        private volatile @Nullable Set<PsiElement> unusedElements;
    }

    private static final Key<SharedAnalysis> SHARED_ANALYSIS_KEY = Key.create("PyUnusedLocal.SharedAnalysis");

    /**
     * Returns the analysis state shared by the unused-symbol inspections running on this session, creating it on first use.
     */
    public static SharedAnalysis getSharedAnalysis(LocalInspectionToolSession session) {
        SharedAnalysis analysis = session.getUserData(SHARED_ANALYSIS_KEY);
        if (analysis == null) {
            synchronized (SHARED_ANALYSIS_KEY) {
                analysis = session.getUserData(SHARED_ANALYSIS_KEY);
                if (analysis == null) {
                    analysis = new SharedAnalysis();
                    session.putUserData(SHARED_ANALYSIS_KEY, analysis);
                }
            }
        }
        return analysis;
    }

    // Names defined directly in a scope. They all belong to this scope.
    private final Map<ScopeOwner, Set<PsiElement>> myScopeWrites;
    // Names read directly in a scope. They might belong to some outer scope.
    private final Map<ScopeOwner, Set<PsiElement>> myScopeReads;

    public PyUnusedLocalInspectionVisitor(ProblemsHolder holder,
                                          boolean ignoreTupleUnpacking,
                                          boolean ignoreLambdaParameters,
                                          boolean ignoreRangeIterationVariables,
                                          boolean ignoreVariablesStartingWithUnderscore,
                                          ReportTarget reportTarget,
                                          SharedAnalysis sharedAnalysis,
                                          TypeEvalContext context) {
        super(holder, context);
        myIgnoreTupleUnpacking = ignoreTupleUnpacking;
        myIgnoreLambdaParameters = ignoreLambdaParameters;
        myIgnoreRangeIterationVariables = ignoreRangeIterationVariables;
        myIgnoreVariablesStartingWithUnderscore = ignoreVariablesStartingWithUnderscore;
        myReportTarget = reportTarget;
        mySharedAnalysis = sharedAnalysis;
        myScopeWrites = sharedAnalysis.scopeWrites;
        myScopeReads = sharedAnalysis.scopeReads;
    }

    @Override
    @RequiredReadAction
    public void visitPyElement(PyElement node) {
        if (node instanceof ScopeOwner scopeOwner && !(node instanceof PyFile)) {
            processScope(scopeOwner);
        }
    }

    @RequiredReadAction
    private void processScope(ScopeOwner owner) {
        if (owner.getContainingFile() instanceof PyExpressionCodeFragment) {
            return;
        }
        if (owner instanceof PyFunction pyFunction && PyiUtil.isOverload(pyFunction, myTypeEvalContext)) {
            return;
        }
        if (!callsLocals(owner)) {
            collectAllWrites(owner);
        }
        owner.accept(new PyRecursiveElementVisitor() {
            @Override
            @RequiredReadAction
            public void visitPyElement(PyElement node) {
                if (node instanceof ScopeOwner scopeOwner) {
                    collectUsedReads(scopeOwner);
                }
                super.visitPyElement(node);
            }
        });
    }

    @RequiredReadAction
    private void collectAllWrites(ScopeOwner owner) {
        if (myScopeWrites.containsKey(owner)) {
            // Already computed for another unused-symbol inspection sharing this session.
            return;
        }
        Set<PsiElement> scopeWrites = new HashSet<>();
        // type parameter list is not included in CFG
        if (owner instanceof PyTypeParameterListOwner typeParameterListOwner) {
            PyTypeParameterList typeParameterList = typeParameterListOwner.getTypeParameterList();
            if (typeParameterList != null) {
                scopeWrites.addAll(typeParameterList.getTypeParameters());
            }
        }
        if (owner instanceof PyClass) {
            myScopeWrites.put(owner, Collections.unmodifiableSet(scopeWrites));
            return;
        }
        Instruction[] instructions = ControlFlowCache.getControlFlow(owner).getInstructions();
        for (Instruction instruction : instructions) {
            PsiElement element = instruction.getElement();
            if (element instanceof PyFunction && owner instanceof PyFunction) {
                if (PyKnownDecoratorUtil.hasUnknownDecorator((PyFunction) element, myTypeEvalContext)) {
                    continue;
                }
                scopeWrites.add(element);
            }
            else if (instruction instanceof ReadWriteInstruction readWriteInstruction) {
                ReadWriteInstruction.ACCESS access = readWriteInstruction.getAccess();
                if (!access.isWriteAccess()) {
                    continue;
                }
                String name = readWriteInstruction.getName();
                // Ignore empty, wildcards, global and nonlocal names
                Scope scope = ControlFlowCache.getScope(owner);
                if (name == null || PyNames.UNDERSCORE.equals(name) || scope.isGlobal(name) || scope.isNonlocal(name)) {
                    continue;
                }
                if (element instanceof PyTargetExpression && ((PyTargetExpression) element).isQualified()) {
                    continue;
                }
                // Ignore underscore-prefixed parameters
                if (name.startsWith(PyNames.UNDERSCORE) && element instanceof PyParameter) {
                    continue;
                }
                // Ignore elements out of scope
                if (element == null || !PsiTreeUtil.isAncestor(owner, element, false)) {
                    continue;
                }
                // Ignore arguments of the import statement
                if (PyImportStatementNavigator.getImportStatementByElement(element) != null) {
                    continue;
                }
                if (PyAugAssignmentStatementNavigator.getStatementByTarget(element) != null) {
                    continue;
                }
                if (parameterInMethodWithFixedSignature(owner, element)) {
                    continue;
                }
                if (PyTypeDeclarationStatementNavigator.isTypeDeclarationTarget(element)) {
                    continue;
                }
                scopeWrites.add(element);
            }
        }
        myScopeWrites.put(owner, Collections.unmodifiableSet(scopeWrites));
    }

    private static boolean parameterInMethodWithFixedSignature(ScopeOwner owner, PsiElement element) {
        if (owner instanceof PyFunction function && element instanceof PyParameter) {
            String functionName = function.getName();

            LanguageLevel level = LanguageLevel.forElement(function);
            Map<String, PyNames.BuiltinDescription> builtinMethods =
                function.getContainingClass() != null ? PyNames.getBuiltinMethods(level) : PyNames.getModuleBuiltinMethods(level);

            return functionName != null && !PyNames.INIT.equals(functionName) && builtinMethods.containsKey(functionName);
        }

        return false;
    }

    @RequiredReadAction
    private Set<PsiElement> analyzeReadsInDoctests(PyStringLiteralExpression docstring, ScopeOwner owner) {
        PsiElement instrAnchor = PsiTreeUtil.getParentOfType(docstring, PyStatement.class);
        if (instrAnchor == null) {
            return Collections.emptySet();
        }
        Instruction[] instructions = ControlFlowCache.getControlFlow(owner).getInstructions();
        int startInstruction = ControlFlowUtil.findInstructionNumberByElement(instructions, instrAnchor);
        if (startInstruction < 0) {
            return Collections.emptySet();
        }
        Project project = docstring.getProject();
        List<Pair<PsiElement, TextRange>> pairs = InjectedLanguageManager.getInstance(project).getInjectedPsiFiles(docstring);
        if (pairs != null) {
            Set<PsiElement> result = new HashSet<>();
            for (Pair<PsiElement, TextRange> pair : pairs) {
                pair.getFirst().accept(new PyRecursiveElementVisitor() {
                    @Override
                    @RequiredReadAction
                    public void visitPyReferenceExpression(PyReferenceExpression expr) {
                        PyExpression qualifier = expr.getQualifier();
                        if (qualifier != null) {
                            qualifier.accept(this);
                            return;
                        }
                        String name = expr.getName();
                        if (name != null) {
                            result.addAll(analyzeReadsInScope(name, owner, instructions, startInstruction, docstring));
                        }
                    }
                });
            }
            return result;
        }
        return Collections.emptySet();
    }


    @RequiredReadAction
    private void collectUsedReads(ScopeOwner owner) {
        // Avoid performing the analysis twice for the same nested function
        if (myScopeReads.containsKey(owner)) {
            return;
        }
        Set<PsiElement> allPathsScopeReads = new HashSet<>();
        if (owner instanceof PyDocStringOwner docStringOwner) {
            PyStringLiteralExpression docstring = docStringOwner.getDocStringExpression();
            if (docstring != null) {
                allPathsScopeReads.addAll(analyzeReadsInDoctests(docstring, owner));
            }
        }

        Instruction[] instructions = ControlFlowCache.getControlFlow(owner).getInstructions();
        for (int i = 0; i < instructions.length; i++) {
            Instruction instruction = instructions[i];
            if (instruction instanceof ReadWriteInstruction readWriteInstruction) {
                ReadWriteInstruction.ACCESS access = readWriteInstruction.getAccess();
                // A `del name` statement is a common pattern to suppress an unused parameter PY-39449
                if (!access.isReadAccess() && !access.isDeleteAccess()) {
                    continue;
                }
                String name = readWriteInstruction.getName();
                if (name == null) {
                    continue;
                }
                PsiElement element = instruction.getElement();
                // Ignore elements out of scope
                if (element == null || !PsiTreeUtil.isAncestor(owner, element, false)) {
                    continue;
                }
                int startInstruction;
                if (access.isWriteAccess()) {
                    PyAugAssignmentStatement augAssignmentStatement = PyAugAssignmentStatementNavigator.getStatementByTarget(element);
                    startInstruction = ControlFlowUtil.findInstructionNumberByElement(instructions, augAssignmentStatement);
                }
                else {
                    startInstruction = i;
                }
                allPathsScopeReads.addAll(
                    analyzeReadsInScope(name, owner, instructions, startInstruction, PyUtil.as(element, PyReferenceExpression.class)));
            }
        }
        myScopeReads.put(owner, Collections.unmodifiableSet(allPathsScopeReads));
    }

    private static Set<PsiElement> analyzeReadsInScope(String name,
                                                       ScopeOwner owner,
                                                       Instruction[] instructions,
                                                       int startInstruction,
                                                       @Nullable PsiElement scopeAnchor) {
        Set<PsiElement> readsFromInstruction = new HashSet<>();
        // Check if the element is declared out of scope, mark all out-of-scope write accesses as used
        if (scopeAnchor != null) {
            ScopeOwner declOwner = ScopeUtil.getDeclarationScopeOwner(scopeAnchor, name);
            if (declOwner != null && declOwner != owner) {
                ScopeUtil.getElementsOfAccessType(name, declOwner, ReadWriteInstruction.ACCESS.WRITE)
                    .iterator().forEachRemaining(readsFromInstruction::add);
            }
            // Type params are not present in CFG
            else if (owner instanceof PyTypeParameterListOwner typeParameterListOwner &&
                typeParameterListOwner.getTypeParameterList() != null) {
                StreamEx.of(typeParameterListOwner.getTypeParameterList().getTypeParameters())
                    .filter(typeParameter -> name.equals(typeParameter.getName()))
                    .forEach(readsFromInstruction::add);
            }
        }
        ControlFlowUtil.iteratePrev(startInstruction, instructions, inst -> {
            PsiElement instElement = inst.getElement();
            // Mark the function as used
            if (instElement instanceof PyFunction) {
                if (name.equals(((PyFunction) instElement).getName())) {
                    readsFromInstruction.add(instElement);
                    return ControlFlowUtil.Operation.CONTINUE;
                }
            }
            // Mark write access as used
            else if (inst instanceof ReadWriteInstruction rwInstruction) {
                if (rwInstruction.getAccess().isWriteAccess() && name.equals(rwInstruction.getName())) {
                    // Look up higher in CFG for actual definitions
                    if (instElement != null && PyTypeDeclarationStatementNavigator.isTypeDeclarationTarget(instElement)) {
                        return ControlFlowUtil.Operation.NEXT;
                    }
                    // For elements in scope
                    if (instElement != null && PsiTreeUtil.isAncestor(owner, instElement, false)) {
                        readsFromInstruction.add(instElement);
                    }
                    return ControlFlowUtil.Operation.CONTINUE;
                }
            }
            return ControlFlowUtil.Operation.NEXT;
        });
        return readsFromInstruction;
    }

    static class DontPerformException extends RuntimeException {
    }

    private static boolean callsLocals(ScopeOwner owner) {
        try {
            owner.acceptChildren(new PyRecursiveElementVisitor() {
                @Override
                public void visitPyCallExpression(PyCallExpression node) {
                    PyExpression callee = node.getCallee();
                    if (callee != null && "locals".equals(callee.getName())) {
                        throw new DontPerformException();
                    }
                    node.acceptChildren(this); // look at call expr in arguments
                }

                @Override
                public void visitPyFunction(PyFunction node) {
                    // stop here
                }
            });
        }
        catch (DontPerformException e) {
            return true;
        }
        return false;
    }

    private static ReportTarget categorize(PsiElement element) {
        if (element instanceof PyNamedParameter || element.getParent() instanceof PyNamedParameter) {
            return ReportTarget.PARAMETERS;
        }
        if (element instanceof PyFunction) {
            return ReportTarget.FUNCTIONS;
        }
        return ReportTarget.LOCALS;
    }

    @RequiredReadAction
    public void registerProblems() {
        List<PyInspectionExtension> filters = PyInspectionExtension.EP_NAME.getExtensionList();
        // Register problems

        Set<PyFunction> functionsWithInheritors = new HashSet<>();
        Map<PyFunction, Boolean> emptyFunctions = new HashMap<>();

        Set<PsiElement> unusedElements = mySharedAnalysis.unusedElements;
        if (unusedElements == null) {
            unusedElements = excludeElementsUsedInTypeHints(
                StreamEx.of(myScopeWrites.entrySet())
                    .flatCollection(writeEntry -> ContainerUtil.subtract(writeEntry.getValue(), getReadsInsideScope(writeEntry.getKey())))
                    .toImmutableSet());
            // Benign race: concurrent inspections may recompute the identical set; the last write wins.
            mySharedAnalysis.unusedElements = unusedElements;
        }

        for (PsiElement element : unusedElements) {
            // Parameters, functions, and the remaining "local symbols" are each reported by a separate inspection.
            if (categorize(element) != myReportTarget) {
                continue;
            }

            boolean ignoreUnused = false;
            for (PyInspectionExtension filter : filters) {
                if (filter.ignoreUnused(element, myTypeEvalContext)) {
                    ignoreUnused = true;
                }
            }
            if (ignoreUnused) {
                continue;
            }

            if (element instanceof PyFunction) {
                // Local function
                PsiElement nameIdentifier = ((PyFunction) element).getNameIdentifier();
                registerWarning(nameIdentifier == null ? element : nameIdentifier,
                                PyInspectionMessages.problemMessage(p -> PyLocalize.inspUnusedLocalsLocalFunctionIsnotUsed(p[0]),
                                                                    CodifiedParam.ofReference((PyFunction) element)),
                                new PyRemoveStatementQuickFix());
            }
            else if (element instanceof PyClass cls) {
                // Local class
                PsiElement name = cls.getNameIdentifier();
                registerWarning(name != null ? name : element,
                                PyInspectionMessages.problemMessage(p -> PyLocalize.inspUnusedLocalsLocalClassIsnotUsed(p[0]),
                                                                    CodifiedParam.ofReference(cls)),
                                new PyRemoveStatementQuickFix());
            }
            else if (element instanceof PyTypeAliasStatement typeAlias) {
                PsiElement name = typeAlias.getNameIdentifier();
                registerWarning(name != null ? name : element,
                                PyInspectionMessages.problemMessage(p -> PyLocalize.inspUnusedLocalsTypeAliasIsnotUsed(p[0]),
                                                                    CodifiedParam.ofReference(typeAlias)),
                                new PyRemoveStatementQuickFix());
            }
            else if (element instanceof PyTypeParameter typeParameter) {
                PsiElement name = typeParameter.getNameIdentifier();
                registerWarning(name != null ? name : element,
                                PyInspectionMessages.problemMessage(p -> PyLocalize.inspUnusedLocalsTypeParameterIsnotUsed(p[0]),
                                                                    CodifiedParam.ofReference(typeParameter)),
                                new PyRemoveTypeParameterQuickFix());
            }
            else {
                // Local variable or parameter
                String name = element instanceof PsiNamedElement namedElement ? StringUtil.notNullize(namedElement.getName()) : element.getText();
                if (element instanceof PyNamedParameter || element.getParent() instanceof PyNamedParameter) {
                    PyNamedParameter namedParameter = element instanceof PyNamedParameter
                        ? (PyNamedParameter) element
                        : (PyNamedParameter) element.getParent();
                    name = namedParameter.getName();
                    // When the function is inside a class, the first parameter may be either self or cls which is always 'used'.
                    if (namedParameter.isSelf()) {
                        continue;
                    }
                    if (myIgnoreLambdaParameters && PsiTreeUtil.getParentOfType(element, PyCallable.class) instanceof PyLambdaExpression) {
                        continue;
                    }
                    boolean mayBeField = false;
                    PyClass containingClass = null;
                    PyParameterList paramList = PsiTreeUtil.getParentOfType(element, PyParameterList.class);
                    if (paramList != null && paramList.getParent() instanceof PyFunction func) {
                        if (PyUtil.isInitMethod(func)) {
                            List<PyFunction> complementaryMethods =
                                PyConstructorSignatureUtil.findComplementaryConstructors(func, myTypeEvalContext);
                            if (!complementaryMethods.isEmpty()) {
                                continue;
                            }
                        }
                        containingClass = func.getContainingClass();
                        if (containingClass != null &&
                            PyUtil.isInitMethod(func) &&
                            !namedParameter.isKeywordContainer() &&
                            !namedParameter.isPositionalContainer()) {
                            mayBeField = true;
                        }
                        else if (ignoreUnusedParameters(func, functionsWithInheritors)) {
                            continue;
                        }
                        if (func.asMethod() != null) {
                            Boolean isEmpty = emptyFunctions.get(func);
                            if (isEmpty == null) {
                                isEmpty = PyUtil.isEmptyFunction(func);
                                emptyFunctions.put(func, isEmpty);
                            }
                            if (isEmpty && !mayBeField) {
                                continue;
                            }
                        }
                    }
                    boolean canRemove = !(PsiTreeUtil.getPrevSiblingOfType(element, PyParameter.class) instanceof PySingleStarParameter) ||
                        PsiTreeUtil.getNextSiblingOfType(element, PyParameter.class) != null;

                    List<LocalQuickFix> fixes = new ArrayList<>();
                    if (mayBeField) {
                        fixes.add(new AddFieldQuickFix(name, name, containingClass.getName(), false));
                    }
                    if (canRemove) {
                        fixes.add(new PyRemoveParameterQuickFix());
                    }
                    String parameterName = name;
                    registerWarning(element,
                                    PyInspectionMessages.problemMessage(p -> PyLocalize.inspUnusedLocalsParameterIsnotUsed(p[0]),
                                                                        String.valueOf(parameterName)),
                                    fixes.toArray(LocalQuickFix.EMPTY_ARRAY));
                }
                else {
                    if (myIgnoreVariablesStartingWithUnderscore && element.getText().startsWith(PyNames.UNDERSCORE)) {
                        continue;
                    }
                    if (myIgnoreTupleUnpacking && isTupleUnpacking(element, unusedElements)) {
                        continue;
                    }

                    String variableName = name;
                    PyInspectionMessages.ProblemMessage warningMsg =
                        PyInspectionMessages.problemMessage(p -> PyLocalize.inspUnusedLocalsLocalVariableIsnotUsed(p[0]), variableName);

                    PyForStatement forStatement = PyForStatementNavigator.getPyForStatementByIterable(element);
                    if (forStatement != null) {
                        if (!myIgnoreRangeIterationVariables || !isRangeIteration(forStatement)) {
                            registerWarning(element, warningMsg, new ReplaceWithWildCard());
                        }
                        continue;
                    }

                    if (isComprehensionTarget(element)) {
                        registerWarning(element, warningMsg, new ReplaceWithWildCard());
                        continue;
                    }

                    PyExceptPart exceptPart = PyExceptPartNavigator.getPyExceptPartByTarget(element);
                    if (exceptPart != null) {
                        registerWarning(element, warningMsg, new PyRemoveExceptionTargetQuickFix());
                        continue;
                    }

                    PyWithItem withItem = PsiTreeUtil.getParentOfType(element, PyWithItem.class);
                    if (withItem != null && PsiTreeUtil.isAncestor(withItem.getTarget(), element, false)) {
                        if (withItem.getTarget() == element) {
                            registerWarning(element, warningMsg, new PyRemoveWithTargetQuickFix());
                        }
                        else {
                            registerWarning(element, warningMsg, new ReplaceWithWildCard());
                        }
                        continue;
                    }

                    PyAssignmentStatement assignmentStatement = PsiTreeUtil.getParentOfType(element, PyAssignmentStatement.class);
                    if (assignmentStatement != null && !PsiTreeUtil.isAncestor(assignmentStatement.getAssignedValue(), element, false)) {
                        if (assignmentStatement.getLeftHandSideExpression() == element) {
                            // Single assignment target (unused = value)
                            registerWarning(element, warningMsg, new PyRemoveAssignmentStatementTargetQuickFix(), new PyRemoveStatementQuickFix());
                        }
                        else if (ArrayUtil.contains(element, assignmentStatement.getRawTargets())) {
                            // Chained assignment target (used = unused = value)
                            registerWarning(element, warningMsg, new PyRemoveAssignmentStatementTargetQuickFix());
                        }
                        else {
                            // Unpacking (used, unused = value)
                            registerWarning(element, warningMsg, new ReplaceWithWildCard());
                        }
                        continue;
                    }

                    registerWarning(element, warningMsg);
                }
            }
        }
    }

    /**
     * Removes the candidates that a quoted annotation or a type comment refers to.
     * The control flow graph does not include these references, so they are searched only for the unused candidates.
     */
    private Set<PsiElement> excludeElementsUsedInTypeHints(Set<PsiElement> candidates) {
        Set<String> candidateNames = StreamEx.of(candidates).map(PyUnusedLocalInspectionVisitor::getCandidateName).nonNull().toSet();
        if (candidateNames.isEmpty()) {
            return candidates;
        }

        List<ScopeOwner> owners = EntryStream.of(myScopeWrites)
            .filterValues(writes -> ContainerUtil.intersects(writes, candidates))
            .keys()
            .toList();
        List<ScopeOwner> outermostOwners = ContainerUtil.filter(
            owners, owner -> !ContainerUtil.exists(owners, other -> PsiTreeUtil.isAncestor(other, owner, true)));

        InjectedLanguageManager injectedLanguageManager = InjectedLanguageManager.getInstance(candidates.iterator().next().getProject());
        PyResolveContext resolveContext = PyResolveContext.defaultContext(myTypeEvalContext);
        Set<PsiElement> used = new HashSet<>();
        PyRecursiveElementVisitor referenceVisitor = new PyRecursiveElementVisitor() {
            @Override
            public void visitPyReferenceExpression(PyReferenceExpression expr) {
                PyExpression qualifier = expr.getQualifier();
                if (qualifier != null) {
                    qualifier.accept(this);
                    return;
                }
                String exprName = expr.getName();
                if (exprName == null || !candidateNames.contains(exprName)) {
                    return;
                }
                for (ResolveResult resolveResult : expr.getReference(resolveContext).multiResolve(false)) {
                    PsiElement resolved = resolveResult.getElement();
                    if (resolved != null && candidates.contains(resolved)) {
                        used.add(resolved);
                    }
                }
            }
        };
        PyRecursiveElementVisitor hostVisitor = new PyRecursiveElementVisitor() {
            @Override
            public void visitPyStringLiteralExpression(PyStringLiteralExpression node) {
                visitInjectedTypeHints(node);
            }

            @Override
            public void visitComment(PsiComment comment) {
                visitInjectedTypeHints(comment);
            }

            private void visitInjectedTypeHints(PsiElement host) {
                List<Pair<PsiElement, TextRange>> injectedFiles = injectedLanguageManager.getInjectedPsiFiles(host);
                if (injectedFiles == null) {
                    return;
                }
                for (Pair<PsiElement, TextRange> injectedFile : injectedFiles) {
                    Language language = injectedFile.getFirst().getLanguage();
                    if (language == PyTypeHintDialect.INSTANCE || language == PyFunctionTypeAnnotationDialect.INSTANCE) {
                        injectedFile.getFirst().accept(referenceVisitor);
                    }
                }
            }
        };
        for (ScopeOwner owner : outermostOwners) {
            owner.accept(hostVisitor);
        }
        return used.isEmpty() ? candidates : StreamEx.of(candidates).remove(used::contains).toImmutableSet();
    }

    private static @Nullable String getCandidateName(PsiElement element) {
        return element instanceof PsiNamedElement namedElement ? namedElement.getName() : element.getText();
    }

    private Set<PsiElement> getReadsInsideScope(ScopeOwner scopeOwner) {
        return StreamEx.of(myScopeReads.entrySet())
            .filter(readEntry -> PsiTreeUtil.isAncestor(scopeOwner, readEntry.getKey(), false))
            .flatCollection(readEntry -> readEntry.getValue())
            .toImmutableSet();
    }

    private static boolean isComprehensionTarget(PsiElement element) {
        PyComprehensionElement comprehensionExpr = PsiTreeUtil.getParentOfType(element, PyComprehensionElement.class);
        if (comprehensionExpr == null) {
            return false;
        }
        return ContainerUtil.exists(comprehensionExpr.getForComponents(),
                                    it -> PsiTreeUtil.isAncestor(it.getIteratorVariable(), element, false));
    }

    private boolean isRangeIteration(PyForStatement forStatement) {
        PyExpression source = forStatement.getForPart().getSource();
        if (!(source instanceof PyCallExpression expr)) {
            return false;
        }
        if (expr.isCalleeText("range", "xrange")) {
            PyResolveContext resolveContext = PyResolveContext.defaultContext(myTypeEvalContext);
            PyBuiltinCache builtinCache = PyBuiltinCache.getInstance(forStatement);

            return ContainerUtil.exists(expr.multiResolveCalleeFunction(resolveContext), builtinCache::isBuiltin);
        }
        return false;
    }

    private boolean ignoreUnusedParameters(PyFunction func, Set<PyFunction> functionsWithInheritors) {
        if (functionsWithInheritors.contains(func)) {
            return true;
        }
        if (!PyUtil.isInitMethod(func) && PySuperMethodsSearch.search(func, myTypeEvalContext).findFirst() != null ||
            PyOverridingMethodsSearch.search(func, true).findFirst() != null) {
            functionsWithInheritors.add(func);
            return true;
        }
        return false;
    }

    private static boolean isTupleUnpacking(PsiElement element, Set<PsiElement> unusedElements) {
        if (!(element instanceof PyTargetExpression)) {
            return false;
        }
        // Handling of the star expressions
        PsiElement current = element;
        PsiElement parent = current.getParent();
        if (parent instanceof PyStarExpression) {
            current = parent;
            parent = current.getParent();
        }
        if (parent instanceof PyTupleExpression tuple) {
            // if all the items of the tuple are unused, we still highlight all of them; if some are unused, we ignore
            for (PyExpression expression : tuple.getElements()) {
                if (expression instanceof PyStarExpression) {
                    if (!unusedElements.contains(((PyStarExpression) expression).getExpression())) {
                        return true;
                    }
                }
                else if (!unusedElements.contains(expression)) {
                    return true;
                }
            }
        }
        return false;
    }

    @RequiredReadAction
    private void registerWarning(PsiElement element,
                                 PyInspectionMessages.ProblemMessage msg,
                                 LocalQuickFix... quickfixes) {
        registerProblem(element, msg, ProblemHighlightType.LIKE_UNUSED_SYMBOL, quickfixes);
    }

    // JB: PsiUpdateModCommandQuickFix (ModCommand API is not available in Consulo)
    private static class ReplaceWithWildCard implements LocalQuickFix {
        @Override
        public LocalizeValue getName() {
            return PyLocalize.inspUnusedLocalsReplaceWithWildcard();
        }

        @Override
        @RequiredWriteAction
        public void applyFix(Project project, ProblemDescriptor descriptor) {
            PsiElement element = descriptor.getPsiElement();
            if (element == null) {
                return;
            }
            PyFile pyFile = (PyFile) PyElementGenerator.getInstance(element.getProject()).createDummyFile(LanguageLevel.getDefault(),
                                                                                                         "for _ in tuples:\n  pass"
            );
            PyExpression target = ((PyForStatement) pyFile.getStatements().get(0)).getForPart().getTarget();
            if (target != null) {
                element.replace(target);
            }
        }
    }
}
