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
package com.jetbrains.python.impl.inspections.unresolvedReference;

import com.google.common.collect.ImmutableSet;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.PyCustomMember;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.PyCustomType;
import com.jetbrains.python.impl.codeInsight.PyCodeInsightSettings;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.impl.codeInsight.imports.AutoImportHintAction;
import com.jetbrains.python.impl.codeInsight.imports.AutoImportQuickFix;
import com.jetbrains.python.impl.codeInsight.imports.OptimizeImportsQuickFix;
import com.jetbrains.python.impl.codeInsight.imports.PythonImportUtils;
import com.jetbrains.python.impl.console.PydevConsoleRunner;
import com.jetbrains.python.impl.documentation.docstrings.DocStringParameterReference;
import com.jetbrains.python.impl.documentation.docstrings.DocStringTypeReference;
import com.jetbrains.python.impl.inspections.PyInspection;
import com.jetbrains.python.impl.inspections.PyInspectionVisitor;
import com.jetbrains.python.impl.inspections.PyPackageRequirementsInspection;
import com.jetbrains.python.impl.inspections.PyUnreachableCodeInspection;
import com.jetbrains.python.impl.inspections.quickfix.*;
import com.jetbrains.python.impl.packaging.PyPIPackageUtil;
import com.jetbrains.python.impl.packaging.PyPackageUtil;
import com.jetbrains.python.impl.psi.PyKnownDecoratorUtil;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import com.jetbrains.python.impl.psi.impl.PyImportStatementNavigator;
import com.jetbrains.python.impl.psi.impl.PyImportedModule;
import com.jetbrains.python.impl.psi.impl.references.PyImportReference;
import com.jetbrains.python.impl.psi.impl.references.PyOperatorReference;
import com.jetbrains.python.impl.psi.resolve.ImportedResolveResult;
import com.jetbrains.python.impl.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.impl.psi.types.*;
import com.jetbrains.python.impl.sdk.PythonSdkType;
import com.jetbrains.python.impl.sdk.skeletons.PySkeletonRefresher;
import com.jetbrains.python.inspections.PyInspectionExtension;
import com.jetbrains.python.inspections.PyUnresolvedReferenceQuickFixProvider;
import com.jetbrains.python.packaging.PyRequirement;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.*;
import consulo.annotation.component.ExtensionImpl;
import consulo.component.extension.Extensions;
import consulo.content.bundle.Sdk;
import consulo.document.util.TextRange;
import consulo.language.ast.ASTNode;
import consulo.language.editor.annotation.HighlightSeverity;
import consulo.language.editor.inspection.*;
import consulo.language.editor.inspection.scheme.InspectionProfile;
import consulo.language.editor.inspection.scheme.InspectionProjectProfileManager;
import consulo.language.inject.InjectedLanguageManager;
import consulo.language.psi.*;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.psi.util.QualifiedName;
import consulo.language.util.ModuleUtilCore;
import consulo.localize.LocalizeValue;
import consulo.module.Module;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.SmartList;
import consulo.util.dataholder.Key;
import consulo.util.lang.Comparing;
import consulo.util.lang.Pair;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;

import java.util.*;

import static com.jetbrains.python.impl.inspections.quickfix.AddIgnoredIdentifierQuickFix.END_WILDCARD;

/**
 * Marks references that fail to resolve. Also tracks unused imports and provides "optimize imports" support.
 *
 * @author dcheryasov
 * @since 2008-11-15
 */
@ExtensionImpl
public class PyUnresolvedReferencesInspection extends PyInspection {
    private static final Key<Visitor> KEY = Key.create("PyUnresolvedReferencesInspection.Visitor");
    public static final Key<PyUnresolvedReferencesInspection> SHORT_NAME_KEY =
        Key.create(PyUnresolvedReferencesInspection.class.getSimpleName());

    public static PyUnresolvedReferencesInspection getInstance(PsiElement element) {
        InspectionProfile inspectionProfile =
            InspectionProjectProfileManager.getInstance(element.getProject()).getInspectionProfile();
        return (PyUnresolvedReferencesInspection) inspectionProfile.getUnwrappedTool(SHORT_NAME_KEY.toString(), element);
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameUnresolvedRefs();
    }

    @Nonnull
    @Override
    public InspectionToolState<?> createStateProvider() {
        return new PyUnresolvedReferencesInspectionState();
    }

    @Nonnull
    @Override
    public PsiElementVisitor buildVisitor(
        @Nonnull ProblemsHolder holder,
        boolean isOnTheFly,
        @Nonnull LocalInspectionToolSession session,
        Object state
    ) {
        PyUnresolvedReferencesInspectionState inspectionState = (PyUnresolvedReferencesInspectionState) state;

        Visitor visitor = new Visitor(holder, session, inspectionState.ignoredIdentifiers);
        // buildVisitor() will be called on injected files in the same session - don't overwrite if we already have one
        Visitor existingVisitor = session.getUserData(KEY);
        if (existingVisitor == null) {
            session.putUserData(KEY, visitor);
        }
        return visitor;
    }

    @Override
    public void inspectionFinished(@Nonnull LocalInspectionToolSession session, @Nonnull ProblemsHolder holder, Object state) {
        Visitor visitor = session.getUserData(KEY);
        assert visitor != null;
        if (PyCodeInsightSettings.getInstance().HIGHLIGHT_UNUSED_IMPORTS) {
            visitor.highlightUnusedImports();
        }
        session.putUserData(KEY, null);
    }

    public static class Visitor extends PyInspectionVisitor {
        private Set<PyImportedNameDefiner> myUsedImports = Collections.synchronizedSet(new HashSet<PyImportedNameDefiner>());
        private Set<PyImportedNameDefiner> myAllImports = Collections.synchronizedSet(new HashSet<PyImportedNameDefiner>());
        private final ImmutableSet<String> myIgnoredIdentifiers;
        private volatile Boolean myIsEnabled = null;

