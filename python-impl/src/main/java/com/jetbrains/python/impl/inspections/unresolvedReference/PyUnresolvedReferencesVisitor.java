// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.unresolvedReference;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.PyCustomType;
import com.jetbrains.python.impl.codeInsight.PyDunderMatchArgsReference;
import com.jetbrains.python.impl.codeInsight.PySubstitutionChunkReference;
import com.jetbrains.python.impl.codeInsight.controlflow.PyDataFlowKt;
import com.jetbrains.python.impl.codeInsight.controlflow.Reachability;
import com.jetbrains.python.impl.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.impl.documentation.PythonDocumentationProvider;
import com.jetbrains.python.impl.documentation.docstrings.DocStringTypeReference;
import com.jetbrains.python.impl.inspections.PyInspectionMessages;
import com.jetbrains.python.impl.inspections.PyInspectionVisitor;
import com.jetbrains.python.impl.inspections.quickfix.PyRenameUnresolvedRefQuickFix;
import com.jetbrains.python.impl.psi.PyKnownDecoratorUtil;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import com.jetbrains.python.impl.psi.impl.PyImportStatementNavigator;
import com.jetbrains.python.impl.psi.impl.references.PyFromImportNameReference;
import com.jetbrains.python.impl.psi.impl.references.PyImportReference;
import com.jetbrains.python.impl.psi.impl.references.PyOperatorReference;
import com.jetbrains.python.impl.psi.impl.references.hasattr.PyHasAttrHelper;
import com.jetbrains.python.impl.psi.types.PyCompositeType;
import com.jetbrains.python.impl.psi.types.PyFunctionTypeImpl;
import com.jetbrains.python.impl.psi.types.PyImportedModuleType;
import com.jetbrains.python.impl.psi.types.PyModuleType;
import com.jetbrains.python.impl.psi.types.PySelfType;
import com.jetbrains.python.impl.psi.types.PyStructuralType;
import com.jetbrains.python.impl.psi.types.PyTypeChecker;
import com.jetbrains.python.impl.psi.types.PyTypeUtil;
import com.jetbrains.python.impl.psi.types.PyTypeUtilKt;
import com.jetbrains.python.impl.psi.types.PyUnionType;
import com.jetbrains.python.inspections.PyInspectionExtension;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PsiReferenceEx;
import com.jetbrains.python.psi.PyAugAssignmentStatement;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyCallable;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyClassPattern;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyExceptPart;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFromImportStatement;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyIfStatement;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.PyImportStatementBase;
import com.jetbrains.python.psi.PyKeywordPattern;
import com.jetbrains.python.psi.PyPrefixExpression;
import com.jetbrains.python.psi.PyQualifiedElement;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyReferenceOwner;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.PyTryExceptStatement;
import com.jetbrains.python.psi.PyTryPart;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.PyClassMembersProvider;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeVarType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.access.RequiredReadAction;
import consulo.document.util.TextRange;
import consulo.language.ast.ASTNode;
import consulo.language.editor.annotation.HighlightSeverity;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiPolyVariantReference;
import consulo.language.psi.PsiReference;
import consulo.language.psi.ResolveResult;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.psi.util.QualifiedName;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.collection.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static com.jetbrains.python.PyNames.END_WILDCARD;

public class PyUnresolvedReferencesVisitor extends PyInspectionVisitor {
    private final boolean myStrictClassAttributes;
    private final boolean myStrictInstanceAttributes;
    private final PyUnresolvedReferenceQuickFixes myQuickFixes;
    private final Set<String> myIgnoredIdentifiers;

    private volatile @Nullable Boolean myIsEnabled = null;
    private final List<PyPackageInstallAllProblemInfo> myUnresolvedRefs = Collections.synchronizedList(new ArrayList<>());

    public PyUnresolvedReferencesVisitor(
        @Nullable ProblemsHolder holder,
        List<String> ignoredIdentifiers,
        TypeEvalContext context,
        LanguageLevel languageLevel,  // kept for binary compat with Java subclasses
        boolean strictClassAttributes,
        boolean strictInstanceAttributes,
        PyUnresolvedReferenceQuickFixes quickFixes
    ) {
        super(holder, context);
        myStrictClassAttributes = strictClassAttributes;
        myStrictInstanceAttributes = strictInstanceAttributes;
        myQuickFixes = quickFixes;
        myIgnoredIdentifiers = Set.copyOf(ignoredIdentifiers);
    }

