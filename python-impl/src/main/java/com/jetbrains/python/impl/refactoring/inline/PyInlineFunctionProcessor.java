// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.refactoring.inline;

import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.codeInsight.PyDunderAllReference;
import com.jetbrains.python.impl.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.impl.codeInsight.controlflow.ReadWriteInstruction;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.Scope;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import com.jetbrains.python.impl.psi.impl.PyCallExpressionHelper;
import com.jetbrains.python.impl.psi.resolve.PyResolveUtil;
import com.jetbrains.python.impl.pyi.PyiUtil;
import com.jetbrains.python.impl.refactoring.PyRefactoringUtil;
import com.jetbrains.python.impl.refactoring.classes.PyClassRefactoringUtil;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyDecorator;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyExpressionStatement;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.PyImportStatementBase;
import com.jetbrains.python.psi.PyKeywordArgument;
import com.jetbrains.python.psi.PyLambdaExpression;
import com.jetbrains.python.psi.PyLiteralExpression;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.PyPassStatement;
import com.jetbrains.python.psi.PyRecursiveElementVisitor;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyReturnStatement;
import com.jetbrains.python.psi.PyStarArgument;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.PyStatementList;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.PyTupleExpression;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.PyCallableParameter;
import com.jetbrains.python.psi.types.PyCallableType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.codeEditor.Editor;
import consulo.document.Document;
import consulo.document.util.TextRange;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.controlFlow.Instruction;
import consulo.language.editor.refactoring.BaseRefactoringProcessor;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiReference;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.psi.SyntaxTraverser;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localHistory.LocalHistory;
import consulo.localHistory.LocalHistoryAction;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.usage.UsageInfo;
import consulo.usage.UsageViewDescriptor;
import consulo.util.collection.MultiMap;
import consulo.util.lang.Pair;
import consulo.util.lang.ref.SimpleReference;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * @author Aleksei.Kniazev
 */
public class PyInlineFunctionProcessor extends BaseRefactoringProcessor {
    private final Editor myEditor;
    private final PyFunction myFunction;
    private final @Nullable PsiReference myReference;
    private final boolean myInlineThisOnly;

    private final @Nullable PyClass myFunctionClass;
    private final PyElementGenerator myGenerator;
    private boolean myRemoveDeclaration;

    public PyInlineFunctionProcessor(
        Project project,
        Editor editor,
        PyFunction function,
        @Nullable PsiReference reference,
        boolean inlineThisOnly,
        boolean removeDeclaration
    ) {
        super(project);
        myEditor = editor;
        myFunction = function;
        myReference = reference;
        myInlineThisOnly = inlineThisOnly;
        myFunctionClass = myFunction.getContainingClass();
        myGenerator = PyElementGenerator.getInstance(myProject);
        myRemoveDeclaration = !myInlineThisOnly && removeDeclaration;
    }