        public Visitor(@Nullable ProblemsHolder holder, @Nonnull LocalInspectionToolSession session, List<String> ignoredIdentifiers) {
            super(holder, session);
            myIgnoredIdentifiers = ImmutableSet.copyOf(ignoredIdentifiers);
        }

        public boolean isEnabled(@Nonnull PsiElement anchor) {
            if (myIsEnabled == null) {
                if (PySkeletonRefresher.isGeneratingSkeletons()) {
                    myIsEnabled = false;
                }
                else {
                    myIsEnabled = true;
                }
            }
            return myIsEnabled;
        }

        @Override
        public void visitPyTargetExpression(PyTargetExpression node) {
            checkSlotsAndProperties(node);
        }

        private void checkSlotsAndProperties(PyQualifiedExpression node) {
            PyExpression qualifier = node.getQualifier();
            if (qualifier != null) {
                PyType type = myTypeEvalContext.getType(qualifier);
                if (type instanceof PyClassType) {
                    PyClass pyClass = ((PyClassType) type).getPyClass();
                    if (pyClass.isNewStyleClass(myTypeEvalContext)) {
                        if (pyClass.getOwnSlots() == null) {
                            return;
                        }
                        String attrName = node.getReferencedName();
                        if (!canHaveAttribute(pyClass, attrName)) {
                            for (PyClass ancestor : pyClass.getAncestorClasses(myTypeEvalContext)) {
                                if (ancestor == null) {
                                    return;
                                }
                                if (PyNames.OBJECT.equals(ancestor.getName())) {
                                    break;
                                }
                                if (canHaveAttribute(ancestor, attrName)) {
                                    return;
                                }
                            }
                            ASTNode nameNode = node.getNameElement();
                            PsiElement e = nameNode != null ? nameNode.getPsi() : node;
                            registerProblem(e, "'" + pyClass.getName() + "' object has no attribute '" + attrName + "'");
                        }
                    }
                }
            }
        }

        private boolean canHaveAttribute(@Nonnull PyClass cls, @Nullable String attrName) {
            List<String> slots = cls.getOwnSlots();

            // Class instance can contain attributes with arbitrary names
            if (slots == null || slots.contains(PyNames.DICT)) {
                return true;
            }

            if (attrName != null && cls.findClassAttribute(attrName, true, myTypeEvalContext) != null) {
                return true;
            }

            return slots.contains(attrName) || cls.getProperties().containsKey(attrName);
        }

        @Override
        public void visitPyImportElement(PyImportElement node) {
            super.visitPyImportElement(node);
            PyFromImportStatement fromImport = PsiTreeUtil.getParentOfType(node, PyFromImportStatement.class);
            if (isEnabled(node) && (fromImport == null || !fromImport.isFromFuture())) {
                myAllImports.add(node);
            }
        }

        @Override
        public void visitPyStarImportElement(PyStarImportElement node) {
            super.visitPyStarImportElement(node);
            if (isEnabled(node)) {
                myAllImports.add(node);
            }
        }

        @Nullable
        private static PyExceptPart getImportErrorGuard(PyElement node) {
            PyImportStatementBase importStatement = PsiTreeUtil.getParentOfType(node, PyImportStatementBase.class);
            if (importStatement != null) {
                PyTryPart tryPart = PsiTreeUtil.getParentOfType(node, PyTryPart.class);
                if (tryPart != null) {
                    PyTryExceptStatement tryExceptStatement = PsiTreeUtil.getParentOfType(tryPart, PyTryExceptStatement.class);
                    if (tryExceptStatement != null) {
                        for (PyExceptPart exceptPart : tryExceptStatement.getExceptParts()) {
                            PyExpression expr = exceptPart.getExceptClass();
                            if (expr != null && "ImportError".equals(expr.getName())) {
                                return exceptPart;
                            }
                        }
                    }
                }
            }
            return null;
        }

        private static boolean isGuardedByHasattr(@Nonnull PyElement node, @Nonnull String name) {
            String nodeName = node.getName();
            if (nodeName != null) {
                ScopeOwner owner = ScopeUtil.getDeclarationScopeOwner(node, nodeName);
                PyElement e = PsiTreeUtil.getParentOfType(node, PyConditionalStatementPart.class, PyConditionalExpression.class);
                while (e != null && PsiTreeUtil.isAncestor(owner, e, true)) {
                    ArrayList<PyCallExpression> calls = new ArrayList<>();
                    PyExpression cond = null;
                    if (e instanceof PyConditionalStatementPart) {
                        cond = ((PyConditionalStatementPart) e).getCondition();
                    }
                    else if (e instanceof PyConditionalExpression && PsiTreeUtil.isAncestor(
                        ((PyConditionalExpression) e).getTruePart(),
                        node,
                        true
                    )) {
                        cond = ((PyConditionalExpression) e).getCondition();
                    }
                    if (cond instanceof PyCallExpression) {
                        calls.add((PyCallExpression) cond);
                    }
                    if (cond != null) {
                        PyCallExpression[] callExpressions = PsiTreeUtil.getChildrenOfType(cond, PyCallExpression.class);
                        if (callExpressions != null) {
                            calls.addAll(Arrays.asList(callExpressions));
                        }
                        for (PyCallExpression call : calls) {
                            PyExpression callee = call.getCallee();
                            PyExpression[] args = call.getArguments();
                            // TODO: Search for `node` aliases using aliases analysis
                            if (callee != null && "hasattr".equals(callee.getName()) && args.length == 2 &&
                                nodeName.equals(args[0].getName()) && args[1] instanceof PyStringLiteralExpression &&
                                ((PyStringLiteralExpression) args[1]).getStringValue().equals(name)) {
                                return true;
                            }
                        }
                    }
                    e = PsiTreeUtil.getParentOfType(e, PyConditionalStatementPart.class);
                }
            }
            return false;
        }

        @Override
        public void visitComment(PsiComment comment) {
            super.visitComment(comment);
            if (comment instanceof PsiLanguageInjectionHost) {
                processInjection((PsiLanguageInjectionHost) comment);
            }
        }

