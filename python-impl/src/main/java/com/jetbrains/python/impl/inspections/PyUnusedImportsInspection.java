// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.codeInsight.PyCodeInsightSettings;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.PythonRuntimeService;
import com.jetbrains.python.impl.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.impl.codeInsight.controlflow.PyDataFlowKt;
import com.jetbrains.python.impl.codeInsight.controlflow.Reachability;
import com.jetbrains.python.impl.codeInsight.controlflow.ReadWriteInstruction;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.impl.codeInsight.imports.OptimizeImportsQuickFix;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import com.jetbrains.python.impl.psi.resolve.ImportedResolveResult;
import com.jetbrains.python.impl.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.inspections.PyInspectionExtension;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.Application;
import consulo.application.ReadAction;
import consulo.language.controlFlow.ControlFlowUtil;
import consulo.language.controlFlow.Instruction;
import consulo.language.editor.annotation.HighlightSeverity;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.inject.InjectedLanguageManager;
import consulo.language.psi.*;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.psi.util.QualifiedName;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.dataholder.Key;
import consulo.util.lang.Pair;
import consulo.document.util.TextRange;
import org.jspecify.annotations.Nullable;

import java.util.*;

@ExtensionImpl
public class PyUnusedImportsInspection extends PyInspection {
    private static final Key<Visitor> KEY = Key.create("PyUnusedImportsInspection.Visitor");

    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameUnusedImports();
    }

    @Override
    public PsiElementVisitor buildVisitor(ProblemsHolder holder, boolean isOnTheFly, LocalInspectionToolSession session, Object state) {
        Visitor visitor = new Visitor(holder, this, PyInspectionVisitor.getContext(session));
        session.putUserData(KEY, visitor);
        return visitor;
    }

    @Override
    public void inspectionFinished(LocalInspectionToolSession session, ProblemsHolder holder, Object state) {
        Visitor visitor = Objects.requireNonNull(session.getUserData(KEY));
        ReadAction.run(() -> {
            if (PyCodeInsightSettings.getInstance().HIGHLIGHT_UNUSED_IMPORTS) {
                visitor.highlightUnusedImports();
            }
            visitor.highlightImportsInsideGuards();
        });
        session.putUserData(KEY, null);
    }

    public static class Visitor extends PyInspectionVisitor {
        private final PyInspection myInspection;

        private final Set<PyImportedNameDefiner> myAllImports = new LinkedHashSet<>();
        private final Set<PyImportedNameDefiner> myImportsInsideGuard = new LinkedHashSet<>();
        private final Set<PyImportedNameDefiner> myUsedImports = new LinkedHashSet<>();
        private final Set<PyImportedNameDefiner> myUnresolvedImports = new LinkedHashSet<>();

        public Visitor(@Nullable ProblemsHolder holder, PyInspection inspection, TypeEvalContext typeEvalContext) {
            super(holder, typeEvalContext);
            myInspection = inspection;
        }

        @Override
        public void visitPyImportElement(PyImportElement node) {
            super.visitPyImportElement(node);
            PyFromImportStatement fromImport = PsiTreeUtil.getParentOfType(node, PyFromImportStatement.class);
            if (fromImport == null || !fromImport.isFromFuture()) {
                myAllImports.add(node);
            }
        }

        @Override
        public void visitPyStarImportElement(PyStarImportElement node) {
            super.visitPyStarImportElement(node);
            myAllImports.add(node);
        }

        @Override
        public void visitComment(PsiComment comment) {
            super.visitComment(comment);
            if (comment instanceof PsiLanguageInjectionHost) {
                processInjection((PsiLanguageInjectionHost) comment);
            }
        }

        @Override
        @RequiredReadAction
        public void visitPyElement(PyElement node) {
            super.visitPyElement(node);
            if (node instanceof PsiLanguageInjectionHost host) {
                processInjection(host);
            }
            if (node instanceof PyReferenceOwner referenceOwner) {
                PyResolveContext resolveContext = PyResolveContext.defaultContext(myTypeEvalContext);
                processReference(node, referenceOwner.getReference(resolveContext));
            }
            else {
                for (PsiReference reference : node.getReferences()) {
                    processReference(node, reference);
                }
            }
        }

        @RequiredReadAction
        private void processReference(PyElement node, @Nullable PsiReference reference) {
            if (reference == null || reference.isSoft()) {
                return;
            }
            PyExceptPart guard = getImportErrorGuard(node);
            if (guard != null) {
                processReferenceInImportGuard(node, guard);
                return;
            }
            PsiElement target = null;
            boolean unresolved;
            if (reference instanceof PsiPolyVariantReference polyVariantReference) {
                ResolveResult[] resolveResults = polyVariantReference.multiResolve(false);
                unresolved = (resolveResults.length == 0);
                for (ResolveResult resolveResult : resolveResults) {
                    if (target == null && resolveResult.isValidResult()) {
                        target = resolveResult.getElement();
                    }
                    if (resolveResult instanceof ImportedResolveResult importedResolveResult) {
                        PyImportedNameDefiner definer = importedResolveResult.getDefiner();
                        if (definer != null) {
                            myUsedImports.add(definer);
                        }
                    }
                }
            }
            else {
                target = reference.resolve();
                unresolved = (target == null);
            }
            if (unresolved) {
                boolean ignoreUnresolved = ignoreUnresolved(node, reference) ||
                    PyDataFlowKt.getReachabilityForInspection(node, myTypeEvalContext) != Reachability.REACHABLE;
                if (!ignoreUnresolved) {
                    HighlightSeverity severity = reference instanceof PsiReferenceEx referenceEx
                        ? referenceEx.getUnresolvedHighlightSeverity(myTypeEvalContext)
                        : HighlightSeverity.ERROR;
                    if (severity == null) {
                        return;
                    }
                }
                // don't highlight unresolved imports as unused
                if (node.getParent() instanceof PyImportElement importElement) {
                    myUnresolvedImports.add(importElement);
                }
            }
        }

        private boolean ignoreUnresolved(PyElement node, PsiReference reference) {
            return PyInspectionExtension.EP_NAME.findFirstSafe(Application.get(),
                it -> it.ignoreUnresolvedReference(node, reference, myTypeEvalContext)) != null;
        }

        private void processReferenceInImportGuard(PyElement node, PyExceptPart guard) {
            PyImportElement importElement = PsiTreeUtil.getParentOfType(node, PyImportElement.class);
            if (importElement == null) {
                return;
            }
            String visibleName = importElement.getVisibleName();
            ScopeOwner owner = ScopeUtil.getScopeOwner(importElement);
            if (visibleName == null || owner == null) {
                return;
            }
            Iterable<PsiElement> allWrites = ScopeUtil.getElementsOfAccessType(visibleName, owner, ReadWriteInstruction.ACCESS.WRITE);
            boolean hasWriteInsideGuard = false;
            for (PsiElement it : allWrites) {
                if (PsiTreeUtil.isAncestor(guard, it, false)) {
                    hasWriteInsideGuard = true;
                    break;
                }
            }
            if (!hasWriteInsideGuard && !shouldSkipMissingWriteInsideGuard(guard, visibleName)) {
                myImportsInsideGuard.add(importElement);
            }
        }

        private boolean shouldSkipMissingWriteInsideGuard(PyExceptPart guard, String name) {
            return isDefinedInParentScope(name, guard) || PyBuiltinCache.getInstance(guard).getByName(name) != null ||
                controlFlowAlwaysTerminatesInsideGuard(guard);
        }

        private boolean isDefinedInParentScope(String name, PsiElement anchor) {
            return ScopeUtil.getDeclarationScopeOwner(ScopeUtil.getScopeOwner(anchor), name) != null;
        }

        private boolean controlFlowAlwaysTerminatesInsideGuard(PyExceptPart guard) {
            ScopeOwner owner = ScopeUtil.getScopeOwner(guard);
            if (owner == null) {
                return false;
            }
            consulo.language.controlFlow.ControlFlow flow = ControlFlowCache.getControlFlow(owner);
            Instruction[] instructions = flow.getInstructions();
            int start = ControlFlowUtil.findInstructionNumberByElement(instructions, guard.getExceptClass());
            if (start <= 0) {
                return false;
            }
            boolean[] canEscapeGuard = {false};
            // TODO can we replace that with return ControlFlowUtil.process?
            ControlFlowUtil.process(instructions, start, instruction -> {
                PsiElement e = instruction.getElement();
                if (e != null && !PsiTreeUtil.isAncestor(guard, e, true)) {
                    canEscapeGuard[0] = true;
                    return false;
                }
                return true;
            });
            return !canEscapeGuard[0];
        }

        private @Nullable PyExceptPart getImportErrorGuard(PyElement node) {
            PyImportStatementBase importStatement = PsiTreeUtil.getParentOfType(node, PyImportStatementBase.class);
            if (importStatement == null) {
                return null;
            }
            PyTryPart tryPart = PsiTreeUtil.getParentOfType(importStatement, PyTryPart.class);
            if (tryPart == null) {
                return null;
            }
            PyTryExceptStatement tryExceptStatement = PsiTreeUtil.getParentOfType(tryPart, PyTryExceptStatement.class);
            if (tryExceptStatement == null) {
                return null;
            }
            for (PyExceptPart it : tryExceptStatement.getExceptParts()) {
                PyExpression exceptClass = it.getExceptClass();
                if ("ImportError".equals(exceptClass != null ? exceptClass.getName() : null)) {
                    return it;
                }
            }
            return null;
        }

        private void processInjection(@Nullable PsiLanguageInjectionHost node) {
            if (node == null) {
                return;
            }
            List<Pair<PsiElement, TextRange>> files = InjectedLanguageManager.getInstance(node.getProject()).getInjectedPsiFiles(node);
            if (files != null) {
                for (Pair<PsiElement, TextRange> pair : files) {
                    new PyRecursiveElementVisitor() {
                        @Override
                        public void visitPyElement(PyElement element) {
                            super.visitPyElement(element);
                            if (element instanceof PyReferenceOwner referenceOwner) {
                                PyResolveContext resolveContext = PyResolveContext.defaultContext(myTypeEvalContext);
                                PsiPolyVariantReference reference = referenceOwner.getReference(resolveContext);
                                markTargetImportsAsUsed(reference);
                            }
                        }
                    }.visitElement(Objects.requireNonNull(pair.getFirst()));
                }
            }
        }

        private void markTargetImportsAsUsed(PsiPolyVariantReference reference) {
            ResolveResult[] resolveResults = reference.multiResolve(false);
            for (ResolveResult resolveResult : resolveResults) {
                if (resolveResult instanceof ImportedResolveResult importedResolveResult) {
                    PyImportedNameDefiner definer = importedResolveResult.getDefiner();
                    if (definer != null) {
                        myUsedImports.add(definer);
                    }
                }
            }
        }

        @RequiredReadAction
        public void highlightUnusedImports() {
            List<PsiElement> unused = collectUnusedImportElements();
            for (PsiElement element : unused) {
                if (PyInspectionExtension.EP_NAME.findFirstSafe(Application.get(), it -> it.ignoreUnused(element, myTypeEvalContext)) != null) {
                    continue;
                }
                if (PyDataFlowKt.getReachabilityForInspection(element, myTypeEvalContext) != Reachability.REACHABLE) {
                    continue;
                }
                if (element.getTextLength() > 0) {
                    OptimizeImportsQuickFix fix = new OptimizeImportsQuickFix();
                    registerProblem(element,
                        PyInspectionMessages.problemMessage(params -> PyLocalize.inspUnusedImportsUnusedImportStatement()),
                        ProblemHighlightType.LIKE_UNUSED_SYMBOL, fix);
                }
            }
        }

        @RequiredReadAction
        public void highlightImportsInsideGuards() {
            Set<PyImportedNameDefiner> intersection = new LinkedHashSet<>(myImportsInsideGuard);
            intersection.retainAll(myUsedImports);
            for (PyImportedNameDefiner definer : intersection) {
                if (!(definer instanceof PyImportElement importElement)) {
                    continue;
                }
                PyTargetExpression asElement = importElement.getAsNameElement();
                PyElement toHighlight = asElement != null ? asElement : importElement.getImportReferenceExpression();
                registerProblem(toHighlight,
                    PyInspectionMessages.problemMessage(params -> PyLocalize.inspUnusedImportsTryExceptImportError(params[0]),
                        importElement.getVisibleName()),
                    ProblemHighlightType.LIKE_UNKNOWN_SYMBOL);
            }
        }

        public List<PsiElement> collectUnusedImportElements() {
            if (myAllImports.isEmpty()) {
                return Collections.emptyList();
            }
            // PY-1315 Unused imports inspection shouldn't work in python REPL console
            PyImportedNameDefiner first = myAllImports.iterator().next();
            if (first.getContainingFile() instanceof PyExpressionCodeFragment || PythonRuntimeService.getInstance().isInPydevConsole(first)) {
                return Collections.emptyList();
            }
            List<PsiElement> result = new ArrayList<>();

            Set<PyImportedNameDefiner> unusedImports = new HashSet<>(myAllImports);
            unusedImports.removeAll(myUsedImports);
            // TODO revise that
            unusedImports.removeAll(myUnresolvedImports);

            // Remove those unsed, that are reported to be skipped by extension points
            Set<@Nullable PyImportedNameDefiner> unusedImportToSkip = new HashSet<>();
            for (PyImportedNameDefiner unusedImport : unusedImports) {
                if (PyInspectionExtension.EP_NAME.findFirstSafe(Application.get(), it -> it.ignoreUnusedImports(unusedImport)) != null) {
                    unusedImportToSkip.add(unusedImport);
                }
            }

            unusedImports.removeAll(unusedImportToSkip);

            Set<@Nullable String> usedImportNames = new HashSet<>();
            for (PyImportedNameDefiner usedImport : myUsedImports) {
                for (PyElement e : usedImport.iterateNames()) {
                    usedImportNames.add(e.getName());
                }
            }

            Set<@Nullable PyImportStatementBase> unusedStatements = new HashSet<>();
            QualifiedName packageQName = null;
            List<String> dunderAll = null;

            // TODO: Use strategies instead of pack of "continue"
            for (PyImportedNameDefiner unusedImport : unusedImports) {
                if (packageQName == null) {
                    PsiFile file = unusedImport.getContainingFile();
                    if (file instanceof PyFile pyFile) {
                        dunderAll = pyFile.getDunderAll();
                    }
                    if (file != null && PyUtil.isPackage(file)) {
                        packageQName = QualifiedNameFinder.findShortestImportableQName(file);
                    }
                }
                PyImportStatementBase importStatement = PsiTreeUtil.getParentOfType(unusedImport, PyImportStatementBase.class);
                if (importStatement != null && !unusedStatements.contains(importStatement) && !myUsedImports.contains(unusedImport)) {
                    if (myInspection.isSuppressedFor(importStatement)) {
                        continue;
                    }
                    // don't remove as unused imports in try/except statements
                    if (PsiTreeUtil.getParentOfType(importStatement, PyTryExceptStatement.class) != null) {
                        continue;
                    }
                    // Don't report conditional imports as unused
                    if (PsiTreeUtil.getParentOfType(unusedImport, PyIfStatement.class) != null) {
                        boolean anyUsed = false;
                        for (PyElement it : unusedImport.iterateNames()) {
                            if (usedImportNames.contains(it.getName())) {
                                anyUsed = true;
                                break;
                            }
                        }
                        if (anyUsed) {
                            continue;
                        }
                    }
                    PsiFileSystemItem importedElement;
                    if (unusedImport instanceof PyImportElement importElement) {
                        PsiElement element = importElement.resolve();
                        if (element == null) {
                            if (importElement.getImportedQName() != null) {
                                //Mark import as unused even if it can't be resolved
                                if (areAllImportsUnused(importStatement, unusedImports)) {
                                    result.add(importStatement);
                                }
                                else {
                                    result.add(unusedImport);
                                }
                            }
                            continue;
                        }
                        if (dunderAll != null && dunderAll.contains(importElement.getVisibleName())) {
                            continue;
                        }
                        importedElement = element.getContainingFile();
                    }
                    else {
                        assert importStatement instanceof PyFromImportStatement;
                        importedElement = ((PyFromImportStatement) importStatement).resolveImportSource();
                        if (importedElement == null) {
                            continue;
                        }
                    }
                    if (packageQName != null && importedElement != null) {
                        QualifiedName importedQName = QualifiedNameFinder.findShortestImportableQName(importedElement);
                        if (importedQName != null && importedQName.matchesPrefix(packageQName)) {
                            continue;
                        }
                    }
                    if (unusedImport instanceof PyStarImportElement || areAllImportsUnused(importStatement, unusedImports)) {
                        unusedStatements.add(importStatement);
                        result.add(importStatement);
                    }
                    else {
                        result.add(unusedImport);
                    }
                }
            }
            return result;
        }

        private boolean areAllImportsUnused(PyImportStatementBase importStatement, Set<PyImportedNameDefiner> unusedImports) {
            for (PyImportElement it : importStatement.getImportElements()) {
                if (!unusedImports.contains(it)) {
                    return false;
                }
            }
            return true;
        }

        public void optimizeImports() {
            List<PsiElement> elementsToDelete = collectUnusedImportElements();
            for (PsiElement element : elementsToDelete) {
                PyPsiUtils.assertValid(element);
                element.delete();
            }
        }
    }

    @Override
    public @Nullable String getAlternativeID() {
        return "PyUnresolvedReferences";
    }
}