    @Override
    protected boolean preprocessUsages(SimpleReference<UsageInfo[]> refUsages) {
        if (refUsages.isNull()) {
            return false;
        }
        MultiMap<PsiElement, LocalizeValue> conflicts = MultiMap.create();
        UsageInfo[] usagesAndImports = refUsages.get();
        List<UsageInfo> imports = new ArrayList<>();
        List<UsageInfo> usages = new ArrayList<>();
        for (UsageInfo usage : usagesAndImports) {
            if (PsiTreeUtil.getParentOfType(usage.getElement(), PyImportStatementBase.class) != null) {
                imports.add(usage);
            }
            else {
                usages.add(usage);
            }
        }
        List<UsageInfo> filteredUsages = new ArrayList<>();
        for (UsageInfo usage : usages) {
            if (usage.getReference() instanceof PyDunderAllReference) {
                filteredUsages.add(usage);
                continue;
            }
            PsiElement element = usage.getElement();
            assert element != null;
            if (element.getParent() instanceof PyDecorator) {
                if (!handleUsageError(element, PyLocalize::refactoringInlineFunctionIsDecorator, conflicts)) {
                    return false;
                }
                continue;
            }
            else if (!(element.getParent() instanceof PyCallExpression)) {
                if (!handleUsageError(element, PyLocalize::refactoringInlineFunctionIsReference, conflicts)) {
                    return false;
                }
                continue;
            }
            else {
                PyCallExpression callExpression = (PyCallExpression) element.getParent();
                boolean hasStarArgument = false;
                for (PyExpression argument : callExpression.getArguments()) {
                    if (argument instanceof PyStarArgument) {
                        hasStarArgument = true;
                        break;
                    }
                }
                if (hasStarArgument) {
                    if (!handleUsageError(element, PyLocalize::refactoringInlineFunctionUsesUnpacking, conflicts)) {
                        return false;
                    }
                    continue;
                }
            }
            filteredUsages.add(usage);
        }

        List<PsiFile> conflictLocations = new ArrayList<>();
        for (PsiElement element : conflicts.keySet()) {
            conflictLocations.add(element.getContainingFile());
        }
        List<UsageInfo> filtered = new ArrayList<>(filteredUsages);
        for (UsageInfo usage : imports) {
            if (!conflictLocations.contains(usage.getFile())) {
                filtered.add(usage);
            }
        }
        refUsages.set(filtered.toArray(UsageInfo.EMPTY_ARRAY));
        return showConflicts(conflicts, filtered.toArray(UsageInfo.EMPTY_ARRAY));
    }

    private boolean handleUsageError(
        PsiElement element,
        Function<Object, LocalizeValue> error,
        MultiMap<PsiElement, LocalizeValue> conflicts
    ) {
        LocalizeValue errorText = error.apply(myFunction.getName());
        if (myInlineThisOnly) {
            // shortcut for inlining single reference: show error hint instead of modal dialog
            CommonRefactoringUtil.showErrorHint(myProject, myEditor, errorText, PyLocalize.refactoringInlineFunctionTitle(),
                PyInlineFunctionHandler.Helper.REFACTORING_ID
            );
            prepareSuccessful();
            return false;
        }
        conflicts.putValue(element, errorText);
        myRemoveDeclaration = false;
        return true;
    }

    @Override
    protected UsageInfo[] findUsages() {
        if (myInlineThisOnly) {
            assert myReference != null;
            PyReferenceExpression element = (PyReferenceExpression) myReference.getElement();
            ScopeOwner scopeOwner = ScopeUtil.getScopeOwner(element);
            String name = element.getName();
            assert scopeOwner != null && name != null;
            PsiElement localImport = null;
            for (PsiElement resolved : PyResolveUtil.resolveLocally(scopeOwner, name)) {
                if (resolved instanceof PyImportElement) {
                    localImport = resolved;
                    break;
                }
            }
            return localImport != null
                ? new UsageInfo[]{new UsageInfo(element), new UsageInfo(localImport)}
                : new UsageInfo[]{new UsageInfo(element)};
        }

        // TODO: replace with PyRefactoringUtil#findUsages after PY-26881 and PY-36493 are fixed
        Set<PsiReference> references = new LinkedHashSet<>(ReferencesSearch.search(myFunction, myRefactoringScope).findAll());
        PsiElement stub = PyiUtil.getPythonStub(myFunction);
        if (stub != null) {
            references.addAll(ReferencesSearch.search(stub, myRefactoringScope).findAll());
        }
        List<UsageInfo> result = new ArrayList<>();
        for (PsiReference reference : references) {
            result.add(new UsageInfo(reference.getElement()));
        }
        return result.toArray(UsageInfo.EMPTY_ARRAY);
    }

    @Override
    protected void performRefactoring(UsageInfo[] usages) {
        LocalHistoryAction action = LocalHistory.getInstance().startAction(getCommandName());
        try {
            doRefactor(usages);
        }
        finally {
            action.finish();
        }
    }

