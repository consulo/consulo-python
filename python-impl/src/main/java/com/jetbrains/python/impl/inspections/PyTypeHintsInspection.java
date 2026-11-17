// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.ast.PyAstFunction;
import com.jetbrains.python.ast.PyAstTypeParameter;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.impl.codeInsight.controlflow.ReadWriteInstruction;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.impl.codeInsight.functionTypeComments.psi.PyFunctionTypeAnnotation;
import com.jetbrains.python.impl.codeInsight.functionTypeComments.PyFunctionTypeAnnotationDialect;
import com.jetbrains.python.impl.codeInsight.imports.AddImportHelper;
import com.jetbrains.python.impl.codeInsight.typeHints.PyTypeHintFile;
import com.jetbrains.python.impl.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.impl.documentation.PythonDocumentationProvider;
import com.jetbrains.python.impl.inspections.quickfix.PyUnpackTypeVarTupleQuickFix;
import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import com.jetbrains.python.impl.psi.impl.PyEvaluator;
import com.jetbrains.python.impl.psi.impl.PyTargetExpressionImpl;
import com.jetbrains.python.impl.psi.impl.stubs.PyTypingAliasStubType;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.resolve.PyResolveUtil;
import com.jetbrains.python.impl.psi.types.PyConcatenateType;
import com.jetbrains.python.impl.psi.types.PyLiteralType;
import com.jetbrains.python.impl.psi.types.PyNarrowedType;
import com.jetbrains.python.impl.psi.types.PyParamSpecType;
import com.jetbrains.python.impl.psi.types.PySelfType;
import com.jetbrains.python.impl.psi.types.PyTupleType;
import com.jetbrains.python.impl.psi.types.PyTypeChecker;
import com.jetbrains.python.impl.psi.types.PyTypedDictType;
import com.jetbrains.python.impl.psi.types.PyTypeParameterMapping;
import com.jetbrains.python.impl.psi.types.PyTypeUtil;
import com.jetbrains.python.impl.psi.types.PyTypeUtilKt;
import com.jetbrains.python.impl.psi.types.PyTypingNewType;
import com.jetbrains.python.impl.psi.types.PyUnionType;
import com.jetbrains.python.impl.pyi.PyiUtil;
import com.jetbrains.python.impl.sdk.PythonSdkUtil;
import com.jetbrains.python.psi.FutureFeature;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyAnnotation;
import com.jetbrains.python.psi.PyAnnotationOwner;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyComprehensionElement;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyEllipsisLiteralExpression;
import com.jetbrains.python.psi.PyExceptPart;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyExpressionStatement;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyForPart;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyKeywordArgument;
import com.jetbrains.python.psi.PyListLiteralExpression;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyNoneLiteralExpression;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.PyParenthesizedExpression;
import com.jetbrains.python.psi.PyPlainStringElement;
import com.jetbrains.python.psi.PyQualifiedNameOwner;
import com.jetbrains.python.psi.PyRecursiveElementVisitor;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyStarExpression;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PySubscriptionExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.PyTupleExpression;
import com.jetbrains.python.psi.PyTypeAliasStatement;
import com.jetbrains.python.psi.PyTypeCommentOwner;
import com.jetbrains.python.psi.PyTypeDeclarationStatement;
import com.jetbrains.python.psi.PyTypedElement;
import com.jetbrains.python.psi.PyTypeParameter;
import com.jetbrains.python.psi.PyTypeParameterList;
import com.jetbrains.python.psi.PyTypeParameterListOwner;
import com.jetbrains.python.psi.PyWithAncestors;
import com.jetbrains.python.psi.PyWithItem;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.stubs.PyTargetExpressionStub;
import com.jetbrains.python.psi.types.PyAnyType;
import com.jetbrains.python.psi.types.PyCallableParameter;
import com.jetbrains.python.psi.types.PyCallableParameterVariadicType;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyInstantiableType;
import com.jetbrains.python.psi.types.PyPositionalVariadicType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeParameterType;
import com.jetbrains.python.psi.types.PyTypeVarTupleType;
import com.jetbrains.python.psi.types.PyTypeVarType;
import com.jetbrains.python.psi.types.PyUnpackedTupleType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.document.util.TextRange;
import consulo.language.controlFlow.ControlFlowUtil;
import consulo.language.controlFlow.Instruction;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.Language;
import consulo.language.psi.FileContextUtil;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiFileFactory;
import consulo.language.psi.PsiFileSystemItem;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.psi.util.QualifiedName;
import consulo.language.util.ModuleUtilCore;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.Couple;
import consulo.util.lang.Pair;
import consulo.util.lang.ref.Ref;
import consulo.util.lang.ref.SimpleReference;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