        @Override
        public void visitPyElement(PyElement node) {
            super.visitPyElement(node);
            PsiFile file = node.getContainingFile();
            InjectedLanguageManager injectedLanguageManager = InjectedLanguageManager.getInstance(node.getProject());
            if (injectedLanguageManager.isInjectedFragment(file)) {
                PsiLanguageInjectionHost host = injectedLanguageManager.getInjectionHost(node);
                processInjection(host);
            }
            if (node instanceof PyReferenceOwner) {
                PyResolveContext resolveContext = PyResolveContext.noImplicits().withTypeEvalContext(myTypeEvalContext);
                processReference(node, ((PyReferenceOwner) node).getReference(resolveContext));
            }
            else {
                if (node instanceof PsiLanguageInjectionHost) {
                    processInjection((PsiLanguageInjectionHost) node);
                }
                for (PsiReference reference : node.getReferences()) {
                    processReference(node, reference);
                }
            }
        }

        private void processInjection(@Nullable PsiLanguageInjectionHost node) {
            if (node == null) {
                return;
            }
            List<Pair<PsiElement, TextRange>> files =
                InjectedLanguageManager.getInstance(node.getProject()).getInjectedPsiFiles(node);
            if (files != null) {
                for (Pair<PsiElement, TextRange> pair : files) {
                    new PyRecursiveElementVisitor() {
                        @Override
                        public void visitPyElement(PyElement element) {
                            super.visitPyElement(element);
                            if (element instanceof PyReferenceOwner) {
                                PyResolveContext resolveContext =
                                    PyResolveContext.noImplicits().withTypeEvalContext(myTypeEvalContext);
                                PsiPolyVariantReference reference = ((PyReferenceOwner) element).getReference(resolveContext);
                                markTargetImportsAsUsed(reference);
                            }
                        }
                    }.visitElement(pair.getFirst());
                }
            }
        }

        private void markTargetImportsAsUsed(@Nonnull PsiPolyVariantReference reference) {
            ResolveResult[] resolveResults = reference.multiResolve(false);
            for (ResolveResult resolveResult : resolveResults) {
                if (resolveResult instanceof ImportedResolveResult) {
                    PyImportedNameDefiner definer = ((ImportedResolveResult) resolveResult).getDefiner();
                    if (definer != null) {
                        myUsedImports.add(definer);
                    }
                }
            }
        }