    private void doRefactor(UsageInfo[] usages) {
        List<UsageInfo> unsortedRefs = new ArrayList<>();
        List<UsageInfo> imports = new ArrayList<>();
        for (UsageInfo usage : usages) {
            if (PsiTreeUtil.getParentOfType(usage.getElement(), PyImportStatementBase.class) == null) {
                unsortedRefs.add(usage);
            }
            else {
                imports.add(usage);
            }
        }
        List<UsageInfo> callRefs = new ArrayList<>();
        List<UsageInfo> dunderAll = new ArrayList<>();
        for (UsageInfo usage : unsortedRefs) {
            if (!(usage.getReference() instanceof PyDunderAllReference)) {
                callRefs.add(usage);
            }
            else {
                dunderAll.add(usage);
            }
        }

        List<UsageInfo> references = new ArrayList<>(callRefs);
        references.sort(Comparator.comparingInt((UsageInfo usage) ->
            SyntaxTraverser.psiApi().parents(usage.getElement()).filter(it -> it instanceof PyCallExpression).size()).reversed());

        TypeEvalContext typeEvalContext = TypeEvalContext.userInitiated(myProject, null);
        PyResolveContext resolveContext = PyResolveContext.defaultContext(typeEvalContext);

        boolean selfUsed = false;
        PyParameter[] functionParameters = myFunction.getParameterList().getParameters();
        if (functionParameters.length > 0) {
            PyParameter firstParam = functionParameters[0];
            if (firstParam.isSelf()) {
                for (PyReferenceExpression ref : SyntaxTraverser.psiTraverser(myFunction.getStatementList()).traverse()
                    .filter(PyReferenceExpression.class)) {
                    if (!ref.isQualified() && ref.getReference().isReferenceTo(firstParam)) {
                        selfUsed = true;
                        break;
                    }
                }
            }
        }

        Scope functionScope = ControlFlowCache.getScope(myFunction);
        PyClassRefactoringUtil.rememberNamedReferences(myFunction);

        Set<String> reassignedParams = collectReassignedNames();

        for (UsageInfo usage : references) {
            PyReferenceExpression reference = (PyReferenceExpression) usage.getElement();
            LanguageLevel languageLevel = LanguageLevel.forElement(reference);
            ScopeOwner refScopeOwner = ScopeUtil.getScopeOwner(reference);
            if (refScopeOwner == null) {
                throw new IllegalStateException("Unable to find scope owner for " + reference.getName());
            }
            List<PyAssignmentStatement> declarations = new ArrayList<>();
            Set<String> generatedNames = new HashSet<>();

            PyCallExpression callSite = PsiTreeUtil.getParentOfType(reference, PyCallExpression.class);
            if (callSite == null) {
                throw new IllegalStateException("Unable to find call expression for " + reference.getName());
            }
            PyStatement containingStatement = PsiTreeUtil.getParentOfType(callSite, PyStatement.class);
            if (containingStatement == null) {
                throw new IllegalStateException("Unable to find statement for " + reference.getName());
            }
            PsiElement scopeAnchor = containingStatement instanceof PyFunction ? containingStatement : reference;

            PyFunction functionCopy = (PyFunction) myFunction.copy();
            PsiComment typeComment = functionCopy.getTypeComment();
            if (typeComment != null) {
                typeComment.delete();
            }
            PyStatement docStringStatement = PsiTreeUtil.getParentOfType(functionCopy.getDocStringExpression(), PyStatement.class);
            if (docStringStatement != null) {
                docStringStatement.delete();
            }

            PyStatementList replacement = functionCopy.getStatementList();
            Collection<String> namesInOuterScope = PyUtil.collectUsedNames(refScopeOwner);
            PyBuiltinCache builtinCache = PyBuiltinCache.getInstance(reference);

            Map<PyExpression, PyExpression> argumentReplacements = new LinkedHashMap<>();
            Set<String> nameClashes = new LinkedHashSet<>();
            Set<String> importAsTargets = new LinkedHashSet<>();
            MultiMap<String, PyExpression> nameClashRefs = MultiMap.create();
            MultiMap<String, PyReferenceExpression> importAsRefs = MultiMap.create();
            List<PyReturnStatement> returnStatements = new ArrayList<>();

            Map<String, PyExpression> mappedArguments = prepareArguments(
                callSite, declarations, generatedNames, scopeAnchor, reference, languageLevel, resolveContext, selfUsed, reassignedParams
            );

            myFunction.getStatementList().accept(new PyRecursiveElementVisitor() {
                @Override
                public void visitPyReferenceExpression(PyReferenceExpression node) {
                    if (!node.isQualified()) {
                        String name = node.getName();
                        assert name != null;
                        if (namesInOuterScope.contains(name) && !mappedArguments.containsKey(name)) {
                            PsiElement resolved = node.getReference().resolve();
                            PsiElement constructorClass =
                                PyUtil.turnConstructorIntoClass(resolved instanceof PyFunction function ? function : null);
                            PsiElement target = constructorClass != null ? constructorClass : resolved;
                            if (!builtinCache.isBuiltin(target)) {
                                Collection<PsiElement> resolvedLocally = PyResolveUtil.resolveLocally(refScopeOwner, name);
                                boolean importedLocally = false;
                                for (PsiElement local : resolvedLocally) {
                                    if (local instanceof PyImportElement importElement) {
                                        PyReferenceExpression importReferenceExpression = importElement.getImportReferenceExpression();
                                        PsiReference importReference =
                                            importReferenceExpression != null ? importReferenceExpression.getReference() : null;
                                        if (importReference != null) {
                                            assert target != null;
                                            if (importReference.isReferenceTo(target)) {
                                                importedLocally = true;
                                                break;
                                            }
                                        }
                                    }
                                }
                                if (!resolvedLocally.contains(target) && !importedLocally) {
                                    if (PyClassRefactoringUtil.hasEncodedTarget(node)) {
                                        importAsTargets.add(name);
                                    }
                                    else {
                                        nameClashes.add(name);
                                    }
                                }
                            }
                        }
                    }
                    super.visitPyReferenceExpression(node);
                }

                @Override
                public void visitPyTargetExpression(PyTargetExpression node) {
                    if (!node.isQualified()) {
                        String name = node.getName();
                        assert name != null;
                        if (namesInOuterScope.contains(name) && !mappedArguments.containsKey(name) &&
                            functionScope.containsDeclaration(name)) {
                            nameClashes.add(name);
                        }
                    }
                    super.visitPyTargetExpression(node);
                }
            });


            replacement.accept(new PyRecursiveElementVisitor() {
                @Override
                public void visitPyReferenceExpression(PyReferenceExpression node) {
                    if (!node.isQualified()) {
                        PyLambdaExpression parentLambda = PsiTreeUtil.getParentOfType(node, PyLambdaExpression.class);
                        boolean lambdaParameter = false;
                        if (parentLambda != null) {
                            for (PyParameter parameter : parentLambda.getParameterList().getParameters()) {
                                if (java.util.Objects.equals(parameter.getName(), node.getName())) {
                                    lambdaParameter = true;
                                    break;
                                }
                            }
                        }
                        if (parentLambda == null || !lambdaParameter) {
                            String name = node.getName();
                            if (mappedArguments.containsKey(name)) {
                                argumentReplacements.put(node, mappedArguments.get(name));
                            }
                            else if (nameClashes.contains(name)) {
                                nameClashRefs.putValue(name, node);
                            }
                            else if (importAsTargets.contains(name)) {
                                importAsRefs.putValue(name, node);
                            }
                        }
                    }
                    super.visitPyReferenceExpression(node);
                }

                @Override
                public void visitPyReturnStatement(PyReturnStatement node) {
                    returnStatements.add(node);
                    super.visitPyReturnStatement(node);
                }

                @Override
                public void visitPyTargetExpression(PyTargetExpression node) {
                    String name = node.getName();
                    if (!node.isQualified() && name != null) {
                        PyExpression mappedArgument = mappedArguments.get(name);
                        if (mappedArgument != null && reassignedParams.contains(name)) {
                            argumentReplacements.put(node, mappedArgument);
                        }
                        else if (nameClashes.contains(name)) {
                            nameClashRefs.putValue(name, node);
                        }
                    }
                    super.visitPyTargetExpression(node);
                }
            });

            // Replacing
            argumentReplacements.forEach((oldExpression, newExpression) -> oldExpression.replace(newExpression));
            for (Map.Entry<String, Collection<PyExpression>> entry : nameClashRefs.entrySet()) {
                PyAssignmentStatement generated = generateUniqueAssignment(languageLevel, entry.getKey(), generatedNames, scopeAnchor);
                for (PyExpression it : entry.getValue()) {
                    if (it instanceof PyTargetExpression) {
                        it.replace(generated.getTargets()[0]);
                    }
                    else if (it instanceof PyReferenceExpression) {
                        PyExpression assignedValue = generated.getAssignedValue();
                        assert assignedValue != null;
                        it.replace(assignedValue);
                    }
                }
            }

            for (Map.Entry<String, Collection<PyReferenceExpression>> entry : importAsRefs.entrySet()) {
                PyReferenceExpression newRef = (PyReferenceExpression) generateUniqueAssignment(
                    languageLevel, entry.getKey(), generatedNames, scopeAnchor).getAssignedValue();
                assert newRef != null;
                for (PyReferenceExpression it : entry.getValue()) {
                    PyClassRefactoringUtil.transferEncodedImports(it, newRef);
                    String newRefName = newRef.getName();
                    assert newRefName != null;
                    PyClassRefactoringUtil.forceAsName(newRef, newRefName);
                    it.replace(newRef);
                }
            }

            // We may need to delete redundant self-assignment after insertion
            PyAssignmentStatement assignmentToDelete = null;
            if (returnStatements.size() == 1 && !(returnStatements.get(0).getExpression() instanceof PyTupleExpression)) {
                // replace single return with expression itself
                PyReturnStatement statement = returnStatements.get(0);
                PyExpression returnExpression = statement.getExpression();
                assert returnExpression != null;
                PsiElement replaced = callSite.replace(returnExpression);
                PyClassRefactoringUtil.restoreNamedReferences(replaced);
                statement.delete();

                // Detect redundant self-assignment like `x = x` and defer deletion until after insertion
                PyAssignmentStatement parentStmt = PsiTreeUtil.getParentOfType(replaced, PyAssignmentStatement.class);
                if (parentStmt != null) {
                    PyExpression[] targets = parentStmt.getTargets();
                    PyExpression assigned = parentStmt.getAssignedValue();
                    if (targets.length == 1 && assigned instanceof PyReferenceExpression assignedReference) {
                        PyExpression target = targets[0];
                        if (target instanceof PyTargetExpression targetExpression && targetExpression.getName() != null) {
                            String targetName = targetExpression.getName();
                            String refName = assignedReference.getName();
                            if (refName != null && refName.equals(targetName) && !assignedReference.isQualified()) {
                                assignmentToDelete = parentStmt;
                            }
                        }
                    }
                }
            }
            else if (!returnStatements.isEmpty()) {
                PyAssignmentStatement newReturn = generateUniqueAssignment(languageLevel, "result", generatedNames, scopeAnchor);
                for (PyReturnStatement it : returnStatements) {
                    PyAssignmentStatement copy = (PyAssignmentStatement) newReturn.copy();
                    PyExpression copyValue = copy.getAssignedValue();
                    PyExpression returnExpression = it.getExpression();
                    assert copyValue != null && returnExpression != null;
                    copyValue.replace(returnExpression);
                    it.replace(copy);
                }
                PyExpression newReturnValue = newReturn.getAssignedValue();
                assert newReturnValue != null;
                callSite.replace(newReturnValue);
            }

            Function<PsiElement, PsiElement> insertElement =
                elem -> containingStatement.getParent().addBefore(elem, containingStatement);

            List<PyStatement> insertedStatements = new ArrayList<>();
            for (PyAssignmentStatement declaration : declarations) {
                insertedStatements.add((PyStatement) insertElement.apply(declaration));
            }
            List<PsiElement> elementsToInsert = new ArrayList<>();
            for (PsiElement child : replacement.getChildren()) {
                if (!(child instanceof PsiWhiteSpace) && (child instanceof PyStatement || child instanceof PsiComment)) {
                    elementsToInsert.add(child);
                }
            }
            if (!elementsToInsert.isEmpty() && !(elementsToInsert.size() == 1 && elementsToInsert.get(0) instanceof PyPassStatement)) {
                for (PsiElement element : elementsToInsert) {
                    PsiElement inserted = insertElement.apply(element);
                    if (inserted instanceof PyStatement insertedStatement) {
                        PyClassRefactoringUtil.restoreNamedReferences(insertedStatement);
                        insertedStatements.add(insertedStatement);
                    }
                }
            }

            // Delete any redundant self-assignment before formatting
            if (assignmentToDelete != null) {
                assignmentToDelete.delete();
            }

            // Adjust indentation for the inserted block in the final context using a single indent adjustment pass
            if (!insertedStatements.isEmpty()) {
                CodeStyleManager codeStyle = CodeStyleManager.getInstance(myProject);
                PyStatement first = insertedStatements.get(0);
                PyStatement last = insertedStatements.get(insertedStatements.size() - 1);
                PsiFile file = containingStatement.getContainingFile();

                // If there are standalone comments immediately preceding the insertion point,
                // avoid formatting to preserve their position exactly (PY-keeping-comments case).
                boolean hasLeadingStandaloneComments = false;

                PsiElement sib = containingStatement.getPrevSibling();
                while (sib instanceof PsiWhiteSpace || sib instanceof PsiComment) {
                    if (sib instanceof PsiComment) {
                        hasLeadingStandaloneComments = true;
                        break;
                    }
                    sib = sib.getPrevSibling();
                }

                if (!hasLeadingStandaloneComments) {
                    PsiDocumentManager psiDocMgr = PsiDocumentManager.getInstance(myProject);
                    Document doc = psiDocMgr.getDocument(file);
                    if (doc != null) {
                        psiDocMgr.doPostponedOperationsAndUnblockDocument(doc);
                        psiDocMgr.commitDocument(doc);
                    }
                    codeStyle.adjustLineIndent(file, new TextRange(first.getTextRange().getStartOffset(), last.getTextRange().getEndOffset()));
                }
            }

            if (returnStatements.isEmpty()) {
                if (callSite.getParent() instanceof PyExpressionStatement) {
                    containingStatement.delete();
                }
                else {
                    callSite.replace(myGenerator.createExpressionFromText(languageLevel, "None"));
                }
            }
        }

        Set<PsiFile> importFiles = new LinkedHashSet<>();
        for (UsageInfo usage : imports) {
            PsiElement element = usage.getElement();
            if (element != null) {
                importFiles.add(element.getContainingFile());
            }
        }
        for (PsiFile importFile : importFiles) {
            PyClassRefactoringUtil.optimizeImports(importFile);
        }

        if (myRemoveDeclaration) {
            PsiFile file = myFunction.getContainingFile();
            PsiElement stubFunction = PyiUtil.getPythonStub(myFunction);
            if (stubFunction != null && stubFunction.isWritable()) {
                stubFunction.delete();
            }
            List<PyFunction> typingOverloads = PyiUtil.getOverloads(myFunction, typeEvalContext);
            if (!typingOverloads.isEmpty()) {
                for (PyFunction overload : typingOverloads) {
                    overload.delete();
                }
            }
            myFunction.delete();
            PyClassRefactoringUtil.optimizeImports(file);
            for (UsageInfo usage : dunderAll) {
                PsiElement element = usage.getElement();
                if (element != null) {
                    element.delete();
                }
            }
        }
    }