    @Override
    @RequiredReadAction
    public void visitPyTargetExpression(PyTargetExpression node) {
        // Augmented assignments (e.g., `x += 1`) do have a target expression,
        // but for historical reasons it is not represented in the PSI, so delegate to the base visitor for general reference checks
        if (node.getParent() instanceof PyAugAssignmentStatement) {
            super.visitPyTargetExpression(node);
        }

        checkAttributeAssignment(node);
    }

    @RequiredReadAction
    private void checkAttributeAssignment(PyQualifiedExpression node) {
        PyExpression qualifier = node.getQualifier();
        if (qualifier == null) {
            return;
        }
        String attrName = node.getReferencedName();
        if (attrName == null) {
            return;
        }
        PyType type = replaceSelfWithItsScopeClass(myTypeEvalContext.getType(qualifier));
        if (type == null) {
            return;
        }
        ASTNode nameElement = node.getNameElement();
        PsiElement anchor = nameElement != null ? nameElement.getPsi() : node;

        if (type instanceof PyClassLikeType classLikeType && !classLikeType.isAttributeWritable(attrName, myTypeEvalContext)) {
            registerProblem(anchor,
                PyInspectionMessages.problemMessage(a -> PyLocalize.inspUnresolvedRefsClassObjectHasNoAttribute(a[0], a[1]),
                    classLikeType.getName(), attrName));
            return;
        }

        if (!(type instanceof PyClassType classType)) {
            return;
        }
        if (PyUtil.isObjectClass(classType.getPyClass())) {
            return;
        }

        boolean isDefinition = classType.isDefinition();
        boolean strictCheckEnabled = isDefinition ? myStrictClassAttributes : myStrictInstanceAttributes;
        if (!strictCheckEnabled) {
            return;
        }

        if (!ContainerUtil.isEmpty(classType.resolveMember(attrName, node instanceof PyExpression expression ? expression : null,
            AccessDirection.READ, getResolveContext()))) {
            return;
        }
        if (isDeclaredInSlots(classType, attrName)) {
            return;
        }
        // Instance-side: user-defined `__setattr__` accepts any name.
        if (!isDefinition && overridesSetAttr(classType.getPyClass())) {
            return;
        }
        PsiReference reference = node.getReference();
        if (reference != null && ignoreUnresolvedMemberForType(classType, reference, attrName)) {
            return;
        }

        registerProblem(anchor,
            PyInspectionMessages.problemMessage(a -> PyLocalize.inspUnresolvedRefsUnresolvedAttributeForClass(a[0], a[1]),
                attrName, classType.getName()),
            ProblemHighlightType.WARNING);
    }

    private boolean overridesSetAttr(PyClass cls) {
        PyFunction setAttr = cls.findMethodByName("__setattr__", true, myTypeEvalContext);
        return setAttr != null && !PyBuiltinCache.getInstance(cls).isBuiltin(setAttr);
    }