        private void processReference(PyElement node, @Nullable PsiReference reference) {
            if (!isEnabled(node) || reference == null || reference.isSoft()) {
                return;
            }
            HighlightSeverity severity = HighlightSeverity.ERROR;
            if (reference instanceof PsiReferenceEx) {
                severity = ((PsiReferenceEx) reference).getUnresolvedHighlightSeverity(myTypeEvalContext);
                if (severity == null) {
                    return;
                }
            }
            PyExceptPart guard = getImportErrorGuard(node);
            if (guard != null) {
                processReferenceInImportGuard(node, guard);
                return;
            }
            if (node instanceof PyQualifiedExpression) {
                PyQualifiedExpression qExpr = (PyQualifiedExpression) node;
                PyExpression qualifier = qExpr.getQualifier();
                String name = node.getName();
                if (qualifier != null && name != null && isGuardedByHasattr(qualifier, name)) {
                    return;
                }
            }
            PsiElement target = null;
            boolean unresolved;
            if (reference instanceof PsiPolyVariantReference) {
                PsiPolyVariantReference poly = (PsiPolyVariantReference) reference;
                ResolveResult[] resolveResults = poly.multiResolve(false);
                unresolved = (resolveResults.length == 0);
                for (ResolveResult resolveResult : resolveResults) {
                    if (target == null && resolveResult.isValidResult()) {
                        target = resolveResult.getElement();
                    }
                    if (resolveResult instanceof ImportedResolveResult) {
                        PyImportedNameDefiner definer = ((ImportedResolveResult) resolveResult).getDefiner();
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
                boolean ignoreUnresolved = false;
                for (PyInspectionExtension extension : Extensions.getExtensions(PyInspectionExtension.EP_NAME)) {
                    if (extension.ignoreUnresolvedReference(node, reference)) {
                        ignoreUnresolved = true;
                        break;
                    }
                }
                if (!ignoreUnresolved) {
                    registerUnresolvedReferenceProblem(node, reference, severity);
                }
                // don't highlight unresolved imports as unused
                if (node.getParent() instanceof PyImportElement) {
                    myAllImports.remove(node.getParent());
                }
            }
            else if (reference instanceof PyImportReference &&
                target == reference.getElement().getContainingFile() &&
                !isContainingFileImportAllowed(node, (PsiFile) target)) {
                registerProblem(node, "Import resolves to its containing file");
            }
        }

        private static boolean isContainingFileImportAllowed(PyElement node, PsiFile target) {
            return PyImportStatementNavigator.getImportStatementByElement(node) == null && target.getName().equals(PyNames.INIT_DOT_PY);
        }

        private void processReferenceInImportGuard(PyElement node, PyExceptPart guard) {
            PyImportElement importElement = PsiTreeUtil.getParentOfType(node, PyImportElement.class);
            if (importElement != null) {
                String visibleName = importElement.getVisibleName();
                ScopeOwner owner = ScopeUtil.getScopeOwner(importElement);
                if (visibleName != null && owner != null) {
                    Collection<PsiElement> allWrites = ScopeUtil.getReadWriteElements(visibleName, owner, false, true);
                    Collection<PsiElement> writesInsideGuard = new ArrayList<>();
                    for (PsiElement write : allWrites) {
                        if (PsiTreeUtil.isAncestor(guard, write, false)) {
                            writesInsideGuard.add(write);
                        }
                    }
                    if (writesInsideGuard.isEmpty()) {
                        PyTargetExpression asElement = importElement.getAsNameElement();
                        PyElement toHighlight = asElement != null ? asElement : node;
                        registerProblem(
                            toHighlight,
                            PyLocalize.inspTryExceptImportError(visibleName).get(),
                            ProblemHighlightType.LIKE_UNKNOWN_SYMBOL
                        );
                    }
                }
            }
        }

        private void registerUnresolvedReferenceProblem(
            @Nonnull PyElement node,
            @Nonnull PsiReference reference,
            @Nonnull HighlightSeverity severity
        ) {
            if (reference instanceof DocStringTypeReference) {
                return;
            }
            LocalizeValue description = LocalizeValue.empty();
            PsiElement element = reference.getElement();
            String text = element.getText();
            TextRange rangeInElement = reference.getRangeInElement();
            String refText = text;  // text of the part we're working with
            if (rangeInElement.getStartOffset() > 0 && rangeInElement.getEndOffset() > 0) {
                refText = rangeInElement.substring(text);
            }

            List<LocalQuickFix> actions = new ArrayList<>(2);
            String refName =
                (element instanceof PyQualifiedExpression) ? ((PyQualifiedExpression) element).getReferencedName() : refText;
            // Empty text, nothing to highlight
            if (refName == null || refName.length() <= 0) {
                return;
            }

            List<QualifiedName> qualifiedNames = getCanonicalNames(reference, myTypeEvalContext);
            for (QualifiedName name : qualifiedNames) {
                String canonicalName = name.toString();
                for (String ignored : myIgnoredIdentifiers) {
                    if (ignored.endsWith(END_WILDCARD)) {
                        String prefix = ignored.substring(0, ignored.length() - END_WILDCARD.length());
                        if (canonicalName.startsWith(prefix)) {
                            return;
                        }
                    }
                    else if (canonicalName.equals(ignored)) {
                        return;
                    }
                }
            }
            // Legacy non-qualified ignore patterns
            if (myIgnoredIdentifiers.contains(refName)) {
                return;
            }

            if (element instanceof PyReferenceExpression) {
                PyReferenceExpression expr = (PyReferenceExpression) element;
                if (PyNames.COMPARISON_OPERATORS.contains(refName)) {
                    return;
                }
                if (!expr.isQualified()) {
                    if (PyUnreachableCodeInspection.hasAnyInterruptedControlFlowPaths(expr)) {
                        return;
                    }
                    if (LanguageLevel.forElement(node).isOlderThan(LanguageLevel.PYTHON26)) {
                        if ("with".equals(refName)) {
                            actions.add(new UnresolvedRefAddFutureImportQuickFix());
                        }
                    }
                    if (refText.equals("true") || refText.equals("false")) {
                        actions.add(new UnresolvedRefTrueFalseQuickFix(element));
                    }
                    addAddSelfFix(node, expr, actions);
                    PyCallExpression callExpression = PsiTreeUtil.getParentOfType(element, PyCallExpression.class);
                    if (callExpression != null && (!(callExpression.getCallee() instanceof PyQualifiedExpression) || ((PyQualifiedExpression) callExpression
                        .getCallee()).getQualifier() == null)) {
                        actions.add(new UnresolvedRefCreateFunctionQuickFix(callExpression, expr));
                    }
                    PyFunction parentFunction = PsiTreeUtil.getParentOfType(element, PyFunction.class);
                    PyDecorator decorator = PsiTreeUtil.getParentOfType(element, PyDecorator.class);
                    PyAnnotation annotation = PsiTreeUtil.getParentOfType(element, PyAnnotation.class);
                    PyImportStatement importStatement = PsiTreeUtil.getParentOfType(element, PyImportStatement.class);
                    if (parentFunction != null && decorator == null && annotation == null && importStatement == null) {
                        actions.add(new UnresolvedReferenceAddParameterQuickFix(refName));
                    }
                    actions.add(new PyRenameUnresolvedRefQuickFix());
                }
                // unqualified:
                // may be module's
                if (PyModuleType.getPossibleInstanceMembers().contains(refName)) {
                    return;
                }
                // may be a "try: import ..."; not an error not to resolve
                if ((PsiTreeUtil.getParentOfType(
                    PsiTreeUtil.getParentOfType(node, PyImportElement.class),
                    PyTryExceptStatement.class,
                    PyIfStatement.class
                ) != null)) {
                    severity = HighlightSeverity.WEAK_WARNING;
                    description = PyLocalize.inspModule$0NotFound(refText);
                    // TODO: mark the node so that future references pointing to it won't result in a error, but in a warning
                }
            }
            if (reference instanceof PsiReferenceEx referenceEx && description.isEmpty()) {
                description = LocalizeValue.localizeTODO(referenceEx.getUnresolvedDescription());
            }
            if (description.isEmpty()) {
                boolean markedQualified = false;
                if (element instanceof PyQualifiedExpression) {
                    // TODO: Add __qualname__ for Python 3.3 to the skeleton of <class 'object'>, introduce a pseudo-class skeleton for
                    // <class 'function'>
                    if ("__qualname__".equals(refText) && LanguageLevel.forElement(element).isAtLeast(LanguageLevel.PYTHON33)) {
                        return;
                    }
                    PyQualifiedExpression expr = (PyQualifiedExpression) element;
                    if (PyNames.COMPARISON_OPERATORS.contains(expr.getReferencedName())) {
                        return;
                    }
                    PyExpression qualifier = expr.getQualifier();
                    if (qualifier != null) {
                        PyType type = myTypeEvalContext.getType(qualifier);
                        if (type != null) {
                            if (ignoreUnresolvedMemberForType(type, reference, refName)) {
                                return;
                            }
                            addCreateMemberFromUsageFixes(type, reference, refText, actions);
                            if (type instanceof PyClassType) {
                                PyClassType classType = (PyClassType) type;
                                if (reference instanceof PyOperatorReference operatorRef) {
                                    String className = type.getName();
                                    if (classType.isDefinition()) {
                                        PyClassLikeType metaClassType = classType.getMetaClassType(myTypeEvalContext, true);
                                        if (metaClassType != null) {
                                            className = metaClassType.getName();
                                        }
                                    }
                                    description = PyLocalize.inspUnresolvedOperatorRef(
                                        className,
                                        refName,
                                        operatorRef.getReadableOperatorName()
                                    );
                                }
                                else {
                                    List<String> slots = classType.getPyClass().getOwnSlots();

                                    if (slots != null && slots.contains(refName)) {
                                        return;
                                    }

                                    description = PyLocalize.inspUnresolvedRef$0ForClass$1(refText, type.getName());
                                }
                                markedQualified = true;
                            }
                            else if (isHasCustomMember(refName, type)) {
                                // We have dynamic members
                                return;
                            }
                            else {
                                description = PyLocalize.inspCannotFind$0In$1(refText, type.getName());
                                markedQualified = true;
                            }
                        }
                    }
                }
                if (!markedQualified) {
                    description = PyLocalize.inspUnresolvedRef$0(refText);

                    // look in other imported modules for this whole name
                    if (PythonImportUtils.isImportable(element)) {
                        addAutoImportFix(node, reference, actions);
                    }

                    addCreateClassFix(refText, element, actions);
                }
            }
            ProblemHighlightType hl_type;
            if (severity == HighlightSeverity.WARNING) {
                hl_type = ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
            }
            else if (severity == HighlightSeverity.ERROR) {
                hl_type = ProblemHighlightType.GENERIC_ERROR;
            }
            else {
                hl_type = ProblemHighlightType.LIKE_UNKNOWN_SYMBOL;
            }

            PyImportStatementBase importStatementBase = PsiTreeUtil.getParentOfType(element, PyImportStatementBase.class);

            if (qualifiedNames.size() == 1) {
                QualifiedName qualifiedName = qualifiedNames.get(0);
                actions.add(new AddIgnoredIdentifierQuickFix(qualifiedName, false));
                if (qualifiedName.getComponentCount() > 1) {
                    actions.add(new AddIgnoredIdentifierQuickFix(qualifiedName.removeLastComponent(), true));
                }
            }
            addPluginQuickFixes(reference, actions);

            if (reference instanceof PyImportReference) {
                // TODO: Ignore references in the second part of the 'from ... import ...' expression
                QualifiedName qname = QualifiedName.fromDottedString(refName);
                List<String> components = qname.getComponents();
                if (!components.isEmpty()) {
                    String packageName = components.get(0);
                    Module module = ModuleUtilCore.findModuleForPsiElement(node);
                    Sdk sdk = PythonSdkType.findPythonSdk(module);
                    if (module != null && sdk != null && PyPackageUtil.packageManagementEnabled(sdk)) {
                        if (PyPIPackageUtil.INSTANCE.isInPyPI(packageName)) {
                            addInstallPackageAction(actions, packageName, module, sdk);
                        }
                        else {
                            if (PyPIPackageUtil.PACKAGES_TOPLEVEL.containsKey(packageName)) {
                                String suggestedPackage = PyPIPackageUtil.PACKAGES_TOPLEVEL.get(packageName);
                                addInstallPackageAction(actions, suggestedPackage, module, sdk);
                            }
                        }
                    }
                }
            }

            registerProblem(node, description.get(), hl_type, null, rangeInElement, actions.toArray(new LocalQuickFix[actions.size()]));
        }

        private static void addInstallPackageAction(List<LocalQuickFix> actions, String packageName, Module module, Sdk sdk) {
            List<PyRequirement> requirements = Collections.singletonList(new PyRequirement(packageName));
            LocalizeValue name = LocalizeValue.localizeTODO("Install package " + packageName);
            actions.add(new PyPackageRequirementsInspection.PyInstallRequirementsFix(name, module, sdk, requirements));
        }

        /**
         * Checks if type  is custom type  and has custom member with certain name
         *
         * @param refName name to check
         * @param type    type
         * @return true if has one
         */
        private static boolean isHasCustomMember(@Nonnull String refName, @Nonnull PyType type) {
            // TODO: check
            return false;
            /*return (type instanceof PyCustomType) && ((PyCustomType)type).hasMember(refName);*/
        }

        /**
         * Return the canonical qualified names for a reference (even for an unresolved one).
         * If reference is qualified and its qualifier has union type, all possible canonical names will be returned.
         */
        @Nonnull
        private static List<QualifiedName> getCanonicalNames(@Nonnull PsiReference reference, @Nonnull TypeEvalContext context) {
            PsiElement element = reference.getElement();
            List<QualifiedName> result = new SmartList<>();
            if (reference instanceof PyOperatorReference && element instanceof PyQualifiedExpression) {
                PyExpression receiver = ((PyOperatorReference) reference).getReceiver();
                if (receiver != null) {
                    PyType type = context.getType(receiver);
                    if (type instanceof PyClassType) {
                        String methodName = ((PyQualifiedExpression) element).getReferencedName();
                        ContainerUtil.addIfNotNull(result, extractAttributeQNameFromClassType(methodName, (PyClassType) type));
                    }
                }
            }
            else if (element instanceof PyReferenceExpression) {
                PyReferenceExpression expr = (PyReferenceExpression) element;
                PyExpression qualifier = expr.getQualifier();
                String exprName = expr.getName();
                if (exprName != null) {
                    if (qualifier != null) {
                        PyType type = context.getType(qualifier);
                        if (type instanceof PyClassType) {
                            ContainerUtil.addIfNotNull(result, extractAttributeQNameFromClassType(exprName, (PyClassType) type));
                        }
                        else if (type instanceof PyModuleType) {
                            PyFile file = ((PyModuleType) type).getModule();
                            QualifiedName name = QualifiedNameFinder.findCanonicalImportPath(file, element);
                            if (name != null) {
                                ContainerUtil.addIfNotNull(result, name.append(exprName));
                            }
                        }
                        else if (type instanceof PyImportedModuleType) {
                            PyImportedModule module = ((PyImportedModuleType) type).getImportedModule();
                            PsiElement resolved = module.resolve();
                            if (resolved != null) {
                                QualifiedName path = QualifiedNameFinder.findCanonicalImportPath(resolved, element);
                                if (path != null) {
                                    ContainerUtil.addIfNotNull(result, path.append(exprName));
                                }
                            }
                        }
                        else if (type instanceof PyUnionType) {
                            for (PyType memberType : ((PyUnionType) type).getMembers()) {
                                if (memberType instanceof PyClassType) {
                                    ContainerUtil.addIfNotNull(
                                        result,
                                        extractAttributeQNameFromClassType(exprName, (PyClassType) memberType)
                                    );
                                }
                            }
                        }
                    }
                    else {
                        PsiElement parent = element.getParent();
                        if (parent instanceof PyImportElement) {
                            PyImportStatementBase importStmt = PsiTreeUtil.getParentOfType(parent, PyImportStatementBase.class);
                            if (importStmt instanceof PyImportStatement) {
                                ContainerUtil.addIfNotNull(result, QualifiedName.fromComponents(exprName));
                            }
                            else if (importStmt instanceof PyFromImportStatement) {
                                PsiElement resolved = ((PyFromImportStatement) importStmt).resolveImportSource();
                                if (resolved != null) {
                                    QualifiedName path = QualifiedNameFinder.findCanonicalImportPath(resolved, element);
                                    if (path != null) {
                                        ContainerUtil.addIfNotNull(result, path.append(exprName));
                                    }
                                }
                            }
                        }
                        else {
                            QualifiedName path = QualifiedNameFinder.findCanonicalImportPath(element, element);
                            if (path != null) {
                                ContainerUtil.addIfNotNull(result, path.append(exprName));
                            }
                        }
                    }
                }
            }
            else if (reference instanceof DocStringParameterReference) {
                ContainerUtil.addIfNotNull(result, QualifiedName.fromDottedString(reference.getCanonicalText()));
            }
            return result;
        }

        private static QualifiedName extractAttributeQNameFromClassType(String exprName, PyClassType type) {
            String name = type.getClassQName();
            if (name != null) {
                return QualifiedName.fromDottedString(name).append(exprName);
            }
            return null;
        }

        private boolean ignoreUnresolvedMemberForType(@Nonnull PyType type, PsiReference reference, String name) {
            if (type instanceof PyNoneType || PyTypeChecker.isUnknown(type)) {
                // this almost always means that we don't know the type, so don't show an error in this case
                return true;
            }
            if (type instanceof PyStructuralType && ((PyStructuralType) type).isInferredFromUsages()) {
                return true;
            }
            if (type instanceof PyImportedModuleType) {
                PyImportedModule module = ((PyImportedModuleType) type).getImportedModule();
                if (module.resolve() == null) {
                    return true;
                }
            }
            if (type instanceof PyCustomType) {
                // Skip custom member types that mimics another class with fuzzy parents
                for (PyClassLikeType mimic : ((PyCustomType) type).getTypesToMimic()) {
                    if (!(mimic instanceof PyClassType)) {
                        continue;
                    }
                    if (PyUtil.hasUnresolvedAncestors(((PyClassType) mimic).getPyClass(), myTypeEvalContext)) {
                        return true;
                    }
                }
            }
            if (type instanceof PyClassTypeImpl) {
                PyClass cls = ((PyClassType) type).getPyClass();
                if (PyTypeChecker.overridesGetAttr(cls, myTypeEvalContext)) {
                    return true;
                }
                if (cls.findProperty(name, true, myTypeEvalContext) != null) {
                    return true;
                }
                if (PyUtil.hasUnresolvedAncestors(cls, myTypeEvalContext)) {
                    return true;
                }
                if (isDecoratedAsDynamic(cls, true)) {
                    return true;
                }
                if (hasUnresolvedDynamicMember((PyClassType) type, reference, name, myTypeEvalContext)) {
                    return true;
                }
            }
            if (type instanceof PyFunctionTypeImpl) {
                PyCallable callable = ((PyFunctionTypeImpl) type).getCallable();
                if (callable instanceof PyFunction && PyKnownDecoratorUtil.hasNonBuiltinDecorator(
                    (PyFunction) callable,
                    myTypeEvalContext
                )) {
                    return true;
                }
            }
            for (PyInspectionExtension extension : Extensions.getExtensions(PyInspectionExtension.EP_NAME)) {
                if (extension.ignoreUnresolvedMember(type, name)) {
                    return true;
                }
            }
            return false;
        }

        private static boolean hasUnresolvedDynamicMember(
            @Nonnull PyClassType type,
            PsiReference reference,
            @Nonnull String name,
            TypeEvalContext typeEvalContext
        ) {
            for (PyClassMembersProvider provider : Extensions.getExtensions(PyClassMembersProvider.EP_NAME)) {
                Collection<PyCustomMember> resolveResult = provider.getMembers(type, reference.getElement(), typeEvalContext);
                for (PyCustomMember member : resolveResult) {
                    if (member.getName().equals(name)) {
                        return true;
                    }
                }
            }
            return false;
        }

        private boolean isDecoratedAsDynamic(@Nonnull PyClass cls, boolean inherited) {
            if (inherited) {
                if (isDecoratedAsDynamic(cls, false)) {
                    return true;
                }
                for (PyClass base : cls.getAncestorClasses(myTypeEvalContext)) {
                    if (base != null && isDecoratedAsDynamic(base, false)) {
                        return true;
                    }
                }
            }
            else {
                if (cls.getDecoratorList() != null) {
                    return true;
                }
                String docString = cls.getDocStringValue();
                if (docString != null && docString.contains("@DynamicAttrs")) {
                    return true;
                }
            }
            return false;
        }

        private void addCreateMemberFromUsageFixes(PyType type, PsiReference reference, String refText, List<LocalQuickFix> actions) {
            PsiElement element = reference.getElement();
            if (type instanceof PyClassTypeImpl) {
                PyClass cls = ((PyClassType) type).getPyClass();
                if (!PyBuiltinCache.getInstance(element).isBuiltin(cls)) {
                    if (element.getParent() instanceof PyCallExpression) {
                        actions.add(new AddMethodQuickFix(refText, cls.getName(), true));
                    }
                    else if (!(reference instanceof PyOperatorReference)) {
                        actions.add(new AddFieldQuickFix(refText, "None", type.getName(), true));
                    }
                }
            }
            else if (type instanceof PyModuleType) {
                PyFile file = ((PyModuleType) type).getModule();
                actions.add(new AddFunctionQuickFix(refText, file.getName()));
                addCreateClassFix(refText, element, actions);
            }
        }

        private void addAddSelfFix(PyElement node, PyReferenceExpression expr, List<LocalQuickFix> actions) {
            PyClass containedClass = PsiTreeUtil.getParentOfType(node, PyClass.class);
            PyFunction function = PsiTreeUtil.getParentOfType(node, PyFunction.class);
            if (containedClass != null && function != null) {
                PyParameter[] parameters = function.getParameterList().getParameters();
                if (parameters.length == 0) {
                    return;
                }
                String qualifier = parameters[0].getText();
                PyDecoratorList decoratorList = function.getDecoratorList();
                boolean isClassMethod = false;
                if (decoratorList != null) {
                    for (PyDecorator decorator : decoratorList.getDecorators()) {
                        PyExpression callee = decorator.getCallee();
                        if (callee != null && PyNames.CLASSMETHOD.equals(callee.getText())) {
                            isClassMethod = true;
                        }
                    }
                }
                for (PyTargetExpression target : containedClass.getInstanceAttributes()) {
                    if (!isClassMethod && Comparing.strEqual(node.getName(), target.getName())) {
                        actions.add(new UnresolvedReferenceAddSelfQuickFix(expr, qualifier));
                    }
                }
                for (PyStatement statement : containedClass.getStatementList().getStatements()) {
                    if (statement instanceof PyAssignmentStatement) {
                        PyExpression lhsExpression = ((PyAssignmentStatement) statement).getLeftHandSideExpression();
                        if (lhsExpression != null && lhsExpression.getText().equals(expr.getText())) {
                            PyExpression assignedValue = ((PyAssignmentStatement) statement).getAssignedValue();
                            if (assignedValue instanceof PyCallExpression) {
                                PyType type = myTypeEvalContext.getType(assignedValue);
                                if (type != null && type instanceof PyClassTypeImpl) {
                                    if (((PyCallExpression) assignedValue).isCalleeText(PyNames.PROPERTY)) {
                                        actions.add(new UnresolvedReferenceAddSelfQuickFix(expr, qualifier));
                                    }
                                }
                            }
                        }
                    }
                }
                for (PyFunction method : containedClass.getMethods()) {
                    if (expr.getText().equals(method.getName())) {
                        actions.add(new UnresolvedReferenceAddSelfQuickFix(expr, qualifier));
                    }
                }
            }
        }

        private static void addAutoImportFix(PyElement node, PsiReference reference, List<LocalQuickFix> actions) {
            PsiFile file = InjectedLanguageManager.getInstance(node.getProject()).getTopLevelFile(node);
            if (!(file instanceof PyFile)) {
                return;
            }
            AutoImportQuickFix importFix = PythonImportUtils.proposeImportFix(node, reference);
            if (importFix != null) {
                if (!suppressHintForAutoImport(node, importFix) && PyCodeInsightSettings.getInstance().SHOW_IMPORT_POPUP) {
                    AutoImportHintAction autoImportHintAction = new AutoImportHintAction(importFix);
                    actions.add(autoImportHintAction);
                }
                else {
                    actions.add(importFix);
                }
                if (ScopeUtil.getScopeOwner(node) instanceof PyFunction) {
                    actions.add(importFix.forLocalImport());
                }
            }
            else {
                String refName =
                    (node instanceof PyQualifiedExpression) ? ((PyQualifiedExpression) node).getReferencedName() : node.getText();
                if (refName == null) {
                    return;
                }
                QualifiedName qname = QualifiedName.fromDottedString(refName);
                List<String> components = qname.getComponents();
                if (!components.isEmpty()) {
                    String packageName = components.get(0);
                    Module module = ModuleUtilCore.findModuleForPsiElement(node);
                    if (PyPIPackageUtil.INSTANCE.isInPyPI(packageName) && PythonSdkType.findPythonSdk(module) != null) {
                        actions.add(new PyPackageRequirementsInspection.InstallAndImportQuickFix(packageName, packageName, node));
                    }
                    else {
                        String packageAlias = PyPackageAliasesProvider.commonImportAliases.get(packageName);
                        if (packageAlias != null && PyPIPackageUtil.INSTANCE.isInPyPI(packageName) && PythonSdkType.findPythonSdk(module) != null) {
                            actions.add(new PyPackageRequirementsInspection.InstallAndImportQuickFix(packageAlias, packageName, node));
                        }
                    }
                }
            }
        }

        private static boolean suppressHintForAutoImport(PyElement node, AutoImportQuickFix importFix) {
            // if the context doesn't look like a function call and we only found imports of functions, suggest auto-import
            // as a quickfix but no popup balloon (PY-2312)
            if (!isCall(node) && importFix.hasOnlyFunctions()) {
                return true;
            }
            // if we're in a class context and the class defines a variable with the same name, offer auto-import only as quickfix,
            // not as popup
            PyClass containingClass = PsiTreeUtil.getParentOfType(node, PyClass.class);
            if (containingClass != null && (containingClass.findMethodByName(
                importFix.getNameToImport(),
                true,
                null
            ) != null || containingClass.findInstanceAttribute(
                importFix.getNameToImport(),
                true
            ) != null)) {
                return true;
            }
            return false;
        }

        private void addCreateClassFix(@NonNls String refText, PsiElement element, List<LocalQuickFix> actions) {
            if (refText.length() > 2 && Character.isUpperCase(refText.charAt(0)) && !refText.toUpperCase().equals(refText) &&
                PsiTreeUtil.getParentOfType(element, PyImportStatementBase.class) == null) {
                PsiElement anchor = element;
                if (element instanceof PyQualifiedExpression) {
                    PyExpression expr = ((PyQualifiedExpression) element).getQualifier();
                    if (expr != null) {
                        PyType type = myTypeEvalContext.getType(expr);
                        if (type instanceof PyModuleType) {
                            anchor = ((PyModuleType) type).getModule();
                        }
                        else {
                            anchor = null;
                        }
                    }
                    if (anchor != null) {
                        actions.add(new CreateClassQuickFix(refText, anchor));
                    }
                }
            }
        }

        private static boolean isCall(PyElement node) {
            PyCallExpression callExpression = PsiTreeUtil.getParentOfType(node, PyCallExpression.class);
            return callExpression != null && node == callExpression.getCallee();
        }

        private static void addPluginQuickFixes(PsiReference reference, List<LocalQuickFix> actions) {
            for (PyUnresolvedReferenceQuickFixProvider provider : PyUnresolvedReferenceQuickFixProvider.EP_NAME.getExtensionList()) {
                provider.registerQuickFixes(reference, actions::add);
            }
        }

        public void highlightUnusedImports() {
            List<PsiElement> unused = collectUnusedImportElements();
            for (PsiElement element : unused) {
                if (element.getTextLength() > 0) {
                    registerProblem(
                        element,
                        "Unused import statement",
                        ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                        null,
                        new OptimizeImportsQuickFix()
                    );
                }
            }
        }

        private List<PsiElement> collectUnusedImportElements() {
            if (myAllImports.isEmpty()) {
                return Collections.emptyList();
            }
            // PY-1315 Unused imports inspection shouldn't work in python REPL console
            PyImportedNameDefiner first = myAllImports.iterator().next();
            if (first.getContainingFile() instanceof PyExpressionCodeFragment || PydevConsoleRunner.isInPydevConsole(first)) {
                return Collections.emptyList();
            }
            List<PsiElement> result = new ArrayList<>();

            Set<PyImportedNameDefiner> unusedImports = new HashSet<>(myAllImports);
            unusedImports.removeAll(myUsedImports);

            // Remove those unsed, that are reported to be skipped by extension points
            Set<PyImportedNameDefiner> unusedImportToSkip = new HashSet<>();
            for (PyImportedNameDefiner unusedImport : unusedImports) {
                if (importShouldBeSkippedByExtPoint(unusedImport)) { // Pass to extension points
                    unusedImportToSkip.add(unusedImport);
                }
            }

            unusedImports.removeAll(unusedImportToSkip);

            Set<String> usedImportNames = new HashSet<>();
            for (PyImportedNameDefiner usedImport : myUsedImports) {
                for (PyElement e : usedImport.iterateNames()) {
                    usedImportNames.add(e.getName());
                }
            }

            Set<PyImportStatementBase> unusedStatements = new HashSet<>();
            PyUnresolvedReferencesInspection suppressableInspection = new PyUnresolvedReferencesInspection();
            QualifiedName packageQName = null;
            List<String> dunderAll = null;

            // TODO: Use strategies instead of pack of "continue"
            for (PyImportedNameDefiner unusedImport : unusedImports) {
                if (packageQName == null) {
                    PsiFile file = unusedImport.getContainingFile();
                    if (file instanceof PyFile) {
                        dunderAll = ((PyFile) file).getDunderAll();
                    }
                    if (file != null && PyUtil.isPackage(file)) {
                        packageQName = QualifiedNameFinder.findShortestImportableQName(file);
                    }
                }
                PyImportStatementBase importStatement = PsiTreeUtil.getParentOfType(unusedImport, PyImportStatementBase.class);
                if (importStatement != null && !unusedStatements.contains(importStatement) && !myUsedImports.contains(unusedImport)) {
                    if (suppressableInspection.isSuppressedFor(importStatement)) {
                        continue;
                    }
                    // don't remove as unused imports in try/except statements
                    if (PsiTreeUtil.getParentOfType(importStatement, PyTryExceptStatement.class) != null) {
                        continue;
                    }
                    // Don't report conditional imports as unused
                    if (PsiTreeUtil.getParentOfType(unusedImport, PyIfStatement.class) != null) {
                        boolean isUsed = false;
                        for (PyElement e : unusedImport.iterateNames()) {
                            if (usedImportNames.contains(e.getName())) {
                                isUsed = true;
                            }
                        }
                        if (isUsed) {
                            continue;
                        }
                    }
                    PsiElement importedElement;
                    if (unusedImport instanceof PyImportElement) {
                        PyImportElement importElement = (PyImportElement) unusedImport;
                        PsiElement element = importElement.resolve();
                        if (element == null) {
                            if (importElement.getImportedQName() != null) {
                                //Mark import as unused even if it can't be resolved
                                if (areAllImportsUnused(importStatement, unusedImports)) {
                                    result.add(importStatement);
                                }
                                else {
                                    result.add(importElement);
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
                    if (packageQName != null && importedElement instanceof PsiFileSystemItem) {
                        QualifiedName importedQName =
                            QualifiedNameFinder.findShortestImportableQName((PsiFileSystemItem) importedElement);
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

        private static boolean areAllImportsUnused(PyImportStatementBase importStatement, Set<PyImportedNameDefiner> unusedImports) {
            PyImportElement[] elements = importStatement.getImportElements();
            for (PyImportElement element : elements) {
                if (!unusedImports.contains(element)) {
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

    /**
     * Checks if one or more extension points ask unused import to be skipped
     *
     * @param importNameDefiner unused import
     * @return true of one or more asks
     */
    private static boolean importShouldBeSkippedByExtPoint(@Nonnull PyImportedNameDefiner importNameDefiner) {
        for (PyUnresolvedReferenceSkipperExtPoint skipper : PyUnresolvedReferenceSkipperExtPoint.EP_NAME.getExtensions()) {
            if (skipper.unusedImportShouldBeSkipped(importNameDefiner)) {
                return true;
            }
        }
        return false;
    }
}