    private Map<String, PyExpression> prepareArguments(
        PyCallExpression callSite,
        List<PyAssignmentStatement> declarations,
        Set<String> generatedNames,
        PsiElement scopeAnchor,
        PyReferenceExpression reference,
        LanguageLevel languageLevel,
        PyResolveContext context,
        boolean selfUsed,
        Set<String> reassignedParams
    ) {
        List<PyCallExpression.PyArgumentsMapping> mappings = PyCallExpressionHelper.mapArguments(callSite, context);
        if (mappings.isEmpty()) {
            throw new IllegalStateException("Can't map arguments for " + reference.getName());
        }
        PyCallExpression.PyArgumentsMapping mapping = mappings.get(0);
        PyParameter[] parameters = myFunction.getParameterList().getParameters();
        PyParameter selfParameter = parameters.length > 0 && parameters[0].isSelf() ? parameters[0] : null;
        boolean isBoundMethod = false;
        if (selfParameter != null) {
            PyCallableType callableType = mapping.getCallableType();
            List<PyCallableParameter> callableParameters =
                callableType != null ? callableType.getParameters(context.getTypeEvalContext()) : null;
            PyCallableParameter firstCallableParameter =
                callableParameters != null && !callableParameters.isEmpty() ? callableParameters.get(0) : null;
            PyParameter firstMappedParameter = firstCallableParameter != null ? firstCallableParameter.getParameter() : null;
            isBoundMethod = firstMappedParameter != selfParameter;
        }

        Map<String, PyExpression> self = Collections.emptyMap();
        if (selfParameter != null && isBoundMethod) {
            PyExpression qualifier = reference.getQualifier();
            if (qualifier == null) {
                throw new IllegalStateException("Function " + myFunction + " has a self parameter, but the call has no qualifier");
            }
            PyExpression selfReplacement;
            if (!selfUsed) {
                selfReplacement = qualifier;
            }
            else if (qualifier instanceof PyReferenceExpression qualifierReference && !qualifierReference.isQualified()) {
                selfReplacement = qualifier;
            }
            else {
                assert myFunctionClass != null;
                String className = myFunctionClass.getName();
                assert className != null;
                selfReplacement = extractDeclaration(className, qualifier, declarations, generatedNames, scopeAnchor, languageLevel).second;
            }
            String selfName = selfParameter.getName();
            assert selfName != null;
            self = Map.of(selfName, selfReplacement);
        }

        Map<String, PyExpression> passedArguments = new LinkedHashMap<>();
        for (Map.Entry<PyExpression, PyCallableParameter> entry : mapping.getMappedParameters().entrySet()) {
            PyExpression arg = entry.getKey();
            PyCallableParameter param = entry.getValue();
            PyExpression argValue = arg instanceof PyKeywordArgument keywordArgument ? keywordArgument.getValueExpression() : arg;
            String paramName = param.getName();
            assert argValue != null && paramName != null;
            Pair<String, PyExpression> pair =
                tryExtractDeclaration(paramName, argValue, declarations, generatedNames, scopeAnchor, languageLevel, reassignedParams);
            passedArguments.put(pair.first, pair.second);
        }

        Map<String, PyExpression> defaultValues = new LinkedHashMap<>();
        for (PyParameter parameter : parameters) {
            if (passedArguments.containsKey(parameter.getName())) {
                continue;
            }
            if (!parameter.hasDefaultValue()) {
                continue;
            }
            String name = parameter.getName();
            PyExpression defaultValue = parameter.getDefaultValue();
            assert name != null && defaultValue != null;
            Pair<String, PyExpression> pair =
                tryExtractDeclaration(name, defaultValue, declarations, generatedNames, scopeAnchor, languageLevel, reassignedParams);
            defaultValues.put(pair.first, pair.second);
        }

        Map<String, PyExpression> result = new LinkedHashMap<>(self);
        result.putAll(passedArguments);
        result.putAll(defaultValues);
        return result;
    }