    @Override
    @RequiredReadAction
    public void visitPyElement(PyElement node) {
        super.visitPyElement(node);
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
        if (!isEnabled(node) || reference == null || reference.isSoft()) {
            return;
        }
        PyExceptPart guard = getImportErrorGuard(node);
        if (guard != null) {
            return;
        }
        if (node instanceof PyQualifiedExpression qualifiedExpression) {
            PyExpression qualifier = qualifiedExpression.getQualifier();
            String name = qualifiedExpression.getName();
            if (qualifier != null && name != null &&
                PyHasAttrHelper.INSTANCE.getNamesFromHasAttrs(node, qualifier).contains(name)) {
                return;
            }
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
                    if (isAwaitCallToImportedNonAsyncFunction(reference)) {
                        // special case: type of prefixExpression.getQualifier() is null but we want to check whether the called function is async
                        severity = HighlightSeverity.WEAK_WARNING;
                    }
                }
                if (severity == null) {
                    return;
                }
                registerUnresolvedReferenceProblem(node, reference, severity);
            }
        }
        else if (reference instanceof PyImportReference importReference &&
            target instanceof PsiFile targetFile && target == importReference.getElement().getContainingFile() &&
            !isReExportInPackageInit(node, targetFile)) {
            registerProblem(node, PyLocalize.inspUnresolvedRefsImportResolvesToItsContainingFile().get());
        }
        // If an attribute was resolved on some members of a union, we still need to report those where it doesn't exist
        else if (PyUnionType.isStrictSemanticsEnabled() && node instanceof PyQualifiedExpression qualifiedExpression) {
            String referencedName = qualifiedExpression.getReferencedName();
            if (referencedName == null) {
                return;
            }
            // operators on binary/augmented expressions are handled by PyTypeCheckerInspection.
            if (!(node instanceof PyAugAssignmentStatement) && !(node instanceof PyBinaryExpression)) {
                checkStrictUnionQualifierAttributeReference(qualifiedExpression, reference, referencedName, target);
            }
        }
    }

    private record StrictUnionMissingMember(PyType unionMemberType, String attributeName) {
    }

    @RequiredReadAction
    private void checkStrictUnionQualifierAttributeReference(
        PyQualifiedExpression node,
        PsiReference reference,
        String referencedName,
        @Nullable PsiElement target
    ) {
        PyExpression qualifier = reference instanceof PyOperatorReference operatorReference && target instanceof PyCallable callable
            ? operatorReference.getReceiver(callable)
            : node.getQualifier();
        PyType qualifierType = qualifier != null ? myTypeEvalContext.getType(qualifier) : null;
        if (!(qualifierType instanceof PyUnionType unionType)) {
            return;
        }
        PyType missing = findStrictUnionMemberMissingAttribute(unionType, reference, referencedName);
        if (missing == null) {
            return;
        }
        reportStrictUnionMissingMembers(node, reference, unionType,
            List.of(new StrictUnionMissingMember(missing, referencedName)));
    }

    @RequiredReadAction
    private void reportStrictUnionMissingMembers(
        PyQualifiedExpression node,
        PsiReference reference,
        PyUnionType containingUnion,
        List<StrictUnionMissingMember> missingMembers
    ) {
        String unionRender = PythonDocumentationProvider.getTypeName(containingUnion, myTypeEvalContext);

        for (StrictUnionMissingMember missingMember : new LinkedHashSet<>(missingMembers)) {
            String memberRender = PythonDocumentationProvider.getTypeName(missingMember.unionMemberType(), myTypeEvalContext);
            PyInspectionMessages.ProblemMessage message = PyInspectionMessages.problemMessage(
                a -> PyLocalize.inspUnresolvedRefsUnresolvedAttributeInUnionType(a[0], a[1], a[2]),
                memberRender,
                unionRender,
                missingMember.attributeName()
            );

            registerProblem(
                node,
                message,
                ProblemHighlightType.WEAK_WARNING,
                LocalQuickFix.EMPTY_ARRAY,
                reference.getRangeInElement()
            );
        }
    }

    @RequiredReadAction
    private boolean isAwaitCallToImportedNonAsyncFunction(PsiReference reference) {
        if (!(reference.getElement() instanceof PyPrefixExpression prefixExpression)) {
            return false;
        }
        if (!PyNames.DUNDER_AWAIT.equals(prefixExpression.getOperator().getSpecialMethodName())) {
            return false;
        }
        if (!(getReferenceQualifierOrImportSource(reference) instanceof PyCallExpression callExpression)) {
            return false;
        }
        List<PyCallable> callees = callExpression.multiResolveCalleeFunction(PyResolveContext.defaultContext(myTypeEvalContext));
        // no signature is declared async -> warning
        return !callees.isEmpty() && ContainerUtil.and(callees, it -> !(it instanceof PyFunction function && function.isAsync()));
    }

    @RequiredReadAction
    private void registerUnresolvedReferenceProblem(PyElement node, PsiReference initialReference, HighlightSeverity severity) {
        ProblemSpec spec = buildProblemSpec(node, initialReference, severity);
        if (spec == null) {
            return;
        }
        List<LocalQuickFix> fixes = new ArrayList<>();
        PsiReference reference = spec.reference();
        PsiElement element = spec.element();
        if (reference instanceof PsiReferenceEx referenceEx) {
            fixes.addAll(referenceEx.getQuickFixes(myTypeEvalContext));
        }
        if (element instanceof PyReferenceExpression referenceExpression && !referenceExpression.isQualified()) {
            ContainerUtil.addIfNotNull(fixes, PyUnresolvedReferenceQuickFixesKt.getTrueFalseQuickFix(spec.refText()));
            fixes.addAll(PyUnresolvedReferenceQuickFixesKt.getAddSelfFixes(myTypeEvalContext, spec.node(), referenceExpression));
            ContainerUtil.addIfNotNull(fixes, PyUnresolvedReferenceQuickFixesKt.getCreateFunctionQuickFix(referenceExpression));
            ContainerUtil.addIfNotNull(fixes, PyUnresolvedReferenceQuickFixesKt.getAddParameterQuickFix(spec.refName(), referenceExpression));
            fixes.add(new PyRenameUnresolvedRefQuickFix());
        }
        PyType qualifierType = spec.qualifierType();
        if (qualifierType != null) {
            fixes.addAll(PyUnresolvedReferenceQuickFixesKt.getCreateMemberFromUsageFixes(myTypeEvalContext, qualifierType, reference,
                spec.refText()));
        }
        if (spec.fallbackToUnqualifiedFix()) {
            fixes.addAll(myQuickFixes.getAutoImportFixes(spec.node(), reference, element));
            ContainerUtil.addIfNotNull(fixes, PyUnresolvedReferenceQuickFixesKt.getCreateClassFix(myTypeEvalContext, spec.refText(), element));
        }
        ProblemHighlightType hlType = computeHighlightType(spec.severity());
        fixes.addAll(myQuickFixes.getImportStatementQuickFixes(spec.element()));
        fixes.addAll(myQuickFixes.getAddIgnoredIdentifierQuickFixes(spec.qualifiedNames()));
        List<LocalQuickFix> installPackageQuickFixes = myQuickFixes.getInstallPackageQuickFixes(spec.node(), spec.reference(), spec.refName());
        boolean installAll = !installPackageQuickFixes.isEmpty();
        if (installAll) {
            fixes.addAll(installPackageQuickFixes);
            myUnresolvedRefs.add(new PyPackageInstallAllProblemInfo(spec.node(), spec.message(), hlType, spec.refName(), fixes));
        }
        ContainerUtil.addIfNotNull(fixes, myQuickFixes.getAddSourceRootQuickFix(spec.node()));
        // PySubstitutionChunkReference: install-all only, no direct registerProblem.
        if (spec.reference() instanceof PySubstitutionChunkReference) {
            return;
        }
        myQuickFixes.getPluginQuickFixes(fixes, spec.reference());
        if (!installAll) {
            registerProblem(spec.node(), spec.message(), hlType, fixes.toArray(LocalQuickFix.EMPTY_ARRAY), spec.rangeInElement());
        }
    }

    @RequiredReadAction
    private @Nullable ProblemSpec buildProblemSpec(PyElement initialNode, PsiReference reference, HighlightSeverity initialSeverity) {
        if (reference instanceof DocStringTypeReference) {
            return null;
        }

        PsiElement element = reference.getElement();
        TextRange rangeInElement = reference.getRangeInElement();
        String text = element.getText();
        String refText = rangeInElement.getStartOffset() >= 0 && rangeInElement.getEndOffset() > 0 ? rangeInElement.substring(text) : text;
        // Operator refs (e.g. `not x`) have null `referencedName`; do not fall back to refText, the empty-check below must fire.
        String refName = element instanceof PyQualifiedElement qualifiedElement ? qualifiedElement.getReferencedName() : refText;
        if (refName == null || refName.isEmpty()) {
            return null;
        }

        List<QualifiedName> qualifiedNames = PyUnresolvedReferenceCanonicalNamesHelperKt.getCanonicalNames(reference, myTypeEvalContext);
        if (isIgnoredIdentifier(qualifiedNames, refName)) {
            return null;
        }

        if (element instanceof PyKeywordPattern keywordPattern) {
            PyClassPattern classPattern = keywordPattern.getContainingClassPattern();
            if (classPattern != null) {
                PyClassType classType = myTypeEvalContext.getType(classPattern) instanceof PyClassType t ? t : null;
                if (classType != null && classType.getMemberNames(true, myTypeEvalContext).contains(refName)) {
                    return null;
                }
            }
        }

        PyElement node = initialNode;
        HighlightSeverity severity = initialSeverity;
        PyInspectionMessages.ProblemMessage message = null;
        TextRange resolvedRange = rangeInElement;

        if (element instanceof PyReferenceExpression referenceExpression) {
            if (PyNames.COMPARISON_OPERATORS.contains(refName)) {
                return null;
            }
            else if (!referenceExpression.isQualified() &&
                PyDataFlowKt.getReachabilityForInspection(referenceExpression, myTypeEvalContext) != Reachability.REACHABLE) {
                return null;
            }
            else if (PyModuleType.getPossibleInstanceMembers().contains(refName)) {
                return null;
            }
            else if (PsiTreeUtil.getParentOfType(PsiTreeUtil.getParentOfType(node, PyImportElement.class),
                PyTryExceptStatement.class, PyIfStatement.class) != null) {
                severity = HighlightSeverity.WEAK_WARNING;
                message = PyInspectionMessages.problemMessage(a -> PyLocalize.inspUnresolvedRefsModuleNotFound(a[0]), refText);
                // TODO: mark the node so that future references pointing to it won't result in a error, but in a warning
            }
        }

        if (reference instanceof PsiReferenceEx referenceEx && message == null) {
            String unresolvedDescription = referenceEx.getUnresolvedDescription();
            if (unresolvedDescription != null) {
                message = new PyInspectionMessages.ProblemMessage(unresolvedDescription, unresolvedDescription);
            }
        }

        PyType qualifierType = null;
        boolean fallbackToUnqualifiedFix = false;

        if (message == null) {
            if (element instanceof PyQualifiedExpression) {
                // TODO: Add __qualname__ for Python 3.3 to the skeleton of <class 'object'>, introduce a pseudo-class skeleton for
                // <class 'function'>
                if ("__qualname__".equals(refText) && !LanguageLevel.forElement(element).isPython2()) {
                    return null;
                }
                if (PyNames.COMPARISON_OPERATORS.contains(refName)) {
                    return null;
                }
            }

            boolean markedQualified = false;
            PyExpression qualifier = getReferenceQualifierOrImportSource(reference);
            if (qualifier != null) {
                PyType type = replaceSelfWithItsScopeClass(myTypeEvalContext.getType(qualifier));
                if (!PyTypeUtilKt.isUnknown(type)) {
                    if (ignoreUnresolvedMemberForType(type, reference, refName) || isDeclaredInSlots(type, refName)) {
                        return null;
                    }
                    qualifierType = type;
                    if (type instanceof PyClassLikeType classLikeType) {
                        if (reference instanceof PyOperatorReference operatorReference) {
                            String className = classLikeType.getName();
                            if (classLikeType.isDefinition()) {
                                PyClassLikeType metaClassType = classLikeType.getMetaClassType(myTypeEvalContext, true);
                                if (metaClassType != null && metaClassType.getName() != null) {
                                    className = metaClassType.getName();
                                }
                            }
                            message = PyInspectionMessages.problemMessage(
                                a -> PyLocalize.inspUnresolvedRefsClassDoesNotDefineOperator(a[0], a[1], a[2]),
                                className, refName, operatorReference.getReadableOperatorName());
                        }
                        else {
                            // TODO use proper type rendering here
                            message = PyInspectionMessages.problemMessage(
                                a -> PyLocalize.inspUnresolvedRefsUnresolvedAttributeForClass(a[0], a[1]),
                                refText, classLikeType.getName());
                        }
                    }
                    else {
                        PyType unionMemberWithoutAttr = findStrictUnionMemberMissingAttribute(type, reference, refName);
                        if (unionMemberWithoutAttr != null) {
                            String unionTypeRender = PythonDocumentationProvider.getTypeName(type, myTypeEvalContext);
                            String unionMemberRender = PythonDocumentationProvider.getTypeName(unionMemberWithoutAttr, myTypeEvalContext);
                            message = PyInspectionMessages.problemMessage(
                                a -> PyLocalize.inspUnresolvedRefsUnresolvedAttributeInUnionType(a[0], a[1], a[2]),
                                unionMemberRender, unionTypeRender, refName);
                            severity = HighlightSeverity.WEAK_WARNING;
                        }
                        else {
                            message = PyInspectionMessages.problemMessage(
                                a -> PyLocalize.inspUnresolvedRefsCannotFindReferenceInType(a[0], a[1]),
                                refText, type.getName());
                        }
                    }
                    markedQualified = true;
                }
                else {
                    if (isAwaitCallToImportedNonAsyncFunction(reference)) {
                        message = PyInspectionMessages.problemMessage(
                            a -> PyLocalize.inspAwaitCallOnImportedUntypedFunction(a[0]), qualifier.getText());
                        node = qualifier; // show warning on the function call
                        resolvedRange = TextRange.create(0, qualifier.getTextRange().getLength());
                        markedQualified = true;
                    }
                    else if (reference instanceof PyDunderMatchArgsReference) {
                        markedQualified = true;
                    }
                }
            }

            if (!markedQualified) {
                message = PyInspectionMessages.problemMessage(a -> PyLocalize.inspUnresolvedRefsUnresolvedReference(a[0]), refText);
                fallbackToUnqualifiedFix = true;
            }
        }

        if (message == null) {
            return null;
        }
        return new ProblemSpec(node, element, reference, resolvedRange, refName, refText,
            message, severity, qualifiedNames, qualifierType, fallbackToUnqualifiedFix);
    }

    private boolean isIgnoredIdentifier(List<QualifiedName> qualifiedNames, String refName) {
        for (QualifiedName name : qualifiedNames) {
            String canonicalName = name.toString();
            for (String ignored : myIgnoredIdentifiers) {
                if (ignored.endsWith(END_WILDCARD)) {
                    String prefix = ignored.substring(0, ignored.length() - END_WILDCARD.length());
                    if (canonicalName.startsWith(prefix)) {
                        return true;
                    }
                }
                else if (canonicalName.equals(ignored)) {
                    return true;
                }
            }
        }
        // Legacy non-qualified ignore patterns.
        return myIgnoredIdentifiers.contains(refName);
    }

    private static ProblemHighlightType computeHighlightType(HighlightSeverity severity) {
        if (severity == HighlightSeverity.WARNING) {
            return ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
        }
        else if (severity == HighlightSeverity.WEAK_WARNING) {
            return ProblemHighlightType.WEAK_WARNING;
        }
        else if (severity == HighlightSeverity.ERROR) {
            return ProblemHighlightType.GENERIC_ERROR;
        }
        else {
            return ProblemHighlightType.LIKE_UNKNOWN_SYMBOL;
        }
    }

    private boolean isDeclaredInSlots(PyType type, String attrName) {
        return PyTypeUtil.toStream(type)
            .select(PyClassType.class)
            .map(PyClassType::getPyClass)
            .flatMap(cls -> StreamEx.of(cls).append(cls.getAncestorClasses(myTypeEvalContext)))
            .nonNull()
            .filter(it -> it.isNewStyleClass(myTypeEvalContext))
            .flatCollection(PyClass::getOwnSlots)
            .anyMatch(it -> it.equals(attrName));
    }

    private boolean ignoreUnresolvedMemberForType(@Nullable PyType type, PsiReference reference, String name) {
        if (type instanceof PyTypeVarType typeVarType) {
            return isUnboundedTypeVar(typeVarType);
        }
        if (type instanceof PyUnionType unionType) {
            return isUnionMemberIgnored(unionType, reference, name);
        }
        if (type instanceof PyCompositeType compositeType) {
            return ContainerUtil.or(compositeType.getMembers(), it -> ignoreUnresolvedMemberForType(it, reference, name));
        }

        // Unknown / Any-ish types: do not emit anything, we have no information to be confident.
        if (PyTypeChecker.isUnknown(type, myTypeEvalContext)) {
            return true;
        }

        if (type instanceof PyStructuralType structuralType && structuralType.isInferredFromUsages()) {
            return true;
        }
        if (type instanceof PyImportedModuleType importedModuleType && importedModuleType.getImportedModule().resolve() == null) {
            return true;
        }
        if (type instanceof PyCustomType customType && hasUnclearClassParent(customType)) {
            return true;
        }
        if (type instanceof PyClassType classType && isDynamicClass(classType, reference, name)) {
            return true;
        }
        if (type instanceof PyFunctionTypeImpl functionType && hasUnknownAttrsDecorator(functionType)) {
            return true;
        }

        if (type instanceof PyModuleType moduleType &&
            moduleType.getModule().getLanguageLevel().isAtLeast(LanguageLevel.PYTHON37)) {
            return PyTypeChecker.definesGetAttr(moduleType.getModule(), myTypeEvalContext);
        }

        return isIgnoredByExtension(type, name);
    }

    private static boolean isUnboundedTypeVar(PyTypeVarType type) {
        return PyTypeUtilKt.isAnyOrUnknown(type.getBound()) && type.getDefaultType() == null && type.getConstraints().isEmpty();
    }

    /**
     * Under strict-union semantics a union is ignored only when every member resolves the attribute
     * — otherwise (e.g. `x: int | Any`) the missing-on-one-arm case is still reported. Under permissive
     * semantics, ignoring any one member is enough.
     */
    private boolean isUnionMemberIgnored(PyUnionType type, PsiReference reference, String name) {
        if (PyUnionType.isStrictSemanticsEnabled()) {
            // There is no union member for which the unresolved attribute shouldn't be ignored (e.g. `Any` in `int | Any`)
            return findStrictUnionMemberMissingAttribute(type, reference, name) == null;
        }
        return ContainerUtil.or(type.getMembers(), it -> ignoreUnresolvedMemberForType(it, reference, name));
    }

    /**
     * Custom type that mimics a class whose ancestors did not all resolve — too uncertain to flag.
     */
    private boolean hasUnclearClassParent(PyCustomType type) {
        return ContainerUtil.or(type.getTypesToMimic(),
            it -> it instanceof PyClassType classType && PyUtil.hasUnresolvedAncestors(classType.getPyClass(), myTypeEvalContext));
    }

    /**
     * Class participates in some dynamic-attribute mechanism (`__getattr__`, property, `@DynamicAttrs`, etc.).
     */
    private boolean isDynamicClass(PyClassType type, PsiReference reference, String name) {
        PyClass cls = type.getPyClass();
        return PyTypeChecker.overridesGetAttr(type, myTypeEvalContext, name) ||
            cls.findProperty(name, true, myTypeEvalContext) != null ||
            PyUtil.hasUnresolvedAncestors(cls, myTypeEvalContext) ||
            isDecoratedAsDynamic(cls) ||
            hasUnresolvedDynamicMember(type, reference, name, myTypeEvalContext) ||
            isAwaitOnGeneratorBasedCoroutine(name, reference, cls);
    }

    private boolean hasUnknownAttrsDecorator(PyFunctionTypeImpl type) {
        PyCallable callable = type.getCallable();
        return callable instanceof PyFunction function && PyKnownDecoratorUtil.hasUnknownOrUpdatingAttributesDecorator(function, myTypeEvalContext);
    }

    private boolean isIgnoredByExtension(@Nullable PyType type, String name) {
        return PyInspectionExtension.EP_NAME.findFirstSafe(it -> it.ignoreUnresolvedMember(type, name, myTypeEvalContext)) != null;
    }

    private @Nullable PyType findStrictUnionMemberMissingAttribute(@Nullable PyType type, PsiReference ref, String name) {
        if (!(type instanceof PyUnionType unionType) || !PyUnionType.isStrictSemanticsEnabled()) {
            return null;
        }
        // In cases like the following (see PyUnusedImportTest#testModuleAndSubmodule):
        //
        // import pkg.mod
        // import pkg
        // pkg.mod
        //     ^
        //
        // The type of `pkg` is a union of PyModuleType('pkg/__init__.py') and PyImportedModuleType('import pkg').
        // Only the last one owns the attribute `mod` directly, and the first needs a location to inspect imports
        // of this module in the file the original reference belongs to.
        PyExpression location = ref.getElement() instanceof PyExpression expression ? expression : null;
        for (PyType t : unionType.getMembers()) {
            if (t != null && !ignoreUnresolvedMemberForType(t, ref, name)) {
                List<? extends RatedResolveResult> results = t.resolveMember(name, location, AccessDirection.READ, getResolveContext());
                if (results == null || results.isEmpty()) {
                    return t;
                }
            }
        }
        return null;
    }

    private boolean isDecoratedAsDynamic(PyClass cls) {
        if (hasDynamicAttrsDocstring(cls)) {
            return true;
        }
        return ContainerUtil.or(cls.getAncestorClasses(myTypeEvalContext), it -> it != null && hasDynamicAttrsDocstring(it));
    }

    private static boolean hasDynamicAttrsDocstring(PyClass cls) {
        String docStringValue = cls.getDocStringValue();
        return docStringValue != null && docStringValue.contains("@DynamicAttrs");
    }

    private boolean isAwaitOnGeneratorBasedCoroutine(String name, PsiReference reference, PyClass cls) {
        if (PyNames.DUNDER_AWAIT.equals(name) &&
            reference instanceof PyOperatorReference operatorReference &&
            PyTypingTypeProvider.GENERATOR.equals(cls.getQualifiedName())) {
            PyExpression receiver = operatorReference.getReceiver(null);

            if (receiver instanceof PyCallExpression callExpression) {
                return PyKnownDecoratorUtil.isResolvedToGeneratorBasedCoroutine(callExpression, getResolveContext(), myTypeEvalContext);
            }
        }

        return false;
    }

    private boolean isEnabled(PsiElement anchor) {
        Boolean enabled = myIsEnabled;
        if (enabled == null) {
            Boolean overridden = overriddenUnresolvedReferenceInspection(anchor.getContainingFile());
            enabled = overridden != null ? overridden : true;
            myIsEnabled = enabled;
        }
        return enabled;
    }

    @RequiredReadAction
    public void addInstallAllImports() {
        Set<String> refNames = new HashSet<>();
        List<PyPackageInstallAllProblemInfo> unresolvedRefs;
        synchronized (myUnresolvedRefs) {
            unresolvedRefs = new ArrayList<>(myUnresolvedRefs);
        }
        for (PyPackageInstallAllProblemInfo info : unresolvedRefs) {
            refNames.add(info.refName());
        }
        int refNamesCount = refNames.size();
        LocalQuickFix installAllPackageQuickFixes = myQuickFixes.getInstallAllPackagesQuickFix(unresolvedRefs);
        for (PyPackageInstallAllProblemInfo info : unresolvedRefs) {
            List<LocalQuickFix> quickFixes = new ArrayList<>(info.fixes());
            if (refNamesCount > 1) {
                ContainerUtil.addIfNotNull(quickFixes, installAllPackageQuickFixes);
            }
            registerProblem(info.psiElement(), info.message(), info.highlightType(), quickFixes.toArray(LocalQuickFix.EMPTY_ARRAY));
        }
    }

    private boolean ignoreUnresolved(PyElement node, PsiReference reference) {
        return PyInspectionExtension.EP_NAME.findFirstSafe(it -> it.ignoreUnresolvedReference(node, reference, myTypeEvalContext)) != null;
    }

    private record ProblemSpec(
        PyElement node,
        PsiElement element,
        PsiReference reference,
        TextRange rangeInElement,
        String refName,
        String refText,
        PyInspectionMessages.ProblemMessage message,
        HighlightSeverity severity,
        List<QualifiedName> qualifiedNames,
        @Nullable PyType qualifierType,
        boolean fallbackToUnqualifiedFix
    ) {
    }

    private static @Nullable PyType replaceSelfWithItsScopeClass(@Nullable PyType type) {
        return type instanceof PySelfType selfType ? selfType.getScopeClassType() : type;
    }

    private static @Nullable Boolean overriddenUnresolvedReferenceInspection(PsiFile file) {
        return PyInspectionExtension.EP_NAME.computeSafeIfAny(it -> it.overrideUnresolvedReferenceInspection(file));
    }

    private static @Nullable PyExceptPart getImportErrorGuard(@Nullable PyElement node) {
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

    /**
     * Self-imports inside `__init__.py` are a deliberate re-export pattern — not a "resolves to itself" warning.
     */
    private static boolean isReExportInPackageInit(@Nullable PyElement node, PsiFile target) {
        return PyImportStatementNavigator.getImportStatementByElement(node) == null && PyNames.INIT_DOT_PY.equals(target.getName());
    }

    @RequiredReadAction
    private static @Nullable PyExpression getReferenceQualifierOrImportSource(PsiReference reference) {
        PsiElement element = reference.getElement();
        if (element instanceof PyQualifiedElement qualifiedElement) {
            PyExpression qualifier = qualifiedElement.getQualifier();
            if (qualifier != null) {
                return qualifier;
            }
        }
        if (reference instanceof PyFromImportNameReference) {
            PyFromImportStatement fromImportStatement = PsiTreeUtil.getParentOfType(element, PyFromImportStatement.class);
            return fromImportStatement != null ? fromImportStatement.getImportSource() : null;
        }
        return null;
    }

    private static boolean hasUnresolvedDynamicMember(
        PyClassType type,
        PsiReference reference,
        String name,
        TypeEvalContext typeEvalContext
    ) {
        List<PyClassType> types = new ArrayList<>();
        types.add(type);
        for (PyClassLikeType ancestorType : type.getAncestorTypes(typeEvalContext)) {
            if (ancestorType instanceof PyClassType classType) {
                types.add(classType);
            }
        }

        return ContainerUtil.or(types, typeToCheck ->
            PyClassMembersProvider.EP_NAME.findFirstSafe(provider ->
                ContainerUtil.or(provider.getMembers(typeToCheck, reference.getElement(), typeEvalContext),
                    it -> name.equals(it.getName()))
            ) != null);
    }
}