@ExtensionImpl
public class PyTypeHintsInspection extends PyInspection {
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameTypeHints();
    }

    @Override
    public LocalizeValue getGroupDisplayName() {
        return PyLocalize.inspGroupPythonTypeHinting();
    }

    @Override
    public HighlightDisplayLevel getDefaultLevel() {
        return HighlightDisplayLevel.WARNING;
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

    // JB: Ref.deref
    private static <T> @Nullable T deref(@Nullable Ref<T> ref) {
        return ref != null ? ref.get() : null;
    }

    private static class Visitor extends PyInspectionVisitor {
        private final QualifiedName genericQName = QualifiedName.fromDottedString(PyTypingTypeProvider.GENERIC);
        private final QualifiedName protocolQName = QualifiedName.fromDottedString(PyTypingTypeProvider.PROTOCOL);
        private final QualifiedName protocolExtQName = QualifiedName.fromDottedString(PyTypingTypeProvider.PROTOCOL_EXT);

        Visitor(ProblemsHolder holder, TypeEvalContext context) {
            super(holder, context);
        }

        @Override
        @RequiredReadAction
        public void visitPyCallExpression(PyCallExpression node) {
            super.visitPyCallExpression(node);

            PyReferenceExpression callee = node.getCallee() instanceof PyReferenceExpression referenceExpression ? referenceExpression : null;
            List<QualifiedName> calleeQName = callee != null ? PyResolveUtil.resolveImportedElementQNameLocally(callee) : List.of();

            if (calleeQName.contains(QualifiedName.fromDottedString(PyTypingTypeProvider.TYPE_VAR)) ||
                calleeQName.contains(QualifiedName.fromDottedString(PyTypingTypeProvider.TYPE_VAR_EXT))) {
                PyExpression target = getTargetFromAssignment(node);

                checkTypeVarPlacement(node, target);
                checkTypeVarArguments(node, target);
                checkTypeVarRedefinition(target);
            }

            if (calleeQName.contains(QualifiedName.fromDottedString(PyTypingTypeProvider.PARAM_SPEC))) {
                checkParamSpecArguments(node, getTargetFromAssignment(node));
            }

            if (calleeQName.contains(QualifiedName.fromDottedString(PyTypingTypeProvider.TYPE_VAR_TUPLE)) ||
                calleeQName.contains(QualifiedName.fromDottedString(PyTypingTypeProvider.TYPE_VAR_TUPLE_EXT))) {
                checkTypeVarTupleArguments(node, getTargetFromAssignment(node));
            }

            if (calleeQName.contains(QualifiedName.fromDottedString(PyTypingTypeProvider.TYPE_ALIAS_TYPE)) ||
                calleeQName.contains(QualifiedName.fromDottedString(PyTypingTypeProvider.TYPE_ALIAS_TYPE_EXT))) {
                checkTypeAliasTypeArguments(node, getTargetFromAssignment(node));
            }

            if (calleeQName.contains(QualifiedName.fromDottedString(PyTypingTypeProvider.CAST)) ||
                calleeQName.contains(QualifiedName.fromDottedString(PyTypingTypeProvider.CAST_EXT))) {
                checkCastArguments(node.getArguments());
            }

            checkInstanceAndClassChecks(node);

            if (PyTypingTypeProvider.isInsideTypeHint(node, myTypeEvalContext) &&
                !isInsideTypingAnnotatedMetadata(node)) {
                checkParenthesesOnGenerics(node);
            }
        }

        private @Nullable PyExpression getTargetFromAssignment(PyCallExpression node) {
            if (!(node.getParent() instanceof PyAssignmentStatement assignmentStatement)) {
                return null;
            }
            for (Pair<PyExpression, PyExpression> pair : assignmentStatement.getTargetsToValuesMapping()) {
                if (pair.getSecond() == node) {
                    return pair.getFirst();
                }
            }
            return null;
        }

        @Override
        @RequiredReadAction
        public void visitPyClass(PyClass node) {
            super.visitPyClass(node);

            List<PyExpression> superClassExpressions = Arrays.asList(node.getSuperClassExpressions());

            checkPlainGenericInheritance(superClassExpressions);
            checkGenericDuplication(superClassExpressions);
            checkGenericCompleteness(node);
            checkInconsistentGenericBaseSubstitutions(node);
            reportTypeParametersUsedByOuterScope(node);
            checkMetaClass(node.getMetaClassExpression());
        }

        @Override
        @RequiredReadAction
        public void visitPySubscriptionExpression(PySubscriptionExpression node) {
            super.visitPySubscriptionExpression(node);

            if (!isInsideTypingAnnotatedMetadata(node)) {
                checkParameters(node);
            }
            checkParameterizedBuiltins(node);
        }

        @Override
        @RequiredReadAction
        public void visitPyTypeAliasStatement(PyTypeAliasStatement node) {
            super.visitPyTypeAliasStatement(node);

            reportTypeParametersUsedByOuterScope(node);

            PyExpression typeExpression = node.getTypeExpression() != null ? PyPsiUtils.flattenParens(node.getTypeExpression()) : null;
            if (typeExpression == null) {
                return;
            }
            if (!Helper.isValidTypeHint(typeExpression, myTypeEvalContext) || isAliasOfItself(node)) {
                registerProblem(typeExpression, PyLocalize.inspTypeHintsTypeHintIsNotValid().get());
            }

            if (typeExpression instanceof PyBinaryExpression binaryExpression && binaryExpression.getOperator() == PyTokenTypes.OR) {
                // Check each member of the union to see if it references the type alias itself
                String typeAliasName = node.getName();
                checkForCircularReference(typeExpression, typeAliasName);
            }

            ScopeOwner scopeOwner = ScopeUtil.getScopeOwner(node);
            if (scopeOwner != null && !(scopeOwner instanceof PyClass || scopeOwner instanceof PyFile)) {
                registerProblem(node, PyLocalize.inspTypeHintsTypeStatementIncorrectScope().get());
            }
        }

        // JB: local extension function PyExpression.checkForCircularReference() in visitPyTypeAliasStatement
        @RequiredReadAction
        private void checkForCircularReference(PyExpression expression, @Nullable String typeAliasName) {
            PyExpression it = PyPsiUtils.flattenParens(expression);
            if (it instanceof PyBinaryExpression binaryExpression) {
                if (binaryExpression.getLeftExpression() != null) {
                    checkForCircularReference(binaryExpression.getLeftExpression(), typeAliasName);
                }
                if (binaryExpression.getRightExpression() != null) {
                    checkForCircularReference(binaryExpression.getRightExpression(), typeAliasName);
                }
            }
            else if (it instanceof PyParenthesizedExpression parenthesizedExpression) {
                if (parenthesizedExpression.getContainedExpression() != null) {
                    checkForCircularReference(parenthesizedExpression.getContainedExpression(), typeAliasName);
                }
            }
            else if (it instanceof PySubscriptionExpression subscriptionExpression) {
                if (subscriptionExpression.getQualifier() != null) {
                    checkForCircularReference(subscriptionExpression.getQualifier(), typeAliasName);
                }
            }
            else if (it instanceof PyReferenceExpression referenceExpression) {
                if (!referenceExpression.isQualified() && Objects.equals(referenceExpression.getName(), typeAliasName)) {
                    registerProblem(referenceExpression, PyLocalize.inspTypeHintsCircularReference().get());
                }
            }
        }

        @Override
        @RequiredReadAction
        public void visitPyTypeParameter(PyTypeParameter typeParameter) {
            PyExpression boundExpression = typeParameter.getBoundExpression();
            if (boundExpression != null) {
                PyExpression bound = Objects.requireNonNull(PyPsiUtils.flattenParens(boundExpression));
                switch (typeParameter.getKind()) {
                    case TypeVar -> {
                        if (bound instanceof PyTupleExpression tupleExpression) {
                            for (PyExpression element : tupleExpression.getElements()) {
                                checkAnnotation(element);
                            }
                        }
                        else {
                            checkAnnotation(bound);
                        }
                    }
                    case TypeVarTuple -> checkAnnotation(bound);
                    case ParamSpec -> checkParamSpecDefaultValue(bound);
                }
            }
            PyExpression defaultExpression = typeParameter.getDefaultExpression();
            if (defaultExpression == null) {
                return;
            }
            switch (typeParameter.getKind()) {
                case TypeVar -> {
                    if (!(PyTypingTypeProvider.getTypeParameterTypeFromTypeParameter(typeParameter, myTypeEvalContext)
                        instanceof PyTypeVarType typeVarType)) {
                        return;
                    }
                    checkTypeVarDefaultType(defaultExpression, typeVarType);
                }
                case ParamSpec -> checkParamSpecDefaultValue(defaultExpression);
                case TypeVarTuple -> checkTypeVarTupleDefaultValue(defaultExpression, typeParameter);
            }
        }

        @RequiredReadAction
        private void checkParameterizedBuiltins(PySubscriptionExpression node) {
            if (LanguageLevel.forElement(node).isAtLeast(LanguageLevel.PYTHON39)) {
                return;
            }

            PyExpression qualifier = node.getQualifier();
            if (qualifier instanceof PyReferenceExpression referenceExpression) {
                boolean hasImportFromFuture = node.getContainingFile() instanceof PyFile pyFile && pyFile.hasImportFromFuture(FutureFeature.ANNOTATIONS);
                String name = referenceExpression.getName();
                if (PyBuiltinCache.isInBuiltins(referenceExpression) && name != null &&
                    PyTypingTypeProvider.TYPING_BUILTINS_GENERIC_ALIASES.containsKey(name) &&
                    !hasImportFromFuture) {
                    registerProblem(node,
                        PyInspectionMessages.problemMessage(a -> PyLocalize.inspTypeHintsBuiltinCannotBeParameterizedDirectly(a[0]), name),
                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING, new ReplaceWithTypingGenericAliasQuickFix());
                }
            }
        }

        @RequiredReadAction
        private void checkTypeVarTupleUnpacked(PyReferenceExpression node) {
            if (PsiTreeUtil.getParentOfType(node, PyStarExpression.class) != null) {
                return;
            }
            PySubscriptionExpression subscription = PsiTreeUtil.getParentOfType(node, PySubscriptionExpression.class);
            PyReferenceExpression unpackOperand =
                subscription != null && subscription.getOperand() instanceof PyReferenceExpression referenceExpression ? referenceExpression : null;
            if (unpackOperand != null) {
                QualifiedName operandQName = unpackOperand.asQualifiedName();
                if ((operandQName != null && operandQName.endsWith("Unpack")) ||
                    ContainerUtil.or(PyTypingTypeProvider.resolveToQualifiedNames(unpackOperand, myTypeEvalContext),
                        it -> PyTypingTypeProvider.UNPACK.equals(it) || PyTypingTypeProvider.UNPACK_EXT.equals(it))) {
                    return;
                }
            }
            registerProblem(node, PyLocalize.inspTypeHintsTypeVarTupleMustAlwaysBeUnpacked().get(),
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING, null, new PyUnpackTypeVarTupleQuickFix());
        }

        @Override
        @RequiredReadAction
        public void visitPyReferenceExpression(PyReferenceExpression node) {
            super.visitPyReferenceExpression(node);

            boolean insideTypeHint = PyTypingTypeProvider.isInsideTypeHint(node, myTypeEvalContext);
            if (insideTypeHint || isGenericTypeArgument(node)) {
                PyType type = deref(PyTypingTypeProvider.getType(node, myTypeEvalContext));
                if (insideTypeHint && type instanceof PyPositionalVariadicType) {
                    checkTypeVarTupleUnpacked(node);
                }
                if (insideTypeHint && type instanceof PyParamSpecType) {
                    checkParamSpecPlacement(node);
                }
                if (type instanceof PyTypeParameterType typeParameterType && typeParameterType.getScopeOwner() == null &&
                    !isInsideTypeParameterDefault(node)) {
                    registerProblem(node, PyLocalize.inspTypeHintsUnboundTypeVariable().get(),
                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
                }
                checkSelfType(node);
            }

            if (!insideTypeHint) {
                return;
            }

            checkTypingGenericAsTypeExpression(node);

            if (PyNames.CANONICAL_SELF.equals(node.getReferencedName())) {
                PyType refType = myTypeEvalContext.getType(node);
                PyType nameType = refType instanceof PySelfType selfType ? selfType.getScopeClassType() : refType;
                String typeName = nameType != null ? nameType.getName() : null;
                if (typeName != null && !typeName.equals(PyNames.CANONICAL_SELF)) {
                    registerProblem(node, PyLocalize.inspTypeHintsInvalidTypeSelf().get(), ProblemHighlightType.GENERIC_ERROR, null,
                        new ReplaceWithTypeNameQuickFix(typeName));
                }
            }


            boolean isTopLevelTypeHint = node.getParent() instanceof PyAnnotation ||
                node.getParent() instanceof PyExpressionStatement && node.getParent().getParent() instanceof PyTypeHintFile;
            if (isTopLevelTypeHint) {
                if (resolvesToAnyOfQualifiedNames(node, PyTypingTypeProvider.LITERAL, PyTypingTypeProvider.LITERAL_EXT)) {
                    registerProblem(node, PyLocalize.inspTypeHintsLiteralMustHaveAtLeastOneParameter().get());
                }
                if (resolvesToAnyOfQualifiedNames(node, PyTypingTypeProvider.ANNOTATED, PyTypingTypeProvider.ANNOTATED_EXT)) {
                    registerProblem(node, PyLocalize.inspTypeHintsAnnotatedMustBeCalledWithAtLeastTwoArguments().get());
                }
            }
            else if (resolvesToAnyOfQualifiedNames(node, PyTypingTypeProvider.TYPE_ALIAS, PyTypingTypeProvider.TYPE_ALIAS_EXT)) {
                registerProblem(node, PyLocalize.inspTypeHintsTypeAliasMustBeUsedAsStandaloneTypeHint().get());
            }
        }

        @RequiredReadAction
        private void checkSelfType(PyReferenceExpression node) {
            if (resolvesToAnyOfQualifiedNames(node, PyTypingTypeProvider.SELF, PyTypingTypeProvider.SELF_EXT)) {
                PySelfType selfType = deref(PyTypingTypeProvider.getType(node, myTypeEvalContext)) instanceof PySelfType t ? t : null;
                if (selfType == null) { // we don't infer Self type outside a class
                    registerProblem(node, PyLocalize.inspTypeHintsSelfUseOutsideClass().get(),
                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
                    return;
                }
                else {
                    PyArgumentList argList = PsiTreeUtil.getParentOfType(node, PyArgumentList.class);
                    if (argList != null && argList.getParent() instanceof PyClass) {
                        registerProblem(node, PyLocalize.inspTypeHintsSelfCannotUseSelfInThisContext().get(),
                            ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
                        return;
                    }
                    PyClassType typeType = PyBuiltinCache.getInstance(node).getTypeType();
                    if (selfType.getScopeClassType().getAncestorTypes(myTypeEvalContext)
                        .contains(typeType != null ? typeType.toClass() : null)) {
                        registerProblem(node, PyLocalize.inspTypeHintsSelfCannotUseSelfInMetaclass().get(),
                            ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
                    }
                }
            }
        }

        @RequiredReadAction
        private void checkTypingGenericAsTypeExpression(PyReferenceExpression node) {
            if (!resolvesToAnyOfQualifiedNames(node, PyTypingTypeProvider.GENERIC)) {
                return;
            }
            if (isClassSupertypeReference(node)) {
                return;
            }
            registerProblem(node,
                PyLocalize.inspTypeHintsGenericCannotBeUsedAsATypeExpression().get(),
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
        }

        private boolean isClassSupertypeReference(PyReferenceExpression node) {
            PyClass pyClass = PsiTreeUtil.getParentOfType(node, PyClass.class, false, PyStatement.class);
            if (pyClass == null) {
                return false;
            }
            for (PyExpression it : pyClass.getSuperClassExpressions()) {
                if (!(it instanceof PyKeywordArgument) && PsiTreeUtil.isAncestor(it, node, false)) {
                    return true;
                }
            }
            return false;
        }

        @RequiredReadAction
        private boolean isGenericTypeArgument(PyReferenceExpression node) {
            PySubscriptionExpression subscription = PsiTreeUtil.getParentOfType(node, PySubscriptionExpression.class);
            if (subscription == null) {
                return false;
            }
            PyExpression flatIndexExpr = PyPsiUtils.flattenParens(subscription.getIndexExpression());
            if (flatIndexExpr == null) {
                return false;
            }

            if (flatIndexExpr instanceof PyTupleExpression) {
                if (node.getParent() != flatIndexExpr) {
                    return false;
                }
            }
            else {
                if (node != flatIndexExpr) {
                    return false;
                }
            }

            PyType operandType = myTypeEvalContext.getType(subscription.getOperand());
            return operandType instanceof PyClassType classType && PyTypingTypeProvider.isGeneric(classType, myTypeEvalContext);
        }

        @RequiredReadAction
        private boolean isInsideTypeParameterDefault(PyReferenceExpression node) {
            PyKeywordArgument keywordArgument = PsiTreeUtil.getParentOfType(node, PyKeywordArgument.class, true, PyStatement.class);
            if (keywordArgument != null) {
                if ("default".equals(keywordArgument.getName()) &&
                    PsiTreeUtil.isAncestor(keywordArgument.getValueExpression(), node, false)) {
                    PsiElement argParent = keywordArgument.getParent();
                    PsiElement callExpr = argParent != null ? argParent.getParent() : null;
                    if (callExpr instanceof PyCallExpression callExpression) {
                        return PyTypingTypeProvider.getTypeParameterKindFromDeclaration(callExpression, myTypeEvalContext) != null;
                    }
                }
                return false;
            }
            PyTypeParameter typeParameter = PsiTreeUtil.getParentOfType(node, PyTypeParameter.class, true, PyStatement.class);
            if (typeParameter != null) {
                return PsiTreeUtil.isAncestor(typeParameter.getDefaultExpression(), node, false);
            }
            return false;
        }

        @Override
        @RequiredReadAction
        public void visitPyFile(PyFile node) {
            super.visitPyFile(node);

            if (node instanceof PyTypeHintFile && PyTypingTypeProvider.isInsideTypeHint(node, myTypeEvalContext)) {
                PsiElement[] children = node.getChildren();
                PsiElement it = children.length == 1 ? children[0] : null;
                if (it instanceof PyExpressionStatement expressionStatement) {
                    checkTupleMatching(expressionStatement.getExpression());
                }
            }
        }

        @Override
        @RequiredReadAction
        public void visitPyElement(PyElement node) {
            super.visitPyElement(node);

            if (!(node instanceof PyTypeCommentOwner typeCommentOwner) || !(node instanceof PyAnnotationOwner annotationOwner)) {
                return;
            }
            PsiComment typeComment = typeCommentOwner.getTypeComment();
            String typeCommentText = typeComment != null ? typeComment.getText() : null;
            if (typeCommentText == null || PyTypingTypeProvider.TYPE_IGNORE_PATTERN.matcher(typeCommentText).matches()) {
                return;
            }

            String message = PyLocalize.inspTypeHintsTypeSpecifiedBothInTypeCommentAndAnnotation().get();

            if (node instanceof PyFunction function) {
                if (function.getAnnotationValue() != null ||
                    ContainerUtil.or(function.getParameterList().getParameters(),
                        it -> it instanceof PyNamedParameter namedParameter && namedParameter.getAnnotationValue() != null)) {
                    registerProblem(typeComment, message, new RemoveElementQuickFix(PyLocalize.qfixRemoveTypeComment()));
                    registerProblem(function.getNameIdentifier(), message, new RemoveFunctionAnnotations());
                }
            }
            else if (annotationOwner.getAnnotationValue() != null) {
                registerProblem(typeComment, message, new RemoveElementQuickFix(PyLocalize.qfixRemoveTypeComment()));
                registerProblem(annotationOwner.getAnnotation(), message, new RemoveElementQuickFix(PyLocalize.qfixRemoveAnnotation()));
            }
        }

        @Override
        @RequiredReadAction
        public void visitPyAnnotation(PyAnnotation node) {
            PyExpression value = node.getValue();
            if (value == null) {
                return;
            }
            checkAnnotation(value);
        }

        // JB: local function PyExpression.findSelvesInAnnotation(context) in checkAnnotation
        private static List<PyReferenceExpression> findSelvesInAnnotation(PyExpression expression, TypeEvalContext context) {
            return ContainerUtil.filter(PsiTreeUtil.findChildrenOfAnyType(expression, false, PyReferenceExpression.class), refExpr ->
                ContainerUtil.or(PyTypingTypeProvider.resolveToQualifiedNames(refExpr, context),
                    it -> PyTypingTypeProvider.SELF.equals(it) || PyTypingTypeProvider.SELF_EXT.equals(it)));
        }

        @RequiredReadAction
        private void checkAnnotation(PyExpression annotationValue) {
            if (!Helper.isValidTypeHint(annotationValue, myTypeEvalContext)) {
                registerProblem(annotationValue, PyLocalize.inspTypeHintsTypeHintIsNotValid().get());
            }

            checkForwardReferencesInBinaryExpression(annotationValue);

            checkRawConcatenateUsage(annotationValue);
            checkParamSpecComponentInNonParameterAnnotation(annotationValue);
            PyType type = deref(PyTypingTypeProvider.getType(annotationValue, myTypeEvalContext));
            if (type instanceof PyTupleType tupleType) {
                checkTupleIsValid(annotationValue, tupleType);
            }

            List<PyReferenceExpression> selves = findSelvesInAnnotation(annotationValue, myTypeEvalContext);
            if (selves.isEmpty()) {
                return;
            }

            PyFunction functionParent = PsiTreeUtil.getParentOfType(annotationValue, PyFunction.class);
            if (functionParent != null) {
                if (PyAstFunction.Modifier.STATICMETHOD == functionParent.getModifier() && !PyNames.NEW.equals(functionParent.getName())) {
                    registerProblemForSelves(selves, PyLocalize.inspTypeHintsSelfUseInStaticmethod().get());
                }

                PyParameter[] parameters = functionParent.getParameterList().getParameters();
                if (parameters.length > 0) {
                    PyParameter firstParameter = parameters[0];
                    PyAnnotation firstAnnotation = firstParameter instanceof PyNamedParameter namedParameter ? namedParameter.getAnnotation() : null;
                    PyExpression firstAnnotationValue = firstAnnotation != null ? firstAnnotation.getValue() : null;
                    if (firstAnnotationValue != null && firstParameter.isSelf() &&
                        findSelvesInAnnotation(firstAnnotationValue, myTypeEvalContext).isEmpty() &&
                        !(deref(PyTypingTypeProvider.getType(firstAnnotationValue, myTypeEvalContext)) instanceof PySelfType)) {
                        String message = PyAstFunction.Modifier.CLASSMETHOD == functionParent.getModifier()
                            ? PyLocalize.inspTypeHintsSelfUseForClsParameterWithSelfAnnotation().get()
                            : PyLocalize.inspTypeHintsSelfUseForSelfParameterWithSelfAnnotation().get();
                        registerProblemForSelves(selves, message);
                    }
                }
            }
        }

        // JB: local function registerProblemForSelves(message) in checkAnnotation
        @RequiredReadAction
        private void registerProblemForSelves(List<PyReferenceExpression> selves, String message) {
            for (PyReferenceExpression it : selves) {
                registerProblem(it, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
            }
        }

        @RequiredReadAction
        private void checkTupleIsValid(PyExpression annotationValue, PyTupleType type) {
            List<PyType> elementTypes = type.getElementTypes();
            int unpackedTuplesCount = ContainerUtil.count(elementTypes, it -> it instanceof PyUnpackedTupleType || it instanceof PyTypeVarTupleType);
            if (unpackedTuplesCount > 1) {
                registerProblem(annotationValue, PyLocalize.inspTypeHintsAtMostOneUnpackedTuple().get());
            }
        }

        @Override
        @RequiredReadAction
        public void visitPyFunction(PyFunction node) {
            super.visitPyFunction(node);

            PyType returnType = myTypeEvalContext.getReturnType(node);
            if (returnType instanceof PyNarrowedType narrowedType) {
                List<PyCallableParameter> parameters = node.getParameters(myTypeEvalContext);
                boolean isInstanceOrClassMethod = node.asMethod() != null && node.getModifier() != PyAstFunction.Modifier.STATICMETHOD;
                int parameterIndex = isInstanceOrClassMethod ? 1 : 0;
                if (parameterIndex >= parameters.size()) {
                    registerProblem(node.getNameIdentifier(), PyLocalize.inspTypeHintsTypeisHasZeroParameters().get());
                }
                else if (narrowedType.getTypeIs()) {
                    PyCallableParameter parameter = parameters.get(parameterIndex);
                    PyType parameterType = parameter.getType(myTypeEvalContext);
                    if (!PyTypeChecker.match(parameterType, narrowedType.getNarrowedType(), myTypeEvalContext)) {
                        registerProblem(node.getNameIdentifier(), PyInspectionMessages.problemMessage(
                            a -> PyLocalize.inspTypeHintsTypeisDoesNotMatch(a[0], a[1]),
                            PythonDocumentationProvider.getTypeName(narrowedType.getNarrowedType(), myTypeEvalContext),
                            PythonDocumentationProvider.getTypeName(parameterType, myTypeEvalContext)));
                    }
                }
            }

            checkTypeCommentAndParameters(node);
            reportTypeParametersUsedByOuterScope(node);
            checkInitSelfParameterAnnotation(node);
            checkParamSpecComponents(node);
        }

        @Override
        @RequiredReadAction
        public void visitPyTargetExpression(PyTargetExpression node) {
            super.visitPyTargetExpression(node);

            checkAnnotatedNonSelfAttribute(node);
            checkTypeAliasTarget(node);

            PyAnnotation annotation = node.getAnnotation();
            if (annotation != null) {
                checkCircularReference(annotation, node);
            }
        }

        @RequiredReadAction
        private void checkTypeAliasTarget(PyTargetExpression target) {
            PsiElement parent = target.getParent();
            if (parent instanceof PyTypeDeclarationStatement) {
                PyAnnotation targetAnnotation = target.getAnnotation();
                PyExpression annotation = targetAnnotation != null ? targetAnnotation.getValue() : null;
                if (annotation instanceof PyReferenceExpression referenceExpression &&
                    resolvesToAnyOfQualifiedNames(referenceExpression, PyTypingTypeProvider.TYPE_ALIAS, PyTypingTypeProvider.TYPE_ALIAS_EXT)) {
                    registerProblem(target, PyLocalize.inspTypeHintsTypeAliasMustBeImmediatelyInitialized().get());
                }
            }
            else if (parent instanceof PyAssignmentStatement assignmentStatement &&
                PyTypingTypeProvider.isExplicitTypeAlias(assignmentStatement, myTypeEvalContext)) {
                PyExpression assignedValue = assignmentStatement.getAssignedValue();
                if (assignedValue != null) {
                    if (!Helper.isValidTypeHint(assignedValue, myTypeEvalContext)) {
                        registerProblem(assignedValue, PyLocalize.inspTypeHintsTypeAliasInvalidAssignedValue().get());
                    }
                }
                if (!PyUtil.isTopLevel(assignmentStatement)) {
                    if (!(ScopeUtil.getScopeOwner(assignmentStatement) instanceof PyClass)) {
                        registerProblem(target, PyLocalize.inspTypeHintsTypeAliasMustBeTopLevelDeclaration().get());
                    }
                    else if (assignedValue != null && usesBoundTypeParameters(assignedValue, target)) {
                        registerProblem(target, PyLocalize.inspTypeHintsTypeAliasInClassCannotUseBoundTypeVariables().get());
                    }
                }
            }
        }

        private boolean usesBoundTypeParameters(PyExpression typeHint, PyTargetExpression alias) {
            PyType type = deref(PyTypingTypeProvider.getType(typeHint, myTypeEvalContext));
            if (type == null) {
                return false;
            }
            return ContainerUtil.or(PyTypeChecker.collectGenerics(type, myTypeEvalContext).getAllTypeParameters(), it -> {
                PyQualifiedNameOwner owner = it.getScopeOwner();
                return owner != null && owner != alias;
            });
        }

        @RequiredReadAction
        private void checkTypeVarPlacement(PyCallExpression call, @Nullable PyExpression target) {
            if (target == null) {
                registerProblem(call, PyLocalize.inspTypeHintsTypevarExpressionMustBeAlwaysDirectlyAssignedToVariable().get());
            }
        }

        @RequiredReadAction
        private void checkTypeVarRedefinition(@Nullable PyExpression target) {
            ScopeOwner scopeOwner = ScopeUtil.getScopeOwner(target);
            if (scopeOwner == null) {
                return;
            }
            String name = target != null ? target.getName() : null;
            if (name == null) {
                return;
            }

            Instruction[] instructions = ControlFlowCache.getControlFlow(scopeOwner).getInstructions();
            int startInstruction = ControlFlowUtil.findInstructionNumberByElement(instructions, target);

            ControlFlowUtil.iteratePrev(startInstruction, instructions, instruction -> {
                if (instruction instanceof ReadWriteInstruction readWriteInstruction &&
                    instruction.num() != startInstruction &&
                    name.equals(readWriteInstruction.getName()) &&
                    readWriteInstruction.getAccess().isWriteAccess()) {
                    registerProblem(target, PyLocalize.inspTypeHintsTypeVariablesMustNotBeRedefined().get(),
                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
                    return ControlFlowUtil.Operation.BREAK;
                }
                else {
                    return ControlFlowUtil.Operation.NEXT;
                }
            });
        }

        @RequiredReadAction
        private void checkParamSpecArguments(PyCallExpression call, @Nullable PyExpression target) {
            processMatchedArgument(call, (name, argument) -> {
                if ("name".equals(name)) {
                    checkNameIsTheSameAsTarget(argument, target,
                        PyLocalize.inspTypeHintsParamspecExpectsStringLiteralAsFirstArgument().get(),
                        PyLocalize.inspTypeHintsArgumentToParamspecMustBeStringEqualToVariableName().get());
                }

                if ("default".equals(name) && argument != null) {
                    checkParamSpecDefaultValue(argument);
                }
            });
        }

        @RequiredReadAction
        private void checkTypeVarTupleArguments(PyCallExpression call, @Nullable PyExpression target) {
            processMatchedArgument(call, (name, argument) -> {
                if ("name".equals(name)) {
                    checkNameIsTheSameAsTarget(argument, target,
                        PyLocalize.inspTypeHintsTypevarTupleExpectsStringLiteralAsFirstArgument().get(),
                        PyLocalize.inspTypeHintsArgumentToTypevarTupleMustBeStringEqualToVariableName().get());
                }
                if ("default".equals(name) && argument != null) {
                    checkTypeVarTupleDefaultValue(argument, null);
                }
            });
        }

        @RequiredReadAction
        private void checkTypeAliasTypeArguments(PyCallExpression call, @Nullable PyExpression target) {
            processMatchedArgument(call, (name, argument) -> {
                if ("name".equals(name)) {
                    checkNameIsTheSameAsTarget(
                        argument, target,
                        PyLocalize.inspTypeHintsTypeAliasTypeExpectsStringLiteralAsFirstArgument().get(),
                        PyLocalize.inspTypeHintsArgumentToTypeAliasTypeMustBeStringEqualToVariableName().get()
                    );
                }
                else if ("type_params".equals(name)) {
                    if (argument != null && !isTupleOfTypeParameters(argument)) {
                        registerProblem(argument, PyLocalize.inspTypeHintsTypeAliasTypeParamsMustBeTupleOfTypeParameters().get());
                    }
                }
            });
        }

        private boolean isTupleOfTypeParameters(PyExpression expression) {
            if (!(PyPsiUtils.flattenParens(expression) instanceof PyTupleExpression tuple)) {
                return false;
            }
            PyResolveContext resolveContext = PyResolveContext.defaultContext(myTypeEvalContext);
            return ContainerUtil.and(tuple.getElements(), it ->
                it instanceof PyReferenceExpression &&
                    (PyUtil.multiResolveTopPriority(it, resolveContext).isEmpty() ||
                        deref(PyTypingTypeProvider.getType(it, myTypeEvalContext)) instanceof PyTypeParameterType));
        }

        @RequiredReadAction
        private void checkCastArguments(PyExpression[] arguments) {
            if (arguments.length == 2) {
                if (!Helper.isValidTypeHint(arguments[0], myTypeEvalContext)) {
                    registerProblem(arguments[0], PyLocalize.inspTypeHintsExpectedAType().get());
                }
            }
        }

        @RequiredReadAction
        private void checkTypeVarArguments(PyCallExpression call, @Nullable PyExpression target) {
            boolean[] covariant = {false};
            boolean[] contravariant = {false};
            SimpleReference<PyExpression> bound = SimpleReference.create();
            SimpleReference<PyExpression> defaultRef = SimpleReference.create();
            List<@Nullable PyExpression> constraints = new ArrayList<>();

            processMatchedArgument(call, (name, argument) -> {
                if (name == null) {
                    return;
                }
                switch (name) {
                    case "name" -> checkNameIsTheSameAsTarget(argument, target,
                        PyLocalize.inspTypeHintsTypevarExpectsStringLiteralAsFirstArgument().get(),
                        PyLocalize.inspTypeHintsArgumentToTypevarMustBeStringEqualToVariableName().get());
                    case "covariant" -> covariant[0] = PyEvaluator.evaluateAsBoolean(argument, false);
                    case "contravariant" -> contravariant[0] = PyEvaluator.evaluateAsBoolean(argument, false);
                    case "bound" -> bound.set(argument);
                    case "default" -> defaultRef.set(argument);
                    case "constraints" -> constraints.add(argument);
                    default -> {
                    }
                }
            });

            if (covariant[0] && contravariant[0]) {
                registerProblem(call, PyLocalize.inspTypeHintsBivariantTypeVariablesAreNotSupported().get(),
                    ProblemHighlightType.GENERIC_ERROR);
            }

            if (!constraints.isEmpty() && bound.get() != null) {
                registerProblem(call, PyLocalize.inspTypeHintsTypevarConstraintsCannotBeCombinedWithBound().get(),
                    ProblemHighlightType.GENERIC_ERROR);
            }

            if (constraints.size() == 1) {
                registerProblem(call, PyLocalize.inspTypeHintsSingleTypevarConstraintNotAllowed().get(),
                    ProblemHighlightType.GENERIC_ERROR);
            }

            PyExpression defaultExpression = defaultRef.get();
            if (defaultExpression != null) {
                PyType type = deref(PyTypingTypeProvider.getType(call, myTypeEvalContext));
                if (type instanceof PyTypeVarType typeVarType) {
                    checkTypeVarDefaultType(defaultExpression, typeVarType);
                }
            }

            List<@Nullable PyExpression> constraintsAndBound = new ArrayList<>(constraints);
            constraintsAndBound.add(bound.get());
            for (PyExpression it : constraintsAndBound) {
                if (it != null) {
                    PyType type = deref(PyTypingTypeProvider.getType(it, myTypeEvalContext));

                    if (PyTypeChecker.hasGenerics(type, myTypeEvalContext)) {
                        registerProblem(it, PyLocalize.inspTypeHintsTypevarConstraintsCannotBeParametrizedByTypeVariables().get(),
                            ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
                    }
                }
            }

            PyType boundType = bound.get() != null ? deref(PyTypingTypeProvider.getType(bound.get(), myTypeEvalContext)) : null;
            if (boundType instanceof PyClassLikeType classLikeType && PyTypingTypeProvider.TYPED_DICT.equals(classLikeType.getClassQName())) {
                registerProblem(bound.get(), PyLocalize.inspTypeHintsTypedDictIsNotAllowedAsABoundForATypeVar().get(),
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
            }
        }

        @RequiredReadAction
        private void checkTypeVarDefaultType(PyExpression defaultExpression, PyTypeVarType typeVarType) {
            Ref<? extends PyType> typeRef = typeVarType.getDefaultType();
            if (typeRef == null) {
                if (!Helper.isValidTypeHint(defaultExpression, myTypeEvalContext)) {
                    registerProblem(defaultExpression, PyLocalize.inspTypeHintsDefaultTypeMustBeTypeExpression().get(),
                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
                }
                return;
            }

            PyType defaultType = typeRef.get();
            if (defaultType instanceof PyParamSpecType) {
                registerProblem(defaultExpression,
                    PyInspectionMessages.problemMessage(a -> PyLocalize.inspTypeHintsCannotBeUsedInDefaultTypeOfTypeVar(a[0]), "ParamSpec"),
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
            }
            else if (defaultType instanceof PyTypeVarTupleType) {
                registerProblem(defaultExpression,
                    PyInspectionMessages.problemMessage(a -> PyLocalize.inspTypeHintsCannotBeUsedInDefaultTypeOfTypeVar(a[0]), "TypeVarTuple"),
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
            }
            else {
                validateTypeVarDefaultType(typeVarType, defaultType, defaultExpression);
            }
        }

        @RequiredReadAction
        private void checkTypeVarTupleDefaultValue(PyExpression defaultExpression, @Nullable PyTypeParameter typeParameter) {
            if ((typeParameter != null && defaultExpression instanceof PyStarExpression) || defaultExpression instanceof PySubscriptionExpression) {
                PyType type = deref(PyTypingTypeProvider.getType(defaultExpression, myTypeEvalContext));
                if (type instanceof PyPositionalVariadicType) {
                    return;
                }
            }
            registerProblem(defaultExpression, PyLocalize.inspTypeHintsDefaultTypeOfTypeVarTupleMustBeUnpacked().get(),
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
        }

        @RequiredReadAction
        private void checkParamSpecDefaultValue(PyExpression defaultExpression) {
            if (PyPsiUtils.flattenParens(defaultExpression) instanceof PyEllipsisLiteralExpression) {
                return;
            }
            if (defaultExpression instanceof PyListLiteralExpression listLiteralExpression) {
                for (PyExpression it : listLiteralExpression.getElements()) {
                    if (!Helper.isValidTypeHint(it, myTypeEvalContext)) {
                        registerProblem(it, PyLocalize.inspTypeHintsDefaultTypeMustBeTypeExpression().get(),
                            ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
                    }
                }
                return;
            }
            if (defaultExpression instanceof PyReferenceExpression || defaultExpression instanceof PyStringLiteralExpression) {
                PyType defaultType = deref(PyTypingTypeProvider.getType(defaultExpression, myTypeEvalContext));
                if (!(defaultType instanceof PyParamSpecType)) {
                    registerProblem(defaultExpression,
                        PyLocalize.inspTypeHintsDefaultTypeOfParamSpecMustBeParamSpecOrListOfTypes().get(),
                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
                }
                return;
            }
            registerProblem(defaultExpression,
                PyLocalize.inspTypeHintsDefaultTypeOfParamSpecMustBeParamSpecOrListOfTypes().get(),
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
        }

        @RequiredReadAction
        private void checkNameIsTheSameAsTarget(
            @Nullable PyExpression argument, @Nullable PyExpression target,
            String notStringLiteralMessage,
            String notEqualMessage
        ) {
            if (!(argument instanceof PyStringLiteralExpression stringLiteralExpression)) {
                registerProblem(argument, notStringLiteralMessage);
            }
            else {
                String targetName = target != null ? target.getName() : null;
                if (targetName != null && !targetName.equals(stringLiteralExpression.getStringValue())) {
                    registerProblem(argument,
                        notEqualMessage,
                        new ReplaceWithTargetNameQuickFix(targetName));
                }
            }
        }

        private void processMatchedArgument(
            PyCallExpression call,
            BiConsumer<@Nullable String, @Nullable PyExpression> processor
        ) {
            PyResolveContext resolveContext = PyResolveContext.defaultContext(myTypeEvalContext);
            PyCallExpression.PyArgumentsMapping mapping =
                ContainerUtil.find(call.multiMapArguments(resolveContext), PyCallExpression.PyArgumentsMapping::isComplete);
            if (mapping != null) {
                for (Map.Entry<PyExpression, PyCallableParameter> it : mapping.getMappedParameters().entrySet()) {
                    String name = it.getValue().getName();
                    PyExpression argument = PyUtil.peelArgument(it.getKey());
                    processor.accept(name, argument);
                }
            }
        }

        @RequiredReadAction
        private void checkInstanceAndClassChecks(PyCallExpression call) {
            if (call.isCalleeText(PyNames.ISINSTANCE, PyNames.ISSUBCLASS)) {
                PyExpression[] arguments = call.getArguments();
                PyExpression base = arguments.length > 1 ? arguments[1] : null;
                if (base == null) {
                    return;
                }
                checkInstanceAndClassChecksOn(base);
            }
        }

        @RequiredReadAction
        private void checkInstanceAndClassChecksOn(PyExpression base) {
            if (base instanceof PyBinaryExpression binaryExpression && binaryExpression.getOperator() == PyTokenTypes.OR) {
                if (PyTypingTypeProvider.isBitwiseOrUnionAvailable(base)) {
                    PyExpression left = binaryExpression.getLeftExpression();
                    PyExpression right = binaryExpression.getRightExpression();
                    if (left != null) {
                        checkInstanceAndClassChecksOn(left);
                    }
                    if (right != null) {
                        checkInstanceAndClassChecksOn(right);
                    }
                }
                return;
            }

            checkInstanceAndClassChecksOnExpression(base);
            checkInstanceAndClassChecksOnReference(base);
            checkInstanceAndClassChecksOnSubscription(base);
        }

        @RequiredReadAction
        private void checkInstanceAndClassChecksOnExpression(PyExpression base) {
            PyType type = myTypeEvalContext.getType(base);
            if (
                // T = TypeVar("T"); isinstance(x, T)
                type instanceof PyClassType classType && !classType.isDefinition() &&
                    PyTypingTypeProvider.TYPE_PARAMETER_FACTORIES.contains(StringUtil.notNullize(classType.getClassQName())) ||
                    // TODO should be caught by the type checker relying on the type hints for `isinstance`
                    // p: T; isinstance(x, p)
                    isTypeVarInstance(type) ||
                    // T = TypeVar("T"); isinstance(x, list[T])
                    type instanceof PyClassType classType2 && ContainerUtil.or(classType2.getTypeArguments(), it -> isTypeVarInstance(it)) &&
                        !classType2.isDefinition()
            ) {
                registerProblem(base,
                    PyLocalize.inspTypeHintsTypeVariablesCannotBeUsedWithInstanceClassChecks().get(),
                    ProblemHighlightType.GENERIC_ERROR);

            }
            if (type instanceof PyTypedDictType) {
                registerProblem(base,
                    PyLocalize.inspTypeHintsTypedDictTypeCannotBeUsedInIsinstanceTests().get(),
                    ProblemHighlightType.GENERIC_ERROR);
            }
            if (deref(PyTypingTypeProvider.getType(base, myTypeEvalContext)) instanceof PyTypingNewType) {
                registerProblem(base,
                    PyLocalize.inspTypeHintsNewTypeTypeCannotBeUsedInIsinstanceTests().get(),
                    ProblemHighlightType.GENERIC_ERROR);
            }
        }

        /**
         * Tells if [type] is a value of a type variable, such as `p` in `p: T`.
         * A type variable bound or constrained to class objects (`type[...]` or `type`) is excluded,
         * because its values are valid in an instance or class check.
         */
        private boolean isTypeVarInstance(@Nullable PyType type) {
            if (!(type instanceof PyTypeVarType typeVarType) || typeVarType.isDefinition()) {
                return false;
            }
            List<@Nullable PyType> upperBounds = typeVarType.getConstraints();
            if (upperBounds.isEmpty()) {
                PyType bound = typeVarType.getBound();
                upperBounds = bound != null ? List.of(bound) : List.of();
            }
            return upperBounds.isEmpty() || !ContainerUtil.and(upperBounds, it -> isClassObjectType(it));
        }

        private boolean isClassObjectType(@Nullable PyType type) {
            if (type instanceof PyUnionType unionType) {
                return ContainerUtil.and(unionType.getMembers(), it -> isClassObjectType(it));
            }
            else if (type instanceof PyInstantiableType<?> instantiableType && instantiableType.isDefinition()) {
                return true;
            }
            else if (type instanceof PyClassType classType) {
                return classType.getPyClass().isSubclass(PyNames.FQN.TYPE, myTypeEvalContext);
            }
            else {
                return false;
            }
        }

        @RequiredReadAction
        private void checkInstanceAndClassChecksOnReference(PyExpression base) {
            if (base instanceof PyReferenceExpression referenceExpression) {
                List<PsiElement> resolvedBase = multiFollowAssignmentsChain(referenceExpression);

                for (PsiElement element : resolvedBase) {
                    if (!(element instanceof PyQualifiedNameOwner qualifiedNameOwner)) {
                        continue;
                    }
                    String it = qualifiedNameOwner.getQualifiedName();
                    if (it == null) {
                        continue;
                    }
                    if (it.equals(PyTypingTypeProvider.UNION) && LanguageLevel.forElement(base).isAtLeast(LanguageLevel.PYTHON314)) {
                        continue;
                    }
                    switch (it) {
                        case PyTypingTypeProvider.ANY,
                             PyTypingTypeProvider.UNION,
                             PyTypingTypeProvider.OPTIONAL,
                             PyTypingTypeProvider.CLASS_VAR,
                             PyTypingTypeProvider.NO_RETURN,
                             PyTypingTypeProvider.FINAL,
                             PyTypingTypeProvider.FINAL_EXT,
                             PyTypingTypeProvider.LITERAL,
                             PyTypingTypeProvider.LITERAL_EXT,
                             PyTypingTypeProvider.ANNOTATED,
                             PyTypingTypeProvider.ANNOTATED_EXT,
                             PyTypingTypeProvider.TYPE_ALIAS,
                             PyTypingTypeProvider.TYPE_ALIAS_EXT,
                             PyTypingTypeProvider.SELF,
                             PyTypingTypeProvider.SELF_EXT -> {
                            String shortName = StringUtil.substringAfterLast(it, ".");
                            if (shortName == null) {
                                shortName = it;
                            }
                            registerProblem(base,
                                PyInspectionMessages.problemMessage(a -> PyLocalize.inspTypeHintsTypeCannotBeUsedWithInstanceClassChecks(a[0]),
                                    shortName),
                                ProblemHighlightType.GENERIC_ERROR);
                        }
                        default -> {
                        }
                    }
                }

                for (PsiElement element : resolvedBase) {
                    if (element instanceof PySubscriptionExpression subscriptionExpression && myTypeEvalContext.maySwitchToAST(subscriptionExpression)) {
                        checkInstanceAndClassChecksOnSubscriptionOperand(base, subscriptionExpression.getOperand());
                    }
                }
            }
        }

        @RequiredReadAction
        private void checkInstanceAndClassChecksOnSubscription(PyExpression base) {
            if (base instanceof PySubscriptionExpression subscriptionExpression) {
                checkInstanceAndClassChecksOnSubscriptionOperand(base, subscriptionExpression.getOperand());
            }
        }

        @RequiredReadAction
        private void checkInstanceAndClassChecksOnSubscriptionOperand(PyExpression base, PyExpression operand) {
            if (operand instanceof PyReferenceExpression referenceExpression) {
                for (PsiElement it : multiFollowAssignmentsChain(referenceExpression)) {
                    if (it instanceof PyQualifiedNameOwner qualifiedNameOwner) {
                        String qName = qualifiedNameOwner.getQualifiedName();
                        if (qName != null) {
                            switch (qName) {
                                case PyTypingTypeProvider.GENERIC,
                                     PyTypingTypeProvider.CLASS_VAR,
                                     PyTypingTypeProvider.FINAL,
                                     PyTypingTypeProvider.FINAL_EXT,
                                     PyTypingTypeProvider.LITERAL,
                                     PyTypingTypeProvider.LITERAL_EXT,
                                     PyTypingTypeProvider.ANNOTATED,
                                     PyTypingTypeProvider.ANNOTATED_EXT -> {
                                    registerParametrizedGenericsProblem(qName, base);
                                    continue;
                                }

                                case PyTypingTypeProvider.UNION,
                                     PyTypingTypeProvider.OPTIONAL -> {
                                    if (!PyTypingTypeProvider.isBitwiseOrUnionAvailable(base)) {
                                        registerParametrizedGenericsProblem(qName, base);
                                    }
                                    else if (base instanceof PySubscriptionExpression subscriptionExpression) {
                                        PyExpression[] arguments = getArguments(subscriptionExpression);
                                        if (arguments != null) {
                                            for (PyExpression argument : arguments) {
                                                checkInstanceAndClassChecksOn(argument);
                                            }
                                        }
                                    }
                                }

                                case PyTypingTypeProvider.CALLABLE,
                                     PyTypingTypeProvider.TYPE,
                                     PyTypingTypeProvider.PROTOCOL,
                                     PyTypingTypeProvider.PROTOCOL_EXT -> {
                                    registerProblem(base,
                                        PyLocalize.inspTypeHintsParameterizedGenericsCannotBeUsedWithInstanceClassChecks().get(),
                                        ProblemHighlightType.GENERIC_ERROR,
                                        null,
                                        base instanceof PySubscriptionExpression
                                            ? new LocalQuickFix[]{new RemoveGenericParametersQuickFix()}
                                            : LocalQuickFix.EMPTY_ARRAY);
                                    continue;
                                }
                                default -> {
                                }
                            }
                        }
                    }

                    if (it instanceof PyTypedElement typedElement) {
                        PyType type = myTypeEvalContext.getType(typedElement);

                        if (type instanceof PyClassType classType && classType.isDefinition()) {
                            registerProblem(base,
                                PyLocalize.inspTypeHintsParameterizedGenericsCannotBeUsedWithInstanceClassChecks().get(),
                                ProblemHighlightType.GENERIC_ERROR,
                                null,
                                base instanceof PySubscriptionExpression
                                    ? new LocalQuickFix[]{new RemoveGenericParametersQuickFix()}
                                    : LocalQuickFix.EMPTY_ARRAY);
                        }
                    }
                }
            }
        }

        @RequiredReadAction
        private void registerParametrizedGenericsProblem(String qName, PsiElement base) {
            String shortName = StringUtil.substringAfterLast(qName, ".");
            if (shortName == null) {
                shortName = qName;
            }
            registerProblem(base,
                PyInspectionMessages.problemMessage(a -> PyLocalize.inspTypeHintsTypeCannotBeUsedWithInstanceClassChecks(a[0]), shortName),
                ProblemHighlightType.GENERIC_ERROR);
        }

        @RequiredReadAction
        private boolean isInsideTypingAnnotatedMetadata(PyExpression expr) {
            PySubscriptionExpression parent = PsiTreeUtil.getParentOfType(expr, PySubscriptionExpression.class, false);
            while (parent != null) {
                PyExpression operand = parent.getOperand();
                if (operand instanceof PyReferenceExpression referenceExpression
                    && resolvesToAnyOfQualifiedNames(referenceExpression, PyTypingTypeProvider.ANNOTATED, PyTypingTypeProvider.ANNOTATED_EXT)) {
                    PyTupleExpression tuple = PyPsiUtils.flattenParens(parent.getIndexExpression()) instanceof PyTupleExpression t ? t : null;
                    if (tuple != null) {
                        PyExpression[] elements = tuple.getElements();
                        for (int i = 1; i < elements.length; i++) {
                            if (PsiTreeUtil.isAncestor(elements[i], expr, false)) {
                                return true;
                            }
                        }
                    }
                    break;  // only check the immediate Annotated parent
                }
                parent = PsiTreeUtil.getParentOfType(parent, PySubscriptionExpression.class, true);
            }
            return false;
        }

        @RequiredReadAction
        private void checkParenthesesOnGenerics(PyCallExpression call) {
            PyExpression callee = call.getCallee();
            if (callee instanceof PyReferenceExpression referenceExpression) {
                if (ContainerUtil.or(PyResolveUtil.resolveImportedElementQNameLocally(referenceExpression),
                    it -> PyTypingTypeProvider.GENERIC_CLASSES.contains(it.toString()))) {
                    registerProblem(call,
                        PyLocalize.inspTypeHintsGenericsShouldBeSpecifiedThroughSquareBrackets().get(),
                        ProblemHighlightType.GENERIC_ERROR,
                        null,
                        new ReplaceWithSubscriptionQuickFix());
                }
                else {
                    boolean isGeneric = false;
                    for (PsiElement element : multiFollowAssignmentsChain(referenceExpression)) {
                        PsiElement it = element instanceof PyFunction function ? function.getContainingClass() : element;
                        if (it instanceof PyWithAncestors withAncestors && PyTypingTypeProvider.isGeneric(withAncestors, myTypeEvalContext)) {
                            isGeneric = true;
                            break;
                        }
                    }
                    if (isGeneric) {
                        registerProblem(call, PyLocalize.inspTypeHintsGenericsShouldBeSpecifiedThroughSquareBrackets().get(),
                            new ReplaceWithSubscriptionQuickFix());
                    }
                }
            }
        }

        @RequiredReadAction
        private void checkPlainGenericInheritance(List<PyExpression> superClassExpressions) {
            for (PyExpression it : superClassExpressions) {
                if (it instanceof PyReferenceExpression referenceExpression &&
                    PyResolveUtil.resolveImportedElementQNameLocally(referenceExpression).contains(genericQName)) {
                    registerProblem(it, PyLocalize.inspTypeHintsCannotInheritFromPlainGeneric().get(),
                        ProblemHighlightType.GENERIC_ERROR);
                }
            }
        }

        @RequiredReadAction
        private void checkGenericDuplication(List<PyExpression> superClassExpressions) {
            boolean first = true;
            for (PyExpression superClass : superClassExpressions) {
                List<? extends PsiElement> resolved = superClass instanceof PyReferenceExpression referenceExpression
                    ? multiFollowAssignmentsChain(referenceExpression)
                    : List.of(superClass);

                boolean isGenericSubscription = false;
                for (PsiElement element : resolved) {
                    if (element instanceof PySubscriptionExpression subscriptionExpression &&
                        myTypeEvalContext.maySwitchToAST(subscriptionExpression) &&
                        subscriptionExpression.getOperand() instanceof PyReferenceExpression operand &&
                        PyResolveUtil.resolveImportedElementQNameLocally(operand).contains(genericQName)) {
                        isGenericSubscription = true;
                        break;
                    }
                }
                if (!isGenericSubscription) {
                    continue;
                }
                if (first) {
                    first = false;
                    continue;
                }
                registerProblem(superClass, PyLocalize.inspTypeHintsCannotInheritFromGenericMultipleTimes().get(),
                    ProblemHighlightType.GENERIC_ERROR);
            }
        }

        @RequiredReadAction
        private void checkMetaClass(@Nullable PyExpression metaClassExpression) {
            if (metaClassExpression != null) {
                PyType metaClassType = myTypeEvalContext.getType(metaClassExpression);
                if (metaClassType instanceof PyClassType classType &&
                    ContainerUtil.or(classType.getTypeArguments(), it -> it instanceof PyTypeVarType)) {
                    registerProblem(metaClassExpression, PyLocalize.inspTypeHintsMetaclassCannotBeGeneric().get());
                }
            }
        }

        @RequiredReadAction
        private void checkGenericCompleteness(PyClass cls) {
            boolean seenGeneric = false;
            Set<PyTargetExpression> genericTypeVars = new LinkedHashSet<>();
            Set<PyTargetExpression> nonGenericTypeVars = new LinkedHashSet<>();

            for (PyExpression superClass : cls.getSuperClassExpressions()) {
                Pair<@Nullable Set<PyTargetExpression>, Set<PyTargetExpression>> generics = collectGenerics(superClass);

                if (generics.getFirst() != null) {
                    genericTypeVars.addAll(generics.getFirst());
                    seenGeneric = true;
                }

                nonGenericTypeVars.addAll(generics.getSecond());
            }

            Set<PyTargetExpression> difference = new LinkedHashSet<>(nonGenericTypeVars);
            difference.removeAll(genericTypeVars);
            if (seenGeneric && !difference.isEmpty()) {
                String nonGenericTypeVarsNames = StringUtil.join(ContainerUtil.mapNotNull(nonGenericTypeVars, PyTargetExpression::getName), ", ");

                registerProblem(cls.getSuperClassExpressionList(),
                    PyInspectionMessages.problemMessage(a -> PyLocalize.inspTypeHintsGenericOrProtocolShouldListAllTypeVariables(a[0]),
                        nonGenericTypeVarsNames),
                    ProblemHighlightType.GENERIC_ERROR);
            }
        }

        @RequiredReadAction
        private Pair<@Nullable Set<PyTargetExpression>, Set<PyTargetExpression>> collectGenerics(PyExpression superClassExpression) {
            List<? extends PsiElement> resolvedSuperClass = superClassExpression instanceof PyReferenceExpression referenceExpression
                ? multiFollowAssignmentsChain(referenceExpression)
                : List.of(superClassExpression);

            boolean seenGeneric = false;
            Set<PyTargetExpression> genericTypeVars = new LinkedHashSet<>();
            Set<PyTargetExpression> nonGenericTypeVars = new LinkedHashSet<>();

            for (PsiElement element : resolvedSuperClass) {
                if (!(element instanceof PySubscriptionExpression superSubscription) || !myTypeEvalContext.maySwitchToAST(superSubscription)) {
                    continue;
                }
                PyExpression operand = superSubscription.getOperand();
                boolean generic =
                    operand instanceof PyReferenceExpression operandReference &&
                        ContainerUtil.or(PyResolveUtil.resolveImportedElementQNameLocally(operandReference),
                            it -> List.of(genericQName, protocolQName, protocolExtQName).contains(it));

                PyExpression[] argumentList = getArguments(superSubscription);
                List<PyExpression> arguments = argumentList != null ? Arrays.asList(argumentList) : List.of();
                Set<PyTargetExpression> superClassTypeVars = new LinkedHashSet<>();
                for (PyExpression argument : arguments) {
                    if (!(argument instanceof PyReferenceExpression argumentReference)) {
                        continue;
                    }
                    for (PsiElement it : multiFollowAssignmentsChain(argumentReference, this::followNotTypeVar)) {
                        if (!(it instanceof PyTargetExpression targetExpression)) {
                            continue;
                        }
                        PyClassType pyClassType = myTypeEvalContext.getType(targetExpression) instanceof PyClassType t ? t : null;
                        if (pyClassType == null || pyClassType.isDefinition()) {
                            continue;
                        }
                        if (PyTypingTypeProvider.TYPE_PARAMETER_FACTORIES.contains(StringUtil.notNullize(pyClassType.getClassQName()))) {
                            superClassTypeVars.add(targetExpression);
                        }
                    }
                }

                if (generic) {
                    genericTypeVars.addAll(superClassTypeVars);
                }
                else {
                    nonGenericTypeVars.addAll(superClassTypeVars);
                }
                seenGeneric = seenGeneric || generic;
            }

            return Pair.create(seenGeneric ? genericTypeVars : null, nonGenericTypeVars);
        }

        @RequiredReadAction
        private void checkInconsistentGenericBaseSubstitutions(PyClass cls) {
            Map<PyClass, PyClassType> seenSubstitutions = new HashMap<>();

            for (PyExpression superClassExpression : cls.getSuperClassExpressions()) {
                if (!(deref(PyTypingTypeProvider.getType(superClassExpression, myTypeEvalContext)) instanceof PyClassType superClassType)) {
                    continue;
                }
                PyTypeChecker.GenericSubstitutions substitutions = canonicalizeOwnTypeParameterSubstitutions(superClassType);
                Set<PyClass> classesToCheck = new LinkedHashSet<>();
                classesToCheck.add(superClassType.getPyClass());
                for (PyClassLikeType ancestorType : superClassType.getAncestorTypes(myTypeEvalContext)) {
                    if (ancestorType instanceof PyClassType ancestorClassType) {
                        classesToCheck.add(ancestorClassType.getPyClass());
                    }
                }

                for (PyClass pyClass : classesToCheck) {
                    PyClassType genericDefinitionType = PyTypeChecker.findGenericDefinitionType(pyClass, myTypeEvalContext);
                    if (genericDefinitionType == null) {
                        continue;
                    }
                    if (!(PyTypeChecker.substitute(genericDefinitionType, substitutions, myTypeEvalContext) instanceof PyClassType concreteType)) {
                        continue;
                    }
                    if (!concreteType.isParameterized()) {
                        continue;
                    }

                    PyClassType previous = seenSubstitutions.putIfAbsent(pyClass, concreteType);
                    if (previous != null && !sameTypeArguments(previous.getTypeArguments(), concreteType.getTypeArguments())) {
                        String pyClassName = pyClass.getName();
                        PyInspectionMessages.ProblemMessage msg = PyInspectionMessages.problemMessage(
                            a -> PyLocalize.inspTypeHintsInconsistentTypeVarOrder(a[0], a[1], a[2]),
                            pyClassName != null ? pyClassName : "<unknown>",
                            PyInspectionMessages.CodifiedParam.ofType(previous, superClassExpression, myTypeEvalContext),
                            PyInspectionMessages.CodifiedParam.ofType(concreteType, superClassExpression, myTypeEvalContext)
                        );
                        registerProblem(superClassExpression, msg, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
                        return;
                    }
                }
            }
        }

        private PyTypeChecker.GenericSubstitutions canonicalizeOwnTypeParameterSubstitutions(PyClassType superClassType) {
            PyTypeChecker.GenericSubstitutions substitutions = PyTypeChecker.collectTypeSubstitutions(superClassType, myTypeEvalContext);
            if (!superClassType.isParameterized()) {
                return substitutions;
            }
            List<PyType> typeArguments = superClassType.getTypeArguments();

            PyClassType genericDefinitionType = PyTypeChecker.findGenericDefinitionType(superClassType.getPyClass(), myTypeEvalContext);
            if (genericDefinitionType == null) {
                return substitutions;
            }
            List<PyTypeParameterType> ownTypeParameters =
                ContainerUtil.filterIsInstance(genericDefinitionType.getTypeArguments(), PyTypeParameterType.class);

            if (ownTypeParameters.isEmpty()) {
                return substitutions;
            }

            boolean stubSafeOrdering = true;
            for (PyTypeParameterType it : ownTypeParameters) {
                if (it.getDeclarationElement() instanceof PsiElement declaration && !myTypeEvalContext.maySwitchToAST(declaration)) {
                    stubSafeOrdering = false;
                    break;
                }
            }

            List<PyTypeParameterType> orderedTypeParameters;
            if (stubSafeOrdering) {
                orderedTypeParameters = new ArrayList<>(ownTypeParameters);
                orderedTypeParameters.sort(Comparator.comparingInt(it ->
                    it.getDeclarationElement() instanceof PsiElement declaration ? declaration.getTextOffset() : Integer.MAX_VALUE));
            }
            else {
                // Avoid forcing AST load for stub-based declarations (e.g., typeshed builtins).
                orderedTypeParameters = ownTypeParameters;
            }

            PyTypeParameterMapping mapping = PyTypeParameterMapping.mapByShape(
                orderedTypeParameters,
                typeArguments,
                PyTypeParameterMapping.Option.USE_DEFAULTS
            );
            if (mapping == null) {
                return substitutions;
            }

            Map<PyTypeVarType, @Nullable Ref<@Nullable PyType>> typeVars = new LinkedHashMap<>(substitutions.getTypeVars());
            Map<PyTypeVarTupleType, @Nullable PyPositionalVariadicType> typeVarTuples = new LinkedHashMap<>(substitutions.getTypeVarTuples());
            Map<PyParamSpecType, @Nullable PyCallableParameterVariadicType> paramSpecs = new LinkedHashMap<>(substitutions.getParamSpecs());

            for (Couple<PyType> pair : mapping.getMappedTypes()) {
                PyType expected = pair.getFirst();
                PyType actual = pair.getSecond();
                if (expected instanceof PyTypeVarType typeVarType) {
                    typeVars.put(typeVarType, Ref.create(actual));
                }
                else if (expected instanceof PyTypeVarTupleType typeVarTupleType) {
                    typeVarTuples.put(typeVarTupleType, actual instanceof PyPositionalVariadicType variadicType ? variadicType : null);
                }
                else if (expected instanceof PyParamSpecType paramSpecType) {
                    paramSpecs.put(paramSpecType, actual instanceof PyCallableParameterVariadicType variadicType ? variadicType : null);
                }
            }

            return new PyTypeChecker.GenericSubstitutions(typeVars, typeVarTuples, paramSpecs, substitutions.getSelfType());
        }

        private boolean sameTypeArguments(List<PyType> left, List<PyType> right) {
            if (left.size() != right.size()) {
                return false;
            }
            for (int i = 0; i < left.size(); i++) {
                PyType leftType = left.get(i);
                PyType rightType = right.get(i);
                boolean sameTypeArgument = Objects.equals(leftType, rightType) ||
                    (!(leftType instanceof PyTypeParameterType) || !(rightType instanceof PyTypeParameterType)) &&
                        PyTypeChecker.match(leftType, rightType, myTypeEvalContext) &&
                        PyTypeChecker.match(rightType, leftType, myTypeEvalContext);
                if (!sameTypeArgument) {
                    return false;
                }
            }
            return true;
        }

        @RequiredReadAction
        private void reportTypeParametersUsedByOuterScope(PyTypeParameterListOwner typeParameterListOwner) {
            PyTypeParameterList typeParameterList = typeParameterListOwner.getTypeParameterList();
            if (typeParameterList != null) {
                Set<String> typeParamsUsedByOuterScopes = new HashSet<>(getTypeParametersUsedByOuterScope(typeParameterListOwner));
                for (PyTypeParameter typeParameter : typeParameterList.getTypeParameters()) {
                    if (typeParamsUsedByOuterScopes.contains(typeParameter.getName())) {
                        registerProblem(typeParameter, PyInspectionMessages.problemMessage(
                            a -> PyLocalize.inspTypeHintsTypeParameterIsAlreadyInUseByOuterScope(a[0]), typeParameter.getName()));
                    }
                }
            }
            else if (typeParameterListOwner instanceof PyClass pyClass) {
                // Old generics syntax
                List<String> typeParamsUsedByOuterScopes = getTypeParametersUsedByOuterScope(pyClass);
                if (!typeParamsUsedByOuterScopes.isEmpty()) {
                    registerProblem(pyClass.getNameIdentifier(),
                        PyLocalize.inspTypeHintsSomeTypeVariablesAreAlreadyInUseByOuterScope(StringUtil.join(typeParamsUsedByOuterScopes, ", ")).get());
                }
            }
        }

        private List<String> getTypeParametersUsedByOuterScope(PyTypeParameterListOwner scope) {
            Set<String> typeParams = new LinkedHashSet<>(getTypeParameters(scope));
            List<String> typeParamsUsedByOuterScopes = new ArrayList<>();
            PyTypeParameterListOwner currentScope = scope;
            while (!typeParams.isEmpty()) {
                currentScope = PsiTreeUtil.getParentOfType(currentScope, PyTypeParameterListOwner.class);
                if (currentScope == null) {
                    break;
                }
                for (String typeParam : getTypeParameters(currentScope)) {
                    if (typeParams.remove(typeParam)) {
                        typeParamsUsedByOuterScopes.add(typeParam);
                    }
                }
            }
            return typeParamsUsedByOuterScopes;
        }

        private List<String> getTypeParameters(PyTypeParameterListOwner scope) {
            PyTypeParameterList typeParameterList = scope.getTypeParameterList();
            if (typeParameterList != null) {
                return ContainerUtil.mapNotNull(typeParameterList.getTypeParameters(), PyTypeParameter::getName);
            }
            if (scope instanceof PyClass pyClass) {
                PyClassType genericType = PyTypeChecker.findGenericDefinitionType(pyClass, myTypeEvalContext);
                if (genericType != null) {
                    List<PyTypeParameterType> typeParameters = ContainerUtil.filterIsInstance(genericType.getTypeArguments(), PyTypeParameterType.class);
                    // Without `Generic[...]`, a type parameter from an outer type parameter list does not make this class generic.
                    boolean listsTypeParameters = ContainerUtil.or(pyClass.getSuperClassExpressions(), base -> {
                        PyReferenceExpression operand = base instanceof PySubscriptionExpression subscriptionExpression &&
                            subscriptionExpression.getOperand() instanceof PyReferenceExpression referenceExpression ? referenceExpression : null;
                        return operand != null && ContainerUtil.or(PyResolveUtil.resolveImportedElementQNameLocally(operand),
                            it -> it.equals(genericQName) || it.equals(protocolQName) || it.equals(protocolExtQName));
                    });
                    List<String> result = new ArrayList<>();
                    for (PyTypeParameterType it : typeParameters) {
                        if (listsTypeParameters || !(it.getDeclarationElement() instanceof PyTypeParameter)) {
                            result.add(it.getName());
                        }
                    }
                    return result;
                }
            }
            if (scope instanceof PyFunction function) {
                List<String> result = new ArrayList<>();
                for (PyTypeParameterType it : PyTypingTypeProvider.collectTypeParameters(function, myTypeEvalContext)) {
                    result.add(it.getName());
                }
                return result;
            }
            return List.of();
        }


        @RequiredReadAction
        private void checkParameters(PySubscriptionExpression node) {
            if (!(node.getOperand() instanceof PyReferenceExpression operand)) {
                return;
            }
            PyExpression index = PyPsiUtils.flattenParens(node.getIndexExpression());
            if (index == null) {
                return;
            }

            QualifiedName callableQName = QualifiedName.fromDottedString(PyTypingTypeProvider.CALLABLE);
            QualifiedName literalQName = QualifiedName.fromDottedString(PyTypingTypeProvider.LITERAL);
            QualifiedName literalExtQName = QualifiedName.fromDottedString(PyTypingTypeProvider.LITERAL_EXT);
            QualifiedName annotatedQName = QualifiedName.fromDottedString(PyTypingTypeProvider.ANNOTATED);
            QualifiedName annotatedExtQName = QualifiedName.fromDottedString(PyTypingTypeProvider.ANNOTATED_EXT);
            QualifiedName typeAliasQName = QualifiedName.fromDottedString(PyTypingTypeProvider.TYPE_ALIAS);
            QualifiedName typeAliasExtQName = QualifiedName.fromDottedString(PyTypingTypeProvider.TYPE_ALIAS_EXT);
            QualifiedName typingSelf = QualifiedName.fromDottedString(PyTypingTypeProvider.SELF);
            QualifiedName typingExtSelf = QualifiedName.fromDottedString(PyTypingTypeProvider.SELF_EXT);
            QualifiedName unionQName = QualifiedName.fromDottedString(PyTypingTypeProvider.UNION);
            QualifiedName optionalQName = QualifiedName.fromDottedString(PyTypingTypeProvider.OPTIONAL);
            QualifiedName typeFormQName = QualifiedName.fromDottedString(PyTypingTypeProvider.TYPE_FORM);
            QualifiedName typeFormExtQName = QualifiedName.fromDottedString(PyTypingTypeProvider.TYPE_FORM_EXT);

            List<QualifiedName> qNames = PyResolveUtil.resolveImportedElementQNameLocally(operand);

            boolean typingOnly = true;
            boolean callableExists = false;
            boolean isAnnotated = false;

            for (QualifiedName it : qNames) {
                if (it.equals(genericQName)) {
                    checkTypingGenericParameters(node, false);
                }
                else if (it.equals(protocolQName) || it.equals(protocolExtQName)) {
                    checkTypingGenericParameters(node, true);
                }
                else if (it.equals(literalQName) || it.equals(literalExtQName)) {
                    PyExpression indexExpression = node.getIndexExpression();
                    if (indexExpression == null) {
                        continue;
                    }
                    checkLiteralParameter(indexExpression);
                }
                else if (it.equals(annotatedQName) || it.equals(annotatedExtQName)) {
                    isAnnotated = true;
                    checkAnnotatedParameter(index);
                }
                else if (it.equals(typeAliasQName) || it.equals(typeAliasExtQName)) {
                    reportParameterizedTypeAlias(index);
                }
                else if (it.equals(typingSelf) || it.equals(typingExtSelf)) {
                    reportParameterizedSelf(index);
                }
                else if (it.equals(unionQName)) {
                    checkGenericTypeArguments(node);
                }
                else if (it.equals(optionalQName)) {
                    checkGenericTypeArguments(node);
                    checkOptionalParameter(index);
                }
                else if (it.equals(typeFormQName) || it.equals(typeFormExtQName)) {
                    checkTypeFormParameter(index);
                }
                else if (it.equals(callableQName)) {
                    callableExists = true;
                    checkGenericTypeArguments(node, true);
                    checkCallableParameters(index);
                }
                else {
                    checkGenericTypeParameterization(node);
                }

                typingOnly = typingOnly && PyTypingTypeProvider.TYPING.equals(it.getFirstComponent());
            }

            if (qNames.isEmpty()) {
                checkGenericTypeParameterization(node);
            }
            else {
                if (typingOnly) {
                    checkTypingMemberParameters(index, callableExists, isAnnotated);
                }
            }
        }

        @RequiredReadAction
        private void reportParameterizedTypeAlias(PyExpression index) {
            // There is another warning in the type hint context
            if (!PyTypingTypeProvider.isInsideTypeHint(index, myTypeEvalContext)) {
                registerProblem(index, PyLocalize.inspTypeHintsTypeAliasCannotBeParameterized().get(),
                    ProblemHighlightType.GENERIC_ERROR);
            }
        }

        @RequiredReadAction
        private void reportParameterizedSelf(PyExpression index) {
            registerProblem(index, PyLocalize.inspTypeHintsTypingSelfCannotBeParameterized().get(),
                ProblemHighlightType.GENERIC_ERROR);
        }

        @RequiredReadAction
        private void checkLiteralParameter(PyExpression index) {
            PyExpression subParameter = index instanceof PySubscriptionExpression subscriptionExpression ? subscriptionExpression.getOperand() : null;
            if (subParameter instanceof PyReferenceExpression subParameterReference &&
                ContainerUtil.or(PyResolveUtil.resolveImportedElementQNameLocally(subParameterReference), qName -> {
                    String name = qName.toString();
                    return name.equals(PyTypingTypeProvider.LITERAL) || name.equals(PyTypingTypeProvider.LITERAL_EXT);
                })) {
                // if `index` is like `typing.Literal[...]` and has invalid form,
                // outer `typing.Literal[...]` won't be highlighted
                return;
            }

            if (PyLiteralType.fromLiteralParameter(index, myTypeEvalContext) == null && !hasUnresolvedReference(index)) {
                registerProblem(index, PyLocalize.inspTypeHintsIllegalLiteralParameter().get());
            }
        }

        @RequiredReadAction
        private boolean isAliasOfItself(PyTypeAliasStatement typeAlias) {
            Set<PyTypeAliasStatement> visited = new HashSet<>();
            PyTypeAliasStatement current = typeAlias;
            while (visited.add(current)) {
                PyExpression typeExpression = PyPsiUtils.flattenParens(current.getTypeExpression());
                PyExpression referenceCandidate = typeExpression instanceof PyStringLiteralExpression stringLiteralExpression
                    ? PyUtil.createExpressionFromFragment(stringLiteralExpression.getStringValue(), stringLiteralExpression)
                    : typeExpression;
                if (!(referenceCandidate instanceof PyReferenceExpression reference)) {
                    return false;
                }
                if (!(PyResolveUtil.resolveDeclaration(reference.getReference(getResolveContext()), getResolveContext())
                    instanceof PyTypeAliasStatement next)) {
                    return false;
                }
                current = next;
            }
            return current == typeAlias;
        }

        @RequiredReadAction
        private boolean hasUnresolvedReference(PyExpression expression) {
            List<PyReferenceExpression> references =
                new ArrayList<>(PsiTreeUtil.findChildrenOfType(expression, PyReferenceExpression.class));
            if (expression instanceof PyReferenceExpression referenceExpression) {
                references.add(referenceExpression);
            }
            return ContainerUtil.or(references, it -> it.getReference(getResolveContext()).multiResolve(false).length == 0);
        }

        @RequiredReadAction
        private void checkAnnotatedParameter(PyExpression index) {
            if (!(index instanceof PyTupleExpression)) {
                registerProblem(index, PyLocalize.inspTypeHintsAnnotatedMustBeCalledWithAtLeastTwoArguments().get());
            }
        }

        @RequiredReadAction
        private void checkGenericTypeParameterization(PySubscriptionExpression node) {
            if (!(node.getOperand() instanceof PyReferenceExpression operandRefExpression)) {
                return;
            }
            // A variable with more than one assignment in this file is not a type alias.
            List<@Nullable PsiElement> targets = PyUtil.multiResolveTopPriority(operandRefExpression, getResolveContext());
            if (targets.size() > 1 &&
                ContainerUtil.and(targets, it -> it instanceof PyTargetExpression && it.getContainingFile() == node.getContainingFile())) {
                return;
            }

            PsiElement declaration = ContainerUtil.getFirstItem(multiFollowAssignmentsChain(operandRefExpression, it -> {
                if (PyTypingTypeProvider.isExplicitTypeAlias(it, myTypeEvalContext)) {
                    return false;
                }
                else if (PyTypingAliasStubType.getAssignedValueStubLike(it) instanceof PyReferenceExpression) {
                    return followNotTypingOpaque(it);
                }
                else {
                    return false;
                }
            }));

            if (declaration instanceof PyTargetExpression targetExpression) {
                String qualifiedName = targetExpression.getQualifiedName();
                String builtinName = qualifiedName != null ? PyTypingTypeProvider.BUILTIN_COLLECTION_CLASSES.get(qualifiedName) : null;
                if (builtinName != null) {
                    PyClassType builtinType = PyBuiltinCache.getInstance(node).getObjectType(builtinName);
                    if (builtinType != null) {
                        checkGenericClassParameterization(node, builtinType.getPyClass());
                        return;
                    }
                }

                checkTypeAliasParameterization(node, targetExpression);
            }
            else if (declaration instanceof PyTypeAliasStatement typeAliasStatement) {
                checkTypeAliasStatementParameterization(node, typeAliasStatement);
            }
            else if (declaration instanceof PyClass pyClass) {
                checkGenericClassParameterization(node, pyClass);
            }
        }

        @RequiredReadAction
        private void checkGenericClassParameterization(PySubscriptionExpression node, PyClass declaration) {
            if (!PyTypingTypeProvider.isInsideTypeHint(node, myTypeEvalContext) &&
                PyTypeUtil.hasMetaclassGetItem(declaration, myTypeEvalContext)) {
                return;
            }
            PyClassType genericDefinitionType = PyTypeChecker.findGenericDefinitionType(declaration, myTypeEvalContext);
            if (genericDefinitionType == null) {
                if (PyTypingTypeProvider.isGeneric(declaration, myTypeEvalContext) &&
                    declaration.findMethodByName(PyNames.CLASS_GETITEM, false, myTypeEvalContext) == null) {
                    registerProblem(node.getIndexExpression(),
                        PyInspectionMessages.problemMessage(a -> PyLocalize.inspTypeHintsTypeArgumentsClassIsNotGeneric(a[0]),
                            PyInspectionMessages.CodifiedParam.ofReference(declaration)));
                }
                return;
            }

            List<@Nullable PyType> typeArguments = checkGenericTypeArguments(node);
            if (typeArguments == null) {
                return;
            }

            if (PyNames.FQN.TUPLE.equals(genericDefinitionType.getPyClass().getQualifiedName())) {
                checkTupleTypeForm(node);
                return;
            }

            List<PyType> typeParameters = genericDefinitionType.getTypeArguments();

            String typeParameterListRepresentation = "[" + StringUtil.join(typeParameters, it -> it.getName(), ", ") + "]";

            PyInspectionMessages.ProblemMessage message = PyInspectionMessages.problemMessage(
                a -> PyLocalize.inspTypeHintsTypeArgumentsDoNotMatchTypeParametersOfClass(a[0], a[1]),
                typeParameterListRepresentation,
                genericDefinitionType.getPyClass().getName());

            checkTypeArgumentsMatchTypeParameters(node, typeParameters, typeArguments, message);
        }

        @RequiredReadAction
        private void checkTypeAliasParameterization(PySubscriptionExpression node, PyTargetExpression declaration) {
            PyExpression assignedValue = PyTypingAliasStubType.getAssignedValueStubLike(declaration);
            if (assignedValue == null) {
                return;
            }
            PyType assignedValueType = deref(PyTypingTypeProvider.getType(assignedValue, myTypeEvalContext));
            if (assignedValueType == null) {
                return;
            }

            boolean isExplicitTypeAlias = PyTypingTypeProvider.isExplicitTypeAlias(declaration, myTypeEvalContext);
            PyTypeChecker.Generics generics = collectTypeParametersFromTypeAlias(assignedValue, assignedValueType, isExplicitTypeAlias);
            List<PyTypeParameterType> typeParametersOfType = new ArrayList<>(new LinkedHashSet<>(generics.getAllTypeParameters()));
            List<PyTypeParameterType> typeParametersInValue = findTypeParametersInTypeAliasValue(assignedValue);
            Set<String> namesOfType = ContainerUtil.map2Set(typeParametersOfType, PyTypeParameterType::getName);
            Set<String> namesInValue = ContainerUtil.map2Set(typeParametersInValue, PyTypeParameterType::getName);
            List<PyTypeParameterType> typeParameters =
                namesInValue.size() > namesOfType.size() && namesInValue.containsAll(namesOfType) ? typeParametersInValue
                    : typeParametersOfType;
            if (typeParameters.isEmpty() && generics.isEmpty()) {
                registerProblem(
                    node.getIndexExpression(),
                    PyLocalize.inspTypeHintsGenericTypeAliasIsNotGenericOrAlreadyParameterized().get()
                );
                return;
            }
            List<@Nullable PyType> typeArguments = checkGenericTypeArguments(node);
            if (typeArguments != null) {
                PyInspectionMessages.ProblemMessage message = PyInspectionMessages.problemMessage(
                    a -> PyLocalize.inspTypeHintsTypeArgumentsDoNotMatchTypeParametersOfAlias(a[0]), declaration.getName());
                checkTypeArgumentsMatchTypeParameters(node, new ArrayList<>(typeParameters), typeArguments, message);
            }
        }

        @RequiredReadAction
        private void checkTypeAliasStatementParameterization(PySubscriptionExpression node, PyTypeAliasStatement declaration) {
            PyTypeParameterList typeParameterList = declaration.getTypeParameterList();
            List<PyTypeParameterType> generics = typeParameterList != null
                ? ContainerUtil.mapNotNull(typeParameterList.getTypeParameters(), it ->
                PyTypingTypeProvider.getTypeParameterTypeFromTypeParameter(it,
                    myTypeEvalContext))
                : List.of();
            if (generics.isEmpty()) {
                registerProblem(
                    node.getIndexExpression(),
                    PyLocalize.inspTypeHintsGenericTypeAliasIsNotGenericOrAlreadyParameterized().get()
                );
                return;
            }
            List<@Nullable PyType> typeArguments = checkGenericTypeArguments(node);
            if (typeArguments != null) {
                PyInspectionMessages.ProblemMessage message = PyInspectionMessages.problemMessage(
                    a -> PyLocalize.inspTypeHintsTypeArgumentsDoNotMatchTypeParametersOfAlias(a[0]), declaration.getName());
                checkTypeArgumentsMatchTypeParameters(node, new ArrayList<>(generics), typeArguments, message);
            }
        }

        private PyTypeChecker.Generics collectTypeParametersFromTypeAlias(
            PyExpression assignedValue,
            PyType assignedValueType,
            boolean isExplicitTypeAlias
        ) {
            if (isExplicitTypeAlias || !(assignedValue instanceof PyReferenceExpression && assignedValueType instanceof PyClassType)) {
                return PyTypeChecker.collectGenerics(assignedValueType, myTypeEvalContext);
            }
            else {
                PyClassType genericDefinitionType =
                    PyTypeChecker.findGenericDefinitionType(((PyClassType) assignedValueType).getPyClass(), myTypeEvalContext);
                if (genericDefinitionType == null) {
                    return PyTypeChecker.Generics();
                }
                return PyTypeChecker.collectGenerics(genericDefinitionType, myTypeEvalContext);
            }
        }

        /**
         * Finds the type parameters that {@code value} of an old-style type alias references, also inside forward references.
         * <p>
         * The type of a recursive alias such as {@code Tree = Node["Tree[T]"]} can miss some of them,
         * because the type provider gives {@code Unknown} for the recursive reference.
         * The result keeps the order of the first occurrence.
         */
        @RequiredReadAction
        private List<PyTypeParameterType> findTypeParametersInTypeAliasValue(PyExpression value) {
            Set<PyTypeParameterType> result = new LinkedHashSet<>();
            value.accept(new PyRecursiveElementVisitor() {
                @Override
                @RequiredReadAction
                public void visitPySubscriptionExpression(PySubscriptionExpression node) {
                    PyReferenceExpression operand = node.getOperand() instanceof PyReferenceExpression referenceExpression ? referenceExpression : null;
                    if (operand != null && resolvesToAnyOfQualifiedNames(operand, PyTypingTypeProvider.LITERAL, PyTypingTypeProvider.LITERAL_EXT)) {
                        return;
                    }
                    if (operand != null && resolvesToAnyOfQualifiedNames(operand, PyTypingTypeProvider.ANNOTATED, PyTypingTypeProvider.ANNOTATED_EXT)) {
                        PyExpression index = PyPsiUtils.flattenParens(node.getIndexExpression());
                        PyExpression first = index instanceof PyTupleExpression tupleExpression
                            ? ArrayUtil.getFirstElement(tupleExpression.getElements())
                            : null;
                        PyExpression toVisit = first != null ? first : index;
                        if (toVisit != null) {
                            toVisit.accept(this);
                        }
                        return;
                    }
                    // A type parameter cannot be the operand, so only the index can contain one
                    PyExpression indexExpression = node.getIndexExpression();
                    if (indexExpression != null) {
                        indexExpression.accept(this);
                    }
                }

                @Override
                @RequiredReadAction
                public void visitPyStringLiteralExpression(PyStringLiteralExpression node) {
                    String contents = node.getStringValue();
                    PyExpression expression = PyUtil.createExpressionFromFragment(contents.contains("\n") ? "(" + contents + ")" : contents, node);
                    if (expression != null) {
                        expression.accept(this);
                    }
                }

                @Override
                public void visitPyReferenceExpression(PyReferenceExpression node) {
                    PyType type = deref(PyTypingTypeProvider.getType(node, myTypeEvalContext));
                    if (type instanceof PyTypeParameterType typeParameterType) {
                        result.add(typeParameterType);
                    }
                }
            });
            return new ArrayList<>(result);
        }

        @RequiredReadAction
        private @Nullable List<@Nullable PyType> checkGenericTypeArguments(PySubscriptionExpression node) {
            return checkGenericTypeArguments(node, false);
        }

        @RequiredReadAction
        private @Nullable List<@Nullable PyType> checkGenericTypeArguments(PySubscriptionExpression node, boolean isCallable) {
            PyExpression[] arguments = getArguments(node);
            if (arguments == null) {
                return null;
            }
            List<@Nullable PyType> argumentTypes = new ArrayList<>();

            for (int index = 0; index < arguments.length; index++) {
                PyExpression argument = arguments[index];
                PyExpression flatArgument = PyPsiUtils.flattenParens(argument);
                PyType argumentType;
                if (flatArgument instanceof PyReferenceExpression ||
                    flatArgument instanceof PySubscriptionExpression ||
                    flatArgument instanceof PyBinaryExpression ||
                    flatArgument instanceof PyStarExpression ||
                    flatArgument instanceof PyStringLiteralExpression ||
                    flatArgument instanceof PyListLiteralExpression ||
                    flatArgument instanceof PyCallExpression) {
                    Ref<PyType> typeRef = PyTypingTypeProvider.getType(argument, myTypeEvalContext);
                    if (typeRef == null && (flatArgument instanceof PyListLiteralExpression || !Helper.isValidTypeHint(argument, myTypeEvalContext))) {
                        String message;
                        if (argument instanceof PyReferenceExpression) {
                            message = PyLocalize.inspTypeHintsParametersToGenericTypesMustBeTypes().get();
                        }
                        else {
                            message = PyLocalize.inspTypeHintsInvalidTypeArgument().get();
                        }
                        registerProblem(argument, message, ProblemHighlightType.GENERIC_ERROR);
                    }
                    if (flatArgument instanceof PyListLiteralExpression listLiteralExpression) {
                        checkNoEllipsisInParameterList(listLiteralExpression);
                    }
                    argumentType = PyTypeUtil.derefOrUnknown(typeRef);
                }
                else if (flatArgument instanceof PyNoneLiteralExpression) {
                    argumentType = PyBuiltinCache.getInstance(node).getNoneType();
                }
                else if (flatArgument instanceof PyEllipsisLiteralExpression && (
                    isBuiltinTupleTypeForm(node, myTypeEvalContext) ||
                        isParamSpecArgument(node, index, myTypeEvalContext) ||
                        (isCallable && index == 0)
                )) {
                    argumentType = PyAnyType.getUnknown();
                }
                else if (flatArgument instanceof PyTupleExpression tupleExpression && (
                    (isBuiltinTupleTypeForm(node, myTypeEvalContext) && tupleExpression.getElements().length == 0) ||
                        (isCallable && index == 0)
                )) {
                    argumentType = PyAnyType.getUnknown();
                }
                else {
                    registerProblem(argument, PyLocalize.inspTypeHintsInvalidTypeArgument().get(), ProblemHighlightType.GENERIC_ERROR);
                    argumentType = PyAnyType.getUnknown();
                }
                argumentTypes.add(argumentType);
            }
            return argumentTypes;
        }

        @RequiredReadAction
        private void checkNoEllipsisInParameterList(PyListLiteralExpression parameterList) {
            for (PyExpression element : parameterList.getElements()) {
                if (PyPsiUtils.flattenParens(element) instanceof PyEllipsisLiteralExpression) {
                    registerProblem(element,
                        PyInspectionMessages.problemMessage(a -> PyLocalize.inspTypeHintsEllipsisNotAllowedInParameterList()),
                        ProblemHighlightType.GENERIC_ERROR);
                }
            }
        }

        @RequiredReadAction
        private void checkTupleTypeForm(PySubscriptionExpression node) {
            if (!isBuiltinTupleTypeForm(node, myTypeEvalContext)) {
                return;
            }
            PyExpression[] arguments = getArguments(node);
            if (arguments == null) {
                return;
            }

            for (int index = 0; index < arguments.length; index++) {
                PyExpression argument = arguments[index];
                PyExpression flatArgument = PyPsiUtils.flattenParens(argument);
                if (flatArgument instanceof PyEllipsisLiteralExpression && (index != arguments.length - 1 || arguments.length != 2)) {
                    registerProblem(flatArgument,
                        PyInspectionMessages.problemMessage(a -> PyLocalize.inspTypeHintsEllipsisAllowedOnlyAsSecondArgument()),
                        ProblemHighlightType.GENERIC_ERROR);
                }
                else if (flatArgument instanceof PyTupleExpression tupleExpression && tupleExpression.getElements().length == 0 &&
                    arguments.length != 1) {
                    registerProblem(flatArgument,
                        PyInspectionMessages.problemMessage(a -> PyLocalize.inspTypeHintsEmptyTupleOnlyAsLoneArgument()),
                        ProblemHighlightType.GENERIC_ERROR);
                }
            }

            PyExpression lastArgument = PyPsiUtils.flattenParens(ArrayUtil.getLastElement(arguments));
            if (lastArgument instanceof PyEllipsisLiteralExpression) {
                PyType type = deref(PyTypingTypeProvider.getType(arguments[0], myTypeEvalContext));
                if (type instanceof PyPositionalVariadicType) {
                    registerProblem(lastArgument,
                        PyInspectionMessages.problemMessage(a -> PyLocalize.inspTypeHintsEllipsisCannotBeUsedWithUnpackedType()),
                        ProblemHighlightType.GENERIC_ERROR);
                }
            }
        }

        @RequiredReadAction
        private void checkOptionalParameter(PyExpression index) {
            PyExpression flatIndexExpr = PyPsiUtils.flattenParens(index);
            PyExpression[] elements = flatIndexExpr instanceof PyTupleExpression tupleExpression
                ? tupleExpression.getElements()
                : new PyExpression[]{flatIndexExpr};
            if (elements.length != 1) {
                registerProblem(flatIndexExpr,
                    PyInspectionMessages.problemMessage(a -> PyLocalize.inspTypeHintsOptionalMustHaveExactlyOneArgument()),
                    ProblemHighlightType.GENERIC_ERROR);
            }
        }

        @RequiredReadAction
        private void checkTypeFormParameter(PyExpression index) {
            PyExpression flatIndexExpr = PyPsiUtils.flattenParens(index);
            PyExpression[] elements = flatIndexExpr instanceof PyTupleExpression tupleExpression
                ? tupleExpression.getElements()
                : new PyExpression[]{flatIndexExpr};
            if (elements.length != 1) {
                registerProblem(flatIndexExpr,
                    PyLocalize.inspTypeHintsTypeFormMustHaveExactlyOneArgument().get(),
                    ProblemHighlightType.GENERIC_ERROR);
            }
        }

        @RequiredReadAction
        private void checkTypingGenericParameters(PySubscriptionExpression node, boolean isProtocol) {
            PyExpression[] typeExpressions = getArguments(node);
            if (typeExpressions == null) {
                return;
            }
            Set<PyTypeParameterType> typeParams = new LinkedHashSet<>();
            Set<PyQualifiedNameOwner> typeParamDeclarations = new LinkedHashSet<>();
            boolean lastIsDefault = false;
            boolean lastIsTypeVarTuple = false;

            for (PyExpression typeExpr : typeExpressions) {
                if (!(typeExpr instanceof PyReferenceExpression) && !(typeExpr instanceof PyStarExpression) &&
                    !(typeExpr instanceof PySubscriptionExpression)) {
                    registerProblem(typeExpr,
                        PyInspectionMessages.problemMessage(a -> PyLocalize.inspTypeHintsParametersToGenericMustAllBeTypeVariables(a[0]),
                            isProtocol ? 1 : 0),
                        ProblemHighlightType.GENERIC_ERROR);
                    continue;
                }
                PyType typeParameterTypeCandidate = deref(PyTypingTypeProvider.getType(typeExpr, myTypeEvalContext));
                if (!(typeParameterTypeCandidate instanceof PyTypeParameterType typeParameterType)) {
                    if (hasUnresolvedReference(typeExpr)) {
                        continue;
                    }
                    registerProblem(typeExpr,
                        PyInspectionMessages.problemMessage(a -> PyLocalize.inspTypeHintsParametersToGenericMustAllBeTypeVariables(a[0]),
                            isProtocol ? 1 : 0),
                        ProblemHighlightType.GENERIC_ERROR);
                    continue;
                }
                if (!typeParams.add(typeParameterType)) {
                    registerProblem(typeExpr, PyLocalize.inspTypeHintsParametersToGenericMustAllBeUnique().get(),
                        ProblemHighlightType.GENERIC_ERROR);
                }

                Ref<? extends PyType> defaultType = typeParameterType.getDefaultType();
                if (defaultType != null) {
                    lastIsDefault = true;
                    if (lastIsTypeVarTuple && typeParameterType instanceof PyTypeVarType) {
                        registerProblem(typeExpr,
                            PyLocalize.inspTypeHintsDefaultTypeVarCannotFollowTypeVarTuple().get(),
                            ProblemHighlightType.GENERIC_ERROR);
                    }
                    PyTypeChecker.Generics genericTypesInDefaultExpr = PyTypeChecker.collectGenerics(deref(defaultType), myTypeEvalContext);
                    PyTypeParameterType defaultOutOfScope = ContainerUtil.find(genericTypesInDefaultExpr.getAllTypeParameters(),
                        typeVar -> typeVar.getDeclarationElement() != null && !typeParamDeclarations.contains(typeVar.getDeclarationElement()));

                    if (defaultOutOfScope != null) {
                        registerProblem(typeExpr,
                            PyLocalize.inspTypeHintsDefaultTypeRefersToTypeVarOutOfScope().get());
                    }
                }
                else if (isParamSpecWithEllipsisDefault(typeExpr)) {
                    lastIsDefault = true;
                }
                else if (lastIsDefault) {
                    registerProblem(typeExpr,
                        PyLocalize.inspTypeHintsNonDefaultTypeVarsCannotFollowDefaults().get(),
                        ProblemHighlightType.GENERIC_ERROR);
                }
                PyQualifiedNameOwner typeParamDeclaration = typeParameterType.getDeclarationElement();
                if (typeParamDeclaration != null) {
                    typeParamDeclarations.add(typeParamDeclaration);
                }
                lastIsTypeVarTuple = typeParameterType instanceof PyTypeVarTupleType;
            }
        }

        /**
         * Checks that {@code typeExpression} refers to a {@code ParamSpec} with {@code default=...}. The type of this default is not known.
         */
        @RequiredReadAction
        private boolean isParamSpecWithEllipsisDefault(PyExpression typeExpression) {
            if (!(typeExpression instanceof PyReferenceExpression reference)) {
                return false;
            }
            if (!(PyResolveUtil.resolveDeclaration(reference.getReference(getResolveContext()), getResolveContext())
                instanceof PyTargetExpression target)) {
                return false;
            }
            if (!(target.findAssignedValue() instanceof PyCallExpression call)) {
                return false;
            }
            return call.getKeywordArgument("default") instanceof PyEllipsisLiteralExpression;
        }

        @RequiredReadAction
        private void checkCallableParameters(PyExpression index) {

            if (!(index instanceof PyTupleExpression tupleIndex)) {
                registerProblem(index,
                    PyInspectionMessages.problemMessage(a -> PyLocalize.inspTypeHintsCallableMustHaveExactlyTwoParameters()),
                    ProblemHighlightType.GENERIC_ERROR);
                return;
            }

            PyExpression[] parameters = tupleIndex.getElements();
            if (parameters.length < 2) {
                registerProblem(index,
                    PyInspectionMessages.problemMessage(a -> PyLocalize.inspTypeHintsCallableMustHaveExactlyTwoParameters()),
                    ProblemHighlightType.GENERIC_ERROR);
                return;
            }

            boolean tooManyParameters = parameters.length > 2;
            if (tooManyParameters) {
                PyExpression firstExtraParameter = parameters[2];
                registerProblem(index,
                    PyInspectionMessages.problemMessage(a -> PyLocalize.inspTypeHintsCallableMustHaveExactlyTwoParameters()),
                    ProblemHighlightType.GENERIC_ERROR,
                    LocalQuickFix.EMPTY_ARRAY,
                    TextRange.create(firstExtraParameter.getStartOffsetInParent(), index.getTextLength()));
            }

            PyExpression first = parameters[0];
            if (!isSdkAvailable(first) || isParamSpecOrConcatenate(first, myTypeEvalContext)) {
                return;
            }

            if (first instanceof PySubscriptionExpression firstSubscription &&
                ContainerUtil.or(PyTypingTypeProvider.resolveToQualifiedNames(firstSubscription.getOperand(), myTypeEvalContext),
                    it -> PyTypingTypeProvider.CONCATENATE.equals(it) || PyTypingTypeProvider.CONCATENATE_EXT.equals(it))) {
                return;
            }

            if (!(first instanceof PyListLiteralExpression) && !(first instanceof PyEllipsisLiteralExpression)) {
                PyInspectionMessages.ProblemMessage message =
                    PyInspectionMessages.problemMessage(a -> PyLocalize.inspTypeHintsCallableInvalidFirstParameter());
                if (tooManyParameters) {
                    registerProblem(index, message, ProblemHighlightType.GENERIC_ERROR,
                        new LocalQuickFix[]{new SurroundElementsWithSquareBracketsQuickFix()},
                        TextRange.create(first.getStartOffsetInParent(), first.getStartOffsetInParent() + first.getTextLength()));
                }
                else {
                    registerProblem(first, message, ProblemHighlightType.GENERIC_ERROR,
                        first instanceof PyParenthesizedExpression ? new ReplaceWithListQuickFix() : new SurroundElementWithSquareBracketsQuickFix());
                }
            }
        }

        // Peeks at the snapshot. Each new snapshot restarts the analysis.
        private boolean isSdkAvailable(PsiElement element) {
            // TODO consulo: findEvoPyProjectIfReady (pyproject model) missing
            // JB: ModuleUtilCore.findModuleForPsiElement(element)?.findEvoPyProjectIfReady()?.interpreter != null
            // Uses the check JB had before the pyproject model was introduced.
            return PythonSdkUtil.findPythonSdk(ModuleUtilCore.findModuleForPsiElement(element)) != null;
        }

        @RequiredReadAction
        private boolean isParamSpecOrConcatenate(PyExpression expression, TypeEvalContext context) {
            if (!(expression instanceof PyReferenceExpression) && !(expression instanceof PySubscriptionExpression)) {
                return false;
            }
            PyType parametersType = deref(PyTypingTypeProvider.getType(expression, context));
            return parametersType instanceof PyParamSpecType || parametersType instanceof PyConcatenateType;
        }

        @RequiredReadAction
        private void checkRawConcatenateUsage(PyExpression expression) {
            if (expression instanceof PySubscriptionExpression &&
                deref(PyTypingTypeProvider.getType(expression, myTypeEvalContext)) instanceof PyConcatenateType) {
                registerProblem(
                    expression,
                    PyLocalize.inspTypeHintsConcatenateCanOnlyBeUsedInsideCallable().get()
                );
            }
        }

        @RequiredReadAction
        private void checkForwardReferencesInBinaryExpression(PyExpression expression) {
            // Stub files are never executed, so a string operand of `|` cannot fail at runtime there
            if (PyiUtil.isInsideStub(expression)) {
                return;
            }
            if (expression instanceof PyBinaryExpression binaryExpression && binaryExpression.getOperator() == PyTokenTypes.OR) {
                expression.accept(new PyRecursiveElementVisitor() {
                    @Override
                    @RequiredReadAction
                    public void visitPyStringLiteralExpression(PyStringLiteralExpression node) {
                        if (node.getParent() instanceof PyBinaryExpression) {
                            registerProblem(node, PyLocalize.inspTypeHintsForwardReferenceInUnion().get(),
                                ProblemHighlightType.GENERIC_ERROR);
                        }
                        super.visitPyStringLiteralExpression(node);
                    }
                });
            }
        }

        @RequiredReadAction
        private void checkCircularReference(PyAnnotation annotation, PyTargetExpression targetExpr) {
            if (!(annotation.getValue() instanceof PyStringLiteralExpression annotationValue)) {
                return;
            }
            String stringValue = annotationValue.getStringValue();
            if (!stringValue.equals(targetExpr.getName())) {
                return;
            }

            PsiFile contextFile = FileContextUtil.getContextFile(annotation);
            if (contextFile == null) {
                return;
            }
            if (!(PyUtil.createExpressionFromFragment(stringValue, contextFile) instanceof PyReferenceExpression referenceFromStringLiteral)) {
                return;
            }
            List<@Nullable PsiElement> resolveResults = PyUtil.multiResolveTopPriority(referenceFromStringLiteral,
                PyResolveContext.defaultContext(myTypeEvalContext));
            if (resolveResults.isEmpty() || (resolveResults.size() == 1 && resolveResults.get(0) == targetExpr)) {
                registerProblem(annotationValue, PyLocalize.inspTypeHintsCircularReference().get(),
                    ProblemHighlightType.GENERIC_ERROR);
            }
        }

        @RequiredReadAction
        private void checkTypingMemberParameters(PyExpression index, boolean dropFirst, boolean takeFirst) {
            if (dropFirst && takeFirst) {
                throw new IllegalArgumentException("Failed requirement.");
            }

            PyExpression[] parameters = index instanceof PyTupleExpression tupleExpression ? tupleExpression.getElements() : new PyExpression[]{index};

            boolean alreadyHaveUnpacking = false;
            int from = dropFirst ? 1 : 0;
            int count = takeFirst ? 1 : parameters.length;
            for (int i = from; i < parameters.length && i - from < count; i++) {
                PyExpression argument = parameters[i];
                PyExpression flatArgument = PyPsiUtils.flattenParens(argument);
                if (flatArgument instanceof PyListLiteralExpression) {
                    registerProblem(flatArgument,
                        PyLocalize.inspTypeHintsParametersToGenericTypesMustBeTypes().get(),
                        ProblemHighlightType.GENERIC_ERROR,
                        null,
                        new RemoveSquareBracketsQuickFix());
                }
                else if (flatArgument instanceof PyStarExpression) {
                    if (alreadyHaveUnpacking) {
                        registerProblem(flatArgument,
                            PyLocalize.inspTypeHintsParametersToGenericTypesCannotContainMoreThanOneUnpacking().get(),
                            ProblemHighlightType.GENERIC_ERROR);
                    }
                    else {
                        alreadyHaveUnpacking = true;
                    }
                }
            }
        }

        @RequiredReadAction
        private void checkTupleMatching(PyExpression expression) {
            if (!(expression instanceof PyTupleExpression tupleExpression)) {
                return;
            }

            if (!(PyPsiUtils.getRealContext(expression).getParent() instanceof PyAssignmentStatement assignment)) {
                return;
            }
            PyExpression lhs = assignment.getLeftHandSideExpression();
            if (lhs == null) {
                return;
            }

            if (PyTypingTypeProvider.mapTargetsToAnnotations(lhs, expression).isEmpty() &&
                (tupleExpression.getElements().length != 0 || assignment.getRawTargets().length != 0)) {
                registerProblem(expression, PyLocalize.inspTypeHintsTypeCommentCannotBeMatchedWithUnpackedVariables().get());
            }
        }

        @RequiredReadAction
        private void checkTypeCommentAndParameters(PyFunction node) {
            PyFunctionTypeAnnotation functionTypeAnnotation = PyTypingTypeProvider.getFunctionTypeAnnotation(node);
            if (functionTypeAnnotation == null) {
                return;
            }

            List<PyExpression> parameterTypes = functionTypeAnnotation.getParameterTypeList().getParameterTypes();
            if (parameterTypes.size() == 1 && parameterTypes.get(0) instanceof PyEllipsisLiteralExpression) {
                return;
            }

            int actualParametersSize = node.getParameterList().getParameters().length;
            int commentParametersSize = parameterTypes.size();

            PyClass cls = node.getContainingClass();
            PyAstFunction.Modifier modifier = node.getModifier();

            boolean hasSelf = cls != null && modifier != PyAstFunction.Modifier.STATICMETHOD;

            if (commentParametersSize < actualParametersSize - (hasSelf ? 1 : 0)) {
                registerProblem(node.getTypeComment(), PyLocalize.inspTypeHintsTypeSignatureHasTooFewArguments().get());
            }
            else if (commentParametersSize > actualParametersSize) {
                registerProblem(node.getTypeComment(), PyLocalize.inspTypeHintsTypeSignatureHasTooManyArguments().get());
            }
            else if (hasSelf && actualParametersSize == commentParametersSize) {
                if (!(myTypeEvalContext.getType(cls) instanceof PyInstantiableType<?> instantiableType)) {
                    return;
                }
                PyType actualSelfType = modifier == PyAstFunction.Modifier.CLASSMETHOD ? instantiableType.toClass() : instantiableType.toInstance();
                if (actualSelfType == null) {
                    return;
                }

                PyExpression firstParameterType = ContainerUtil.getFirstItem(parameterTypes);
                Ref<PyType> commentSelfTypeRef = firstParameterType != null ? PyTypingTypeProvider.getType(firstParameterType, myTypeEvalContext) : null;
                PyType commentSelfType = commentSelfTypeRef != null ? commentSelfTypeRef.get() : null;
                if (commentSelfType == null) {
                    return;
                }

                if (!PyTypeChecker.match(commentSelfType, actualSelfType, myTypeEvalContext)) {
                    String actualSelfTypeDescription = PythonDocumentationProvider.getTypeName(actualSelfType, myTypeEvalContext);
                    String commentSelfTypeDescription = PythonDocumentationProvider.getTypeName(commentSelfType, myTypeEvalContext);

                    registerProblem(node.getTypeComment(), PyInspectionMessages.problemMessage(
                        a -> PyLocalize.inspTypeHintsTypeSelfNotSupertypeItsClass(a[0], a[1]),
                        commentSelfTypeDescription, actualSelfTypeDescription));
                }
            }
        }

        @RequiredReadAction
        private void checkInitSelfParameterAnnotation(PyFunction function) {
            // Report the use of class-scoped type vars in a type hint for `self` parameter of the `__init__` method,
            // which is not allowed
            if (!PyNames.INIT.equals(function.getName())) {
                return;
            }

            PyFunction method = function.asMethod();
            if (method == null) {
                return;
            }
            PyClass containingClass = method.getContainingClass();
            if (containingClass == null) {
                return;
            }

            PyParameter selfParameterCandidate = ArrayUtil.getFirstElement(function.getParameterList().getParameters());
            if (!(selfParameterCandidate instanceof PyNamedParameter selfParameter)) {
                return;
            }

            PyAnnotation selfAnnotation = selfParameter.getAnnotation();
            PyExpression selfAnnotationValue = selfAnnotation != null ? selfAnnotation.getValue() : null;
            if (selfAnnotationValue == null) {
                return;
            }
            Ref<PyType> selfAnnotationType = PyTypingTypeProvider.getType(selfAnnotationValue, myTypeEvalContext);
            if (selfAnnotationType == null) {
                return;
            }

            PyTypeChecker.Generics generics = PyTypeChecker.collectGenerics(selfAnnotationType.get(), myTypeEvalContext);
            if (ContainerUtil.or(generics.getTypeVars(), it -> it.getScopeOwner() == containingClass)) {
                registerProblem(selfAnnotationValue,
                    PyLocalize.inspTypeHintsCannotUseClassScopeTypeVariablesInAnnotationForSelfParameterOf__init__().get());
            }
        }

        @RequiredReadAction
        private boolean isParamSpecInScope(String paramSpecName, PyFunction function) {
            PyTypeParameterListOwner current = function;
            while (current != null) {
                PyTypeParameterList typeParameterList = current.getTypeParameterList();
                if (typeParameterList != null &&
                    ContainerUtil.or(typeParameterList.getTypeParameters(),
                        it -> it.getKind() == PyAstTypeParameter.Kind.ParamSpec && paramSpecName.equals(it.getName()))) {
                    return true;
                }

                if (current instanceof PyFunction currentFunction) {
                    List<PyParamSpecType> paramSpecsInNonContainerParams = new ArrayList<>();
                    for (PyParameter parameter : currentFunction.getParameterList().getParameters()) {
                        if (parameter instanceof PyNamedParameter namedParameter &&
                            !namedParameter.isPositionalContainer() && !namedParameter.isKeywordContainer()) {
                            PyType type = myTypeEvalContext.getType(namedParameter);
                            paramSpecsInNonContainerParams.addAll(PyTypeChecker.collectGenerics(type, myTypeEvalContext).getParamSpecs());
                        }
                    }

                    if (ContainerUtil.or(paramSpecsInNonContainerParams, it -> paramSpecName.equals(it.getVariableName()))) {
                        return true;
                    }
                }

                if (current instanceof PyClass currentClass) {
                    PyClassType genericType = PyTypeChecker.findGenericDefinitionType(currentClass, myTypeEvalContext);
                    if (genericType != null &&
                        ContainerUtil.or(genericType.getTypeArguments(),
                            it -> it instanceof PyParamSpecType paramSpecType && paramSpecName.equals(paramSpecType.getVariableName()))) {
                        return true;
                    }
                }

                current = PsiTreeUtil.getParentOfType(current, PyTypeParameterListOwner.class);
            }
            return false;
        }

        @RequiredReadAction
        private void checkParamSpecComponents(PyFunction function) {
            List<PyNamedParameter> parameters = ContainerUtil.filterIsInstance(function.getParameterList().getParameters(), PyNamedParameter.class);

            ParamSpecComponent paramSpecArgs = null;
            ParamSpecComponent paramSpecKwargs = null;
            int argsIdx = -1;

            for (int index = 0; index < parameters.size(); index++) {
                PyNamedParameter param = parameters.get(index);
                ParamSpecComponent component = ParamSpecComponent.getParamSpecComponent(param, myTypeEvalContext);
                if (param.isPositionalContainer()) {
                    paramSpecArgs = component;
                    argsIdx = index;
                }
                else if (param.isKeywordContainer()) {
                    paramSpecKwargs = component;
                }
                else {
                    if (index == argsIdx + 1 && paramSpecArgs != null) {
                        String refName = paramSpecArgs.refName;
                        registerProblem(param,
                            PyInspectionMessages.problemMessage(a -> PyLocalize.inspTypeHintsParamspecNoParamsAllowedBetweenComponents(a[0]), refName),
                            ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
                    }
                    if (component != null) {
                        registerProblem(component.annotationValue,
                            PyLocalize.inspTypeHintsParamspecComponentNotAllowed().get(),
                            ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
                    }
                }
            }

            if (paramSpecArgs == null && paramSpecKwargs == null) {
                return;
            }

            validateParamSpecComponentsPair(paramSpecArgs, paramSpecKwargs, function, true);
            validateParamSpecComponentsPair(paramSpecKwargs, paramSpecArgs, function, false);
        }

        @RequiredReadAction
        private void validateParamSpecComponentsPair(
            @Nullable ParamSpecComponent first,
            @Nullable ParamSpecComponent second,
            PyFunction function,
            boolean firstShouldBeArgs
        ) {
            if (first != null) {
                if (second == null) {
                    registerProblem(first.annotationValue,
                        PyInspectionMessages.problemMessage(a -> PyLocalize.inspTypeHintsParamspecComponentsMustBePaired(a[0]), first.refName),
                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
                }
                boolean isWrongComponent = firstShouldBeArgs ? first.isKwargs() : first.isArgs();
                if (isWrongComponent) {
                    PyInspectionMessages.ProblemMessage msg = firstShouldBeArgs
                        ? PyInspectionMessages.problemMessage(a -> PyLocalize.inspTypeHintsParamspecKwargsMustAnnotateStarKwargs(a[0]), first.refName)
                        : PyInspectionMessages.problemMessage(a -> PyLocalize.inspTypeHintsParamspecArgsMustAnnotateStarArgs(a[0]), first.refName);

                    registerProblem(first.annotationValue, msg, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
                }
                if (!isParamSpecInScope(first.refName, function)) {
                    registerProblem(first.qualifier,
                        PyInspectionMessages.problemMessage(a -> PyLocalize.inspTypeHintsParamspecNotInScope(a[0]), first.refName),
                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
                }
            }
        }

        @RequiredReadAction
        private void checkParamSpecComponentInNonParameterAnnotation(PyExpression annotationValue) {
            PsiElement annotation = annotationValue.getParent();
            ParamSpecComponent component = ParamSpecComponent.getParamSpecComponent(annotationValue, myTypeEvalContext);
            if (component == null) {
                return;
            }
            PsiElement owner = annotation.getParent();
            if (owner instanceof PyNamedParameter) {
                return;
            }
            registerProblem(annotationValue,
                PyLocalize.inspTypeHintsParamspecComponentNotAllowed().get(),
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
        }

        @RequiredReadAction
        private void checkParamSpecPlacement(PyReferenceExpression node) {
            PsiElement parent = node.getParent();
            if (parent instanceof PyReferenceExpression parentReference && parentReference.getQualifier() == node) {
                return;
            }
            if (ParamSpecComponent.getParamSpecComponent(node, myTypeEvalContext) != null) {
                return;
            }

            PsiElement expression =
                parent instanceof PyExpressionStatement && parent.getParent() instanceof PyTypeHintFile ? PyPsiUtils.getRealContext(node) : node;
            while (expression.getParent() instanceof PyParenthesizedExpression) {
                expression = expression.getParent();
            }
            if (expression instanceof PyExpression pyExpression && isInsideTypingAnnotatedMetadata(pyExpression)) {
                return;
            }
            if (!isMisplacedParamSpec(expression)) {
                return;
            }

            PsiElement annotation = expression.getParent();
            String message = annotation instanceof PyAnnotation && annotation.getParent() instanceof PyNamedParameter
                ? PyLocalize.inspTypeHintsParamspecCannotAnnotateParameter().get()
                : PyLocalize.inspTypeHintsParamspecNotAllowedInThisContext().get();
            registerProblem(node, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
        }

        @RequiredReadAction
        private boolean isMisplacedParamSpec(PsiElement expression) {
            PsiElement parent = expression.getParent();
            if (parent instanceof PyAnnotation ||
                parent instanceof PyBinaryExpression ||
                parent instanceof PyListLiteralExpression ||
                parent instanceof PyTypeAliasStatement ||
                parent instanceof PyAssignmentStatement) {
                return true;
            }
            else if (parent instanceof PySubscriptionExpression subscriptionExpression) {
                return subscriptionExpression.getIndexExpression() == expression && isMisplacedTypeArgument(subscriptionExpression, 0, 1);
            }
            else if (parent instanceof PyTupleExpression tupleExpression) {
                if (!(tupleExpression.getParent() instanceof PySubscriptionExpression subscription)) {
                    return false;
                }
                return subscription.getIndexExpression() == tupleExpression &&
                    isMisplacedTypeArgument(subscription, ArrayUtil.indexOf(tupleExpression.getElements(), expression),
                        tupleExpression.getElements().length);
            }
            else {
                return false;
            }
        }

        /**
         * Checks a ParamSpec argument of a special form.
         * The check of a generic class or a type alias argument belongs to {@link #checkGenericTypeParameterization}.
         */
        @RequiredReadAction
        private boolean isMisplacedTypeArgument(PySubscriptionExpression subscription, int index, int count) {
            PyExpression operand = subscription.getOperand();
            Collection<String> qNames = PyTypingTypeProvider.resolveToQualifiedNames(operand, myTypeEvalContext);
            if (ContainerUtil.or(qNames, it -> it.equals(PyTypingTypeProvider.CALLABLE) || it.equals(PyTypingTypeProvider.CALLABLE_EXT))) {
                return index != 0;
            }
            else if (ContainerUtil.or(qNames, it -> it.equals(PyTypingTypeProvider.CONCATENATE) || it.equals(PyTypingTypeProvider.CONCATENATE_EXT))) {
                return index != count - 1;
            }
            else if (ContainerUtil.or(qNames, it -> it.equals(PyTypingTypeProvider.ANNOTATED) || it.equals(PyTypingTypeProvider.ANNOTATED_EXT))) {
                return index == 0;
            }
            else if (ContainerUtil.or(qNames, SPECIAL_FORMS_WITHOUT_PARAM_SPEC_ARGUMENTS::contains)) {
                return true;
            }
            else {
                return isBuiltinTupleTypeForm(subscription, myTypeEvalContext);
            }
        }

        @RequiredReadAction
        private void checkTypeArgumentsMatchTypeParameters(
            PySubscriptionExpression node,
            List<? extends @Nullable PyType> typeParameters,
            List<? extends @Nullable PyType> typeArguments,
            PyInspectionMessages.ProblemMessage message
        ) {
            PyExpression flatIndexExpr = PyPsiUtils.flattenParens(node.getIndexExpression());
            PyTypeParameterMapping mapping = PyTypeParameterMapping.mapByShape(typeParameters,
                typeArguments,
                PyTypeParameterMapping.Option.USE_DEFAULTS);
            if (mapping == null) {
                registerProblem(flatIndexExpr, message);
            }
            else {
                for (Couple<PyType> pair : mapping.getMappedTypes()) {
                    boolean matched =
                        PyTypeChecker.match(pair.getFirst(), pair.getSecond(), myTypeEvalContext);
                    if (!matched) {
                        String expectedName = PythonDocumentationProvider.getVerboseTypeName(pair.getFirst(), myTypeEvalContext);
                        String actualName = PythonDocumentationProvider.getTypeName(pair.getSecond(), myTypeEvalContext);
                        registerProblem(
                            flatIndexExpr,
                            PyInspectionMessages.problemMessage(a -> PyLocalize.inspTypeCheckerExpectedTypeGotTypeInstead(a[0], a[1]),
                                expectedName, actualName)
                        );
                        return;
                    }
                }
            }
        }

        @RequiredReadAction
        private void checkAnnotatedNonSelfAttribute(PyTargetExpression node) {
            PyExpression qualifier = node.getQualifier();
            if (qualifier == null) {
                return;
            }
            if (node.getAnnotation() == null && node.getTypeComment() == null) {
                return;
            }

            ScopeOwner scopeOwner = ScopeUtil.getScopeOwner(node);
            if (!(scopeOwner instanceof PyFunction function)) {
                registerProblem(node, PyLocalize.inspTypeHintsNonSelfAttributeCouldNotBeTypeHinted().get());
                return;
            }

            PyParameter firstParameter = ArrayUtil.getFirstElement(function.getParameterList().getParameters());
            PyParameter self = firstParameter != null && firstParameter.isSelf() ? firstParameter : null;
            if (self == null) {
                registerProblem(node, PyLocalize.inspTypeHintsNonSelfAttributeCouldNotBeTypeHinted().get());
                return;
            }
            List<@Nullable PsiElement> qualifierTargets = PyUtil.multiResolveTopPriority(qualifier, getResolveContext());
            if (!qualifierTargets.isEmpty() && !qualifierTargets.contains(self)) {
                registerProblem(node, PyLocalize.inspTypeHintsNonSelfAttributeCouldNotBeTypeHinted().get());
            }
        }

        private boolean followNotTypingOpaque(PyTargetExpression target) {
            String qualifiedName = target.getQualifiedName();
            return qualifiedName != null && !PyTypingTypeProvider.OPAQUE_NAMES.contains(qualifiedName);
        }

        @RequiredReadAction
        private boolean followNotTypeVar(PyTargetExpression target) {
            return !myTypeEvalContext.maySwitchToAST(target) || !(target.findAssignedValue() instanceof PyCallExpression);
        }

        @RequiredReadAction
        private List<PsiElement> multiFollowAssignmentsChain(PyReferenceExpression referenceExpression) {
            return multiFollowAssignmentsChain(referenceExpression, this::followNotTypingOpaque);
        }

        @RequiredReadAction
        private List<PsiElement> multiFollowAssignmentsChain(
            PyReferenceExpression referenceExpression,
            Predicate<PyTargetExpression> follow
        ) {
            return ContainerUtil.mapNotNull(referenceExpression.multiFollowAssignmentsChain(getResolveContext(), follow), it -> it.getElement());
        }

        @RequiredReadAction
        private boolean resolvesToAnyOfQualifiedNames(PyReferenceExpression referenceExpr, String... names) {
            for (PsiElement it : multiFollowAssignmentsChain(referenceExpr)) {
                if (it instanceof PyQualifiedNameOwner qualifiedNameOwner) {
                    String qualifiedName = qualifiedNameOwner.getQualifiedName();
                    if (qualifiedName != null && ArrayUtil.contains(qualifiedName, names)) {
                        return true;
                    }
                }
            }
            return false;
        }

        @RequiredReadAction
        private void validateTypeVarDefaultType(PyTypeVarType typeVarType, @Nullable PyType defaultType, PyExpression defaultExpression) {
            if (defaultType instanceof PyCallableParameterVariadicType) {
                registerProblem(defaultExpression, PyLocalize.inspTypeHintsDefaultTypeMustBeTypeExpression().get(),
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
                return;
            }

            List<@Nullable PyType> defaultTypes;
            if (defaultType instanceof PyTypeVarType defaultTypeVarType) {
                List<@Nullable PyType> constraints = defaultTypeVarType.getConstraints();
                if (constraints.isEmpty()) {
                    PyType bound = defaultTypeVarType.getBound();
                    List<@Nullable PyType> single = new ArrayList<>();
                    single.add(bound != null && !PyTypeUtilKt.isAnyOrUnknown(bound) ? bound : PyBuiltinCache.getInstance(defaultExpression).getObjectType());
                    defaultTypes = single;
                }
                else {
                    defaultTypes = constraints;
                }
            }
            else {
                List<@Nullable PyType> single = new ArrayList<>();
                single.add(defaultType);
                defaultTypes = single;
            }

            if (!PyTypeUtilKt.isUnknown(typeVarType.getBound())) {
                if (!ContainerUtil.and(defaultTypes, it -> PyTypeChecker.match(typeVarType.getBound(), it, myTypeEvalContext))) {
                    String message = PyLocalize.inspTypeHintsDefaultTypeDoNotMatchBounds().get();
                    // The aligned diff (for structural bounds) with the assignability breakdown appended below it on-the-fly.
                    String diff = PyTypeDiff.diffTooltip(typeVarType.getBound(), defaultType, myTypeEvalContext);
                    PyInspectionMessages.ProblemMessage problemMessage = new PyInspectionMessages.ProblemMessage(message, diff != null ? diff : message);
                    registerProblemWithTooltip(defaultExpression, problemMessage, ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                        LocalQuickFix.EMPTY_ARRAY,
                        () -> PyTypeCheckerInspectionProblemRegistrar.breakdownTooltip(problemMessage, typeVarType.getBound(), defaultType,
                            myTypeEvalContext, defaultExpression));
                }
            }
            else if (!typeVarType.getConstraints().isEmpty()) {
                if (!typeVarType.getConstraints().containsAll(defaultTypes)) {
                    registerProblem(defaultExpression, PyLocalize.inspTypeHintsDefaultTypeDoNotMatchConstraints().get(),
                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
                }
            }
        }
    }

    public static final class Helper {
        private Helper() {
        }

        /**
         * Returns {@code false} only when {@code expression} is surely not a valid type expression.
         * An expression that the type provider cannot evaluate, such as an unresolved reference, is valid.
         */
        @RequiredReadAction
        public static boolean isValidTypeHint(PyExpression expression, TypeEvalContext context) {
            return isValidTypeExpression(expression, context, new HashSet<>());
        }
    }

    /**
     * {@code visitedAliases} holds the variables whose values are already under the check. A variable that refers to itself, such as
     * {@code int = int}, is then valid, because the check cannot find what it means.
     */
    @RequiredReadAction
    private static boolean isValidTypeExpression(PyExpression expression, TypeEvalContext context, Set<PyTargetExpression> visitedAliases) {
        if (expression instanceof PyListLiteralExpression) {
            return false;
        }
        else if (expression instanceof PyCallExpression) {
            return deref(PyTypingTypeProvider.getType(expression, context)) instanceof PyTypeVarType;
        }
        else if (expression instanceof PySubscriptionExpression subscriptionExpression) {
            PyExpression operand = subscriptionExpression.getOperand();
            return operand instanceof PyReferenceExpression referenceExpression &&
                referenceIsCorrectTypeHint(referenceExpression, context, visitedAliases);
        }
        else if (expression instanceof PyStringLiteralExpression stringLiteralExpression) {
            return stringLiteralIsCorrectTypeHint(stringLiteralExpression, context, visitedAliases);
        }
        else if (expression instanceof PyReferenceExpression referenceExpression) {
            return referenceIsCorrectTypeHint(referenceExpression, context, visitedAliases);
        }
        else if (expression instanceof PyBinaryExpression binaryExpression && binaryExpression.getOperator() == PyTokenTypes.OR) {
            PyExpression left = binaryExpression.getLeftExpression();
            PyExpression right = binaryExpression.getRightExpression();
            return left != null && right != null &&
                isValidTypeExpression(left, context, visitedAliases) && isValidTypeExpression(right, context, visitedAliases);
        }
        else if (expression instanceof PyStarExpression starExpression) {
            if (PyTypingTypeProvider.getType(expression, context) != null) {
                return true;
            }
            PyExpression it = starExpression.getExpression();
            return it != null && PyTypingTypeProvider.getType(it, context) == null && isValidTypeExpression(it, context, visitedAliases);
        }
        else {
            return PyTypingTypeProvider.getType(expression, context) != null;
        }
    }

    private static final Set<String> SPECIAL_FORMS_WITHOUT_PARAM_SPEC_ARGUMENTS = Set.of(
        PyTypingTypeProvider.OPTIONAL,
        PyTypingTypeProvider.UNION,
        PyTypingTypeProvider.CLASS_VAR,
        PyTypingTypeProvider.FINAL, PyTypingTypeProvider.FINAL_EXT,
        PyTypingTypeProvider.REQUIRED, PyTypingTypeProvider.REQUIRED_EXT,
        PyTypingTypeProvider.NOT_REQUIRED, PyTypingTypeProvider.NOT_REQUIRED_EXT,
        PyTypingTypeProvider.READONLY, PyTypingTypeProvider.READONLY_EXT,
        PyTypingTypeProvider.TYPE_GUARD, PyTypingTypeProvider.TYPE_GUARD_EXT,
        PyTypingTypeProvider.TYPE_IS, PyTypingTypeProvider.TYPE_IS_EXT,
        PyTypingTypeProvider.TYPE_FORM, PyTypingTypeProvider.TYPE_FORM_EXT,
        PyTypingTypeProvider.UNPACK, PyTypingTypeProvider.UNPACK_EXT,
        PyTypingTypeProvider.TYPE, PyNames.FQN.TYPE
    );

    private static PyExpression @Nullable [] getArguments(PySubscriptionExpression expression) {
        PyExpression flatIndexExpr = PyPsiUtils.flattenParens(expression.getIndexExpression());
        if (flatIndexExpr == null) {
            return null;
        }
        return flatIndexExpr instanceof PyTupleExpression tupleExpression ? tupleExpression.getElements() : new PyExpression[]{flatIndexExpr};
    }

    private static boolean isBuiltinTupleTypeForm(PySubscriptionExpression subscription, TypeEvalContext context) {
        PyType operandType = context.getType(subscription.getOperand());
        return operandType instanceof PyClassType classType && !(operandType instanceof PyTupleType) &&
            PyNames.FQN.TUPLE.equals(classType.getClassQName());
    }

    @RequiredReadAction
    private static boolean isParamSpecArgument(PySubscriptionExpression subscription, int argIndex, TypeEvalContext context) {
        // Generic class parameterization (class Foo[T, **P]: ...)
        PyClassType operandType = context.getType(subscription.getOperand()) instanceof PyClassType classType ? classType : null;
        if (operandType != null) {
            PyClassType genericDefinitionType = PyTypeChecker.findGenericDefinitionType(operandType.getPyClass(), context);
            if (genericDefinitionType != null) {
                List<PyType> typeParameters = genericDefinitionType.getTypeArguments();
                if (argIndex >= 0 && argIndex < typeParameters.size() && typeParameters.get(argIndex) instanceof PyParamSpecType) {
                    return true;
                }
            }
        }

        // PEP-695 type alias: `type Alias[S1, **S2] = ...`
        PyResolveContext resolveContext = PyResolveContext.defaultContext(context);
        PyReferenceExpression operandRef = subscription.getOperand() instanceof PyReferenceExpression referenceExpression ? referenceExpression : null;
        if (operandRef != null) {
            PsiElement declaration = PyResolveUtil.resolveDeclaration(operandRef.getReference(), resolveContext);
            if (declaration instanceof PyTypeAliasStatement typeAliasStatement) {
                PyTypeParameterList typeParameterList = typeAliasStatement.getTypeParameterList();
                List<PyTypeParameter> typeParams = typeParameterList != null ? typeParameterList.getTypeParameters() : List.of();
                if (argIndex >= 0 && argIndex < typeParams.size()) {
                    return typeParams.get(argIndex).getKind() == PyAstTypeParameter.Kind.ParamSpec;
                }
            }

            // Old-style type alias: `Alias = Callable[P, T]`
            if (declaration instanceof PyTargetExpression targetExpression) {
                PyExpression aliasValue = PyTypingAliasStubType.getAssignedValueStubLike(targetExpression);
                if (aliasValue == null) {
                    return false;
                }
                PyType aliasType = deref(PyTypingTypeProvider.getType(aliasValue, context));
                if (aliasType == null) {
                    return false;
                }
                List<PyTypeParameterType> distinct =
                    new ArrayList<PyTypeParameterType>(new LinkedHashSet<PyTypeParameterType>(PyTypeChecker.collectGenerics(aliasType, context).getAllTypeParameters()));
                return argIndex >= 0 && argIndex < distinct.size() && distinct.get(argIndex) instanceof PyParamSpecType;
            }
        }

        return false;
    }

    private static final class ParamSpecComponent {
        static final String ARGS = "args";
        static final String KWARGS = "kwargs";

        final String refName;
        final PyExpression annotationValue;
        final PyExpression qualifier;
        final String component;

        private ParamSpecComponent(String refName, PyExpression annotationValue, PyExpression qualifier, String component) {
            this.refName = refName;
            this.annotationValue = annotationValue;
            this.qualifier = qualifier;
            this.component = component;
            if (!(component.equals(ARGS) || component.equals(KWARGS))) {
                throw new IllegalArgumentException("Invalid component: " + component);
            }
        }

        @RequiredReadAction
        static @Nullable ParamSpecComponent getParamSpecComponent(PyNamedParameter pyNamedParameter, TypeEvalContext context) {
            PyAnnotation annotation = pyNamedParameter.getAnnotation();
            PyExpression annotationValue = annotation != null ? annotation.getValue() : null;
            if (annotationValue == null) {
                return null;
            }
            PyExpression typeHint = unwrapTypeHint(annotationValue, context);
            if (typeHint == null) {
                return null;
            }
            ParamSpecComponent component = getParamSpecComponent(typeHint, context);
            if (component == null) {
                return null;
            }
            if (typeHint == annotationValue) {
                return component;
            }
            return new ParamSpecComponent(component.refName, annotationValue, annotationValue, component.component);
        }

        /**
         * Returns the type hint inside a quoted annotation, or inside the first argument of {@code Annotated}.
         */
        @RequiredReadAction
        private static @Nullable PyExpression unwrapTypeHint(PyExpression expression, TypeEvalContext context) {
            if (expression instanceof PyStringLiteralExpression stringLiteralExpression) {
                return PyUtil.createExpressionFromFragment(stringLiteralExpression.getStringValue(), stringLiteralExpression);
            }
            else if (expression instanceof PySubscriptionExpression subscriptionExpression &&
                ContainerUtil.or(PyTypingTypeProvider.resolveToQualifiedNames(subscriptionExpression.getOperand(), context),
                    it -> it.equals(PyTypingTypeProvider.ANNOTATED) || it.equals(PyTypingTypeProvider.ANNOTATED_EXT))) {
                PyExpression[] arguments = getArguments(subscriptionExpression);
                PyExpression first = arguments != null ? ArrayUtil.getFirstElement(arguments) : null;
                return first != null ? unwrapTypeHint(first, context) : null;
            }
            else {
                return expression;
            }
        }

        @RequiredReadAction
        static @Nullable ParamSpecComponent getParamSpecComponent(PyExpression annotationValue, TypeEvalContext context) {
            if (!(annotationValue instanceof PyReferenceExpression referenceExpression) || !referenceExpression.isQualified()) {
                return null;
            }
            String componentName = referenceExpression.getReferencedName();
            String component;
            if (ARGS.equals(componentName) || KWARGS.equals(componentName)) {
                component = componentName;
            }
            else {
                return null;
            }
            PyExpression qualifier = referenceExpression.getQualifier();
            if (qualifier == null) {
                return null;
            }
            PyType qualifierType = deref(PyTypingTypeProvider.getType(qualifier, context));
            if (!(qualifierType instanceof PyParamSpecType paramSpecType)) {
                return null;
            }
            return new ParamSpecComponent(paramSpecType.getVariableName(), annotationValue, qualifier, component);
        }

        boolean isArgs() {
            return component.equals(ARGS);
        }

        boolean isKwargs() {
            return component.equals(KWARGS);
        }
    }

    // JB: PsiUpdateModCommandQuickFix (ModCommand API is not available in Consulo)
    private static class ReplaceWithTypeNameQuickFix implements LocalQuickFix {
        private final String myTypeName;

        ReplaceWithTypeNameQuickFix(String typeName) {
            myTypeName = typeName;
        }

        @Override
        public LocalizeValue getName() {
            return PyLocalize.qfixReplaceWithTypeName();
        }

        @Override
        @RequiredWriteAction
        public void applyFix(Project project, ProblemDescriptor descriptor) {
            if (!(descriptor.getPsiElement() instanceof PyReferenceExpression element)) {
                return;
            }
            element.getReference().handleElementRename(myTypeName);
        }
    }

    // JB: PsiUpdateModCommandQuickFix (ModCommand API is not available in Consulo)
    private static class RemoveElementQuickFix implements LocalQuickFix {
        private final LocalizeValue myDescription;

        RemoveElementQuickFix(LocalizeValue description) {
            myDescription = description;
        }

        @Override
        public LocalizeValue getName() {
            return myDescription;
        }

        @Override
        @RequiredWriteAction
        public void applyFix(Project project, ProblemDescriptor descriptor) {
            PsiElement element = descriptor.getPsiElement();
            if (element != null) {
                element.delete();
            }
        }
    }

    // JB: PsiUpdateModCommandQuickFix (ModCommand API is not available in Consulo)
    private static class RemoveFunctionAnnotations implements LocalQuickFix {
        @Override
        public LocalizeValue getName() {
            return PyLocalize.qfixRemoveFunctionAnnotations();
        }

        @Override
        @RequiredWriteAction
        public void applyFix(Project project, ProblemDescriptor descriptor) {
            PsiElement element = descriptor.getPsiElement();
            if (element == null || !(element.getParent() instanceof PyFunction function)) {
                return;
            }

            PyAnnotation functionAnnotation = function.getAnnotation();
            if (functionAnnotation != null) {
                functionAnnotation.delete();
            }

            for (PyParameter parameter : function.getParameterList().getParameters()) {
                if (parameter instanceof PyNamedParameter namedParameter) {
                    PyAnnotation annotation = namedParameter.getAnnotation();
                    if (annotation != null) {
                        annotation.delete();
                    }
                }
            }
        }
    }

    // JB: PsiUpdateModCommandQuickFix (ModCommand API is not available in Consulo)
    private static class ReplaceWithTargetNameQuickFix implements LocalQuickFix {
        private final String myTargetName;

        ReplaceWithTargetNameQuickFix(String targetName) {
            myTargetName = targetName;
        }

        @Override
        public LocalizeValue getName() {
            return PyLocalize.qfixReplaceWithTargetName();
        }

        @Override
        @RequiredWriteAction
        public void applyFix(Project project, ProblemDescriptor descriptor) {
            if (!(descriptor.getPsiElement() instanceof PyStringLiteralExpression element)) {
                return;
            }
            PyStringLiteralExpression newElement = PyElementGenerator.getInstance(project).createStringLiteral(element, myTargetName);
            if (newElement == null) {
                return;
            }

            element.replace(newElement);
        }
    }

    // JB: PsiUpdateModCommandQuickFix (ModCommand API is not available in Consulo)
    private static class RemoveGenericParametersQuickFix implements LocalQuickFix {
        @Override
        public LocalizeValue getName() {
            return PyLocalize.qfixRemoveGenericParameters();
        }

        @Override
        @RequiredWriteAction
        public void applyFix(Project project, ProblemDescriptor descriptor) {
            if (!(descriptor.getPsiElement() instanceof PySubscriptionExpression element)) {
                return;
            }

            element.replace(element.getOperand());
        }
    }

    // JB: PsiUpdateModCommandQuickFix (ModCommand API is not available in Consulo)
    private static class ReplaceWithSubscriptionQuickFix implements LocalQuickFix {
        @Override
        public LocalizeValue getName() {
            return PyLocalize.qfixReplaceWithSquareBrackets();
        }

        @Override
        @RequiredWriteAction
        public void applyFix(Project project, ProblemDescriptor descriptor) {
            if (!(descriptor.getPsiElement() instanceof PyCallExpression element)) {
                return;
            }

            PyExpression calleeExpression = element.getCallee();
            if (calleeExpression == null) {
                return;
            }
            String callee = calleeExpression.getText();
            PyArgumentList argumentList = element.getArgumentList();
            if (argumentList == null) {
                return;
            }
            String argumentListText = argumentList.getText();
            String index = argumentListText.substring(1, argumentListText.length() - 1);

            Language language = element.getContainingFile().getLanguage();
            String text = language == PyFunctionTypeAnnotationDialect.INSTANCE ? "() -> " + callee + "[" + index + "]" : callee + "[" + index + "]";

            PsiFile file = PsiFileFactory
                .getInstance(project)
                // it's important to create file with same language as element's file to have correct behaviour in injections
                .createFileFromText("x.py", language, text, false, true);
            if (file != null && file.getFirstChild().getLastChild() instanceof PySubscriptionExpression subscriptionExpression) {
                element.replace(subscriptionExpression);
            }
        }
    }

    // JB: PsiUpdateModCommandQuickFix (ModCommand API is not available in Consulo)
    private static class SurroundElementsWithSquareBracketsQuickFix implements LocalQuickFix {
        @Override
        public LocalizeValue getName() {
            return PyLocalize.qfixSurroundWithSquareBrackets();
        }

        @Override
        @RequiredWriteAction
        public void applyFix(Project project, ProblemDescriptor descriptor) {
            if (!(descriptor.getPsiElement() instanceof PyTupleExpression element)) {
                return;
            }
            PyListLiteralExpression list = PyElementGenerator.getInstance(project).createListLiteral();

            PyExpression[] originalElements = element.getElements();
            for (int i = 0; i < originalElements.length - 1; i++) {
                list.add(originalElements[i]);
            }
            for (int i = 0; i < originalElements.length - 2; i++) {
                originalElements[i].delete();
            }

            element.getElements()[0].replace(list);
        }
    }

    // JB: PsiUpdateModCommandQuickFix (ModCommand API is not available in Consulo)
    private static class SurroundElementWithSquareBracketsQuickFix implements LocalQuickFix {
        @Override
        public LocalizeValue getName() {
            return PyLocalize.qfixSurroundWithSquareBrackets();
        }

        @Override
        @RequiredWriteAction
        public void applyFix(Project project, ProblemDescriptor descriptor) {
            PsiElement element = descriptor.getPsiElement();
            if (element == null) {
                return;
            }
            PyListLiteralExpression list = PyElementGenerator.getInstance(project).createListLiteral();

            list.add(element);

            element.replace(list);
        }
    }

    // JB: PsiUpdateModCommandQuickFix (ModCommand API is not available in Consulo)
    private static class ReplaceWithListQuickFix implements LocalQuickFix {
        @Override
        public LocalizeValue getName() {
            return PyLocalize.qfixReplaceWithSquareBrackets();
        }

        @Override
        @RequiredWriteAction
        public void applyFix(Project project, ProblemDescriptor descriptor) {
            PsiElement element = descriptor.getPsiElement();
            PyExpression expression = element instanceof PyParenthesizedExpression parenthesizedExpression
                ? parenthesizedExpression.getContainedExpression()
                : null;
            if (expression == null) {
                return;
            }
            PyExpression[] elements = expression instanceof PyTupleExpression tupleExpression
                ? tupleExpression.getElements()
                : new PyExpression[]{expression};

            PyListLiteralExpression list = PyElementGenerator.getInstance(project).createListLiteral();
            for (PyExpression it : elements) {
                list.add(it);
            }
            element.replace(list);
        }
    }

    // JB: PsiUpdateModCommandQuickFix (ModCommand API is not available in Consulo)
    private static class RemoveSquareBracketsQuickFix implements LocalQuickFix {
        @Override
        public LocalizeValue getName() {
            return PyLocalize.qfixRemoveSquareBrackets();
        }

        @Override
        @RequiredWriteAction
        public void applyFix(Project project, ProblemDescriptor descriptor) {
            if (!(descriptor.getPsiElement() instanceof PyListLiteralExpression element)) {
                return;
            }

            PySubscriptionExpression subscription = PsiTreeUtil.getParentOfType(element, PySubscriptionExpression.class, true, ScopeOwner.class);
            PyExpression index = subscription != null ? subscription.getIndexExpression() : null;
            if (index == null) {
                return;
            }

            List<PyExpression> newIndexElements = new ArrayList<>();
            if (index instanceof PyTupleExpression tupleExpression) {
                for (PyExpression it : tupleExpression.getElements()) {
                    if (it.equals(element)) {
                        newIndexElements.addAll(Arrays.asList(element.getElements()));
                    }
                    else {
                        newIndexElements.add(it);
                    }
                }
            }
            else {
                newIndexElements.addAll(Arrays.asList(element.getElements()));
            }

            if (newIndexElements.size() == 1) {
                index.replace(newIndexElements.get(0));
            }
            else {
                String newIndexText = "(" + StringUtil.join(newIndexElements, PsiElement::getText, ", ") + ")";

                PyExpression expression = PyElementGenerator.getInstance(project)
                    .createExpressionFromText(LanguageLevel.forElement(element), newIndexText);
                if (!(expression instanceof PyParenthesizedExpression parenthesizedExpression &&
                    parenthesizedExpression.getContainedExpression() instanceof PyTupleExpression newIndex)) {
                    return;
                }

                index.replace(newIndex);
            }
        }
    }

    // JB: PsiUpdateModCommandQuickFix (ModCommand API is not available in Consulo)
    private static class ReplaceWithTypingGenericAliasQuickFix implements LocalQuickFix {
        @Override
        public LocalizeValue getName() {
            return PyLocalize.qfixReplaceWithTypingAlias();
        }

        @Override
        @RequiredWriteAction
        public void applyFix(Project project, ProblemDescriptor descriptor) {
            if (!(descriptor.getPsiElement() instanceof PySubscriptionExpression element)) {
                return;
            }
            if (!(element.getOperand() instanceof PyReferenceExpression refExpr)) {
                return;
            }
            String refName = refExpr.getName();
            String alias = refName != null ? PyTypingTypeProvider.TYPING_BUILTINS_GENERIC_ALIASES.get(refName) : null;
            if (alias == null) {
                return;
            }

            LanguageLevel languageLevel = LanguageLevel.forElement(element);
            AddImportHelper.ImportPriority priority = languageLevel.isAtLeast(LanguageLevel.PYTHON35)
                ? AddImportHelper.ImportPriority.THIRD_PARTY
                : AddImportHelper.ImportPriority.BUILTIN;
            AddImportHelper.addOrUpdateFromImportStatement(element.getContainingFile(), "typing", alias, null, priority, element);
            PyExpression newRefExpr = PyElementGenerator.getInstance(project).createExpressionFromText(languageLevel, alias);
            refExpr.replace(newRefExpr);
        }
    }

    @RequiredReadAction
    private static boolean stringLiteralIsCorrectTypeHint(
        PyStringLiteralExpression stringLiteral,
        TypeEvalContext context,
        Set<PyTargetExpression> visitedAliases
    ) {
        if (!(stringLiteral.getFirstChild() instanceof PyPlainStringElement embeddedString)) {
            return false; // f-strings are not allowed
        }
        if (!embeddedString.getPrefix().isEmpty()) {
            return false; // prefixed strings are not allowed
        }
        if (stringLiteral.getStringElements().size() > 1) {
            return false;
        }
        String expressionText;
        if (embeddedString.isTripleQuoted()) {
            expressionText = "(" + trimIndent(embeddedString.getContent()) + ")";
        }
        else {
            expressionText = embeddedString.getContent();
        }
        PyExpression embeddedExpression = PyUtil.createExpressionFromFragment(expressionText, stringLiteral.getContainingFile());
        if (embeddedExpression == null) {
            return false;
        }
        return isValidTypeExpression(embeddedExpression, context, visitedAliases);
    }

    @RequiredReadAction
    private static boolean referenceIsCorrectTypeHint(
        PyReferenceExpression referenceExpression,
        TypeEvalContext context,
        Set<PyTargetExpression> visitedAliases
    ) {
        PyResolveContext resolveContext = PyResolveContext.defaultContext(context);
        PsiElement resolvedElement = PyResolveUtil.resolveDeclaration(referenceExpression.getReference(), resolveContext);
        if (resolvedElement == null) {
            return true; // We cannot be sure, let it better be false-negative
        }
        if (isClassMemberInDeferredAnnotation(referenceExpression, resolvedElement)) {
            return true;
        }
        // A function name is bound after the signature is evaluated, so the signature cannot refer to the function.
        if (resolvedElement instanceof PyFunction function && PsiTreeUtil.isAncestor(function, referenceExpression, true) &&
            !PsiTreeUtil.isAncestor(function.getStatementList(), referenceExpression, false)) {
            return true;
        }
        if (resolvedElement instanceof PyTargetExpression targetExpression) {
            String qName = targetExpression.getQualifiedName();
            if (qName == null) {
                return true;
            }
            if (PyTypingTypeProvider.OPAQUE_NAMES.contains(qName)) {
                return true;
            }
            PyExpression assignedTypeAliasValue = PyTypingAliasStubType.getAssignedValueStubLike(targetExpression);
            if (assignedTypeAliasValue != null) {
                if (!visitedAliases.add(targetExpression)) {
                    return true;
                }
                PyExpression typeAliasTypeValue = assignedTypeAliasValue instanceof PyCallExpression call
                    ? PyTypingTypeProvider.getTypeAliasTypeValue(call, context)
                    : null;
                // The placement of a ParamSpec has a separate check
                return isValidTypeExpression(typeAliasTypeValue != null ? typeAliasTypeValue : assignedTypeAliasValue, context, visitedAliases) ||
                    deref(PyTypingTypeProvider.getType(referenceExpression, context)) instanceof PyParamSpecType;
            }
            else {
                Ref<PyType> typeRef = PyTypingTypeProvider.getType(referenceExpression, context);
                if (typeRef == null) {
                    return !isVariableOfNonTypeValue(targetExpression, context);
                }
                return typeRef.get() instanceof PyClassLikeType;
            }
        }
        else if (resolvedElement instanceof PyTypeParameter || resolvedElement instanceof PyClass ||
            resolvedElement instanceof PyTypeAliasStatement) {
            return true;
        }
        else if (resolvedElement instanceof PyFunction function) {
            String qualifiedName = function.getQualifiedName();
            return PyTypingTypeProvider.OPAQUE_NAMES.contains(qualifiedName) ||
                qualifiedName != null && (qualifiedName.endsWith("ParamSpec.args") || qualifiedName.endsWith("ParamSpec.kwargs"));
        }
        else if (resolvedElement instanceof PsiFileSystemItem || resolvedElement instanceof PyParameter) {
            return false;
        }
        else {
            return true;
        }
    }

    /**
     * Checks that {@code resolved} is a member of a class that contains {@code reference}, and that {@code reference} is in a stub
     * or in an annotation of a file with {@code from __future__ import annotations}.
     * Then {@code typing.get_type_hints} and type checkers can find the name outside the class, for example a builtin {@code tuple}.
     */
    @RequiredReadAction
    private static boolean isClassMemberInDeferredAnnotation(PyReferenceExpression reference, PsiElement resolved) {
        if (!(ScopeUtil.getScopeOwner(resolved) instanceof PyClass owner)) {
            return false;
        }
        if (!PsiTreeUtil.isAncestor(owner, reference, true)) {
            return false;
        }
        if (PyiUtil.isInsideStub(reference)) {
            return true;
        }
        return reference.getContainingFile() instanceof PyFile pyFile && pyFile.hasImportFromFuture(FutureFeature.ANNOTATIONS) &&
            PsiTreeUtil.getParentOfType(reference, PyAnnotation.class) != null;
    }

    /**
     * Checks that {@code target} is surely a variable and not a type alias.
     * Its value has a declared type, is a plain expression such as a literal, or is the result of a call that creates no type.
     * A target of a loop, of a {@code with} statement, of an {@code except} clause or of a comprehension is also a variable.
     */
    @RequiredReadAction
    private static boolean isVariableOfNonTypeValue(PyTargetExpression target, TypeEvalContext context) {
        if (!target.hasAssignedValue()) {
            return target.getStub() == null && isLoopOrContextTarget(target);
        }
        if (target.getAnnotationValue() != null || target.getTypeCommentAnnotation() != null) {
            return true;
        }
        PyTargetExpressionStub stub = target.getStub();
        if (stub != null && stub.getInitializerType() == PyTargetExpressionStub.InitializerType.Custom) {
            return false;
        }
        if (PyTargetExpressionImpl.getAssignedReferenceQualifiedName(target) != null) {
            return false;
        }
        Ref<QualifiedName> calleeName = PyTargetExpressionImpl.getAssignedCallCalleeQualifiedName(target);
        if (calleeName == null) {
            return true;
        }
        QualifiedName calleeQName = calleeName.get();
        if (calleeQName == null) {
            return true;
        }
        if (!(PyUtil.createExpressionFromFragment(calleeQName.toString(), target) instanceof PyReferenceExpression callee)) {
            return false;
        }
        PyResolveContext resolveContext = PyResolveContext.defaultContext(context);
        PsiElement resolved = PyResolveUtil.resolveDeclaration(callee.getReference(resolveContext), resolveContext);
        if (resolved instanceof PyFunction function) {
            return !FUNCTIONAL_TYPE_FORMS.contains(function.getQualifiedName());
        }
        else if (resolved instanceof PyClass cls) {
            return createsPlainInstance(cls, context);
        }
        else {
            return false;
        }
    }

    @RequiredReadAction
    private static boolean isLoopOrContextTarget(PyTargetExpression target) {
        PsiElement element = target;
        while (element.getParent() instanceof PyTupleExpression ||
            element.getParent() instanceof PyParenthesizedExpression ||
            element.getParent() instanceof PyStarExpression) {
            element = element.getParent();
        }
        PsiElement parent = element.getParent();
        if (parent instanceof PyForPart forPart) {
            return forPart.getTarget() == element;
        }
        else if (parent instanceof PyWithItem withItem) {
            return withItem.getTarget() == element;
        }
        else if (parent instanceof PyExceptPart exceptPart) {
            return exceptPart.getTarget() == element;
        }
        else if (parent instanceof PyComprehensionElement comprehensionElement) {
            PsiElement finalElement = element;
            return ContainerUtil.or(comprehensionElement.getForComponents(), it -> it.<PyExpression>getIteratorVariable() == finalElement);
        }
        else {
            return false;
        }
    }

    /**
     * Checks that a call of {@code cls} surely creates an instance that is not a type.
     * A metaclass, a class with a custom metaclass and a class from {@code typing} or {@code typing_extensions} can create a type.
     */
    @RequiredReadAction
    private static boolean createsPlainInstance(PyClass cls, TypeEvalContext context) {
        String qName = cls.getQualifiedName();
        if (qName == null) {
            return false;
        }
        int dotIndex = qName.indexOf('.');
        if (TYPING_MODULES.contains(dotIndex >= 0 ? qName.substring(0, dotIndex) : qName)) {
            return false;
        }
        if (cls.isSubclass(PyNames.FQN.TYPE, context)) {
            return false;
        }
        PyClassLikeType metaClassType = cls.getMetaClassType(true, context);
        return metaClassType != null && PyNames.FQN.TYPE.equals(metaClassType.getClassQName());
    }

    private static final Set<String> TYPING_MODULES = Set.of(PyTypingTypeProvider.TYPING, "typing_extensions");

    private static final Set<String> FUNCTIONAL_TYPE_FORMS = Set.of(
        PyNames.FQN.NAMEDTUPLE, PyTypingTypeProvider.NAMEDTUPLE, PyTypingTypeProvider.NEW_TYPE, PyTypingTypeProvider.NEW_TYPE_EXT,
        PyTypingTypeProvider.TYPED_DICT, PyTypingTypeProvider.TYPED_DICT_EXT
    );

    // JB: kotlin.text.trimIndent
    private static String trimIndent(String text) {
        List<String> lines = Arrays.asList(text.split("\r\n|\n|\r", -1));
        int minCommonIndent = Integer.MAX_VALUE;
        for (String line : lines) {
            if (!line.isBlank()) {
                int indent = 0;
                while (indent < line.length() && Character.isWhitespace(line.charAt(indent))) {
                    indent++;
                }
                minCommonIndent = Math.min(minCommonIndent, indent);
            }
        }
        if (minCommonIndent == Integer.MAX_VALUE) {
            minCommonIndent = 0;
        }
        List<String> result = new ArrayList<>();
        int lastIndex = lines.size() - 1;
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            if ((index == 0 || index == lastIndex) && line.isBlank()) {
                continue;
            }
            result.add(line.length() > minCommonIndent ? line.substring(minCommonIndent) : "");
        }
        return String.join("\n", result);
    }
}