    /**
     * Returns the names that the body of {@link #myFunction} binds or deletes in the function scope.
     * An inlined parameter with such a name must stay a local variable, because the body changes it.
     */
    private Set<String> collectReassignedNames() {
        PyStatementList body = myFunction.getStatementList();
        Set<String> result = new HashSet<>();
        for (Instruction instruction : ControlFlowCache.getControlFlow(myFunction).getInstructions()) {
            if (!(instruction instanceof ReadWriteInstruction readWriteInstruction)) {
                continue;
            }
            if (!(readWriteInstruction.getAccess().isWriteAccess() || readWriteInstruction.getAccess().isDeleteAccess())) {
                continue;
            }
            PsiElement element = readWriteInstruction.getElement();
            if (element == null || !PsiTreeUtil.isAncestor(body, element, false)) {
                continue;
            }
            String name = readWriteInstruction.getName();
            if (name != null) {
                result.add(name);
            }
        }
        return result;
    }

    private Pair<String, PyExpression> tryExtractDeclaration(
        String paramName, PyExpression arg, List<PyAssignmentStatement> declarations, Set<String> generatedNames,
        PsiElement scopeAnchor, LanguageLevel languageLevel, Set<String> reassignedParams
    ) {
        if (reassignedParams.contains(paramName) || (!(arg instanceof PyReferenceExpression) && !(arg instanceof PyLiteralExpression))) {
            return extractDeclaration(paramName, arg, declarations, generatedNames, scopeAnchor, languageLevel);
        }
        return Pair.create(paramName, arg);
    }

    private Pair<String, PyExpression> extractDeclaration(
        String paramName, PyExpression arg, List<PyAssignmentStatement> declarations, Set<String> generatedNames,
        PsiElement scopeAnchor, LanguageLevel languageLevel
    ) {
        PyAssignmentStatement statement = generateUniqueAssignment(languageLevel, paramName, generatedNames, scopeAnchor);
        PyExpression assignedValue = statement.getAssignedValue();
        assert assignedValue != null;
        assignedValue.replace(arg);
        declarations.add(statement);
        return Pair.create(paramName, statement.getTargets()[0]);
    }

    private PyAssignmentStatement generateUniqueAssignment(
        LanguageLevel level,
        String name,
        Set<String> previouslyGeneratedNames,
        PsiElement scopeAnchor
    ) {
        String uniqueName = PyRefactoringUtil.selectUniqueName(name, scopeAnchor, (newName, anchor) ->
            PyRefactoringUtil.isValidNewName(newName, anchor) && !previouslyGeneratedNames.contains(newName)
        );
        previouslyGeneratedNames.add(uniqueName);
        return myGenerator.createFromText(level, PyAssignmentStatement.class, uniqueName + " = " + uniqueName);
    }

    @Override
    protected LocalizeValue getCommandName() {
        return PyLocalize.refactoringInlineFunctionCommandName(myFunction.getName());
    }

    @Override
    protected @Nullable String getRefactoringId() {
        return PyInlineFunctionHandler.Helper.REFACTORING_ID;
    }

    @Override
    protected Collection<? extends PsiElement> getElementsToWrite(UsageViewDescriptor descriptor) {
        // A kept declaration does not change, so its file can be a read-only or a library file.
        return myRemoveDeclaration ? super.getElementsToWrite(descriptor) : Collections.emptyList();
    }

    @Override
    protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usages) {
        return new UsageViewDescriptor() {
            @Override
            public PsiElement[] getElements() {
                return new PsiElement[]{myFunction};
            }

            @Override
            public String getProcessedElementsHeader() {
                return PyLocalize.refactoringInlineFunctionFunctionToInline().get();
            }

            @Override
            public String getCodeReferencesText(int usagesCount, int filesCount) {
                return PyLocalize.refactoringInlineFunctionInvocationsToBeInlined(filesCount).get();
            }

            @Override
            public String getCommentReferencesText(int usagesCount, int filesCount) {
                return "";
            }
        };
    }
}
