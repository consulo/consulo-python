// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.codeInsight.typing.PyTypedDictTypeProvider;
import com.jetbrains.python.impl.codeInsight.typing.PyTypedDictTypeProvider.Helper.TypedDictFieldQualifier;
import com.jetbrains.python.impl.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.impl.documentation.PythonDocumentationProvider;
import com.jetbrains.python.impl.inspections.PyInspectionMessages.CodifiedParam;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import com.jetbrains.python.impl.psi.impl.PyEvaluator;
import com.jetbrains.python.impl.psi.impl.PySubscriptionExpressionImpl;
import com.jetbrains.python.impl.psi.types.PyTypeChecker;
import com.jetbrains.python.impl.psi.types.PyTypeParameterMapping;
import com.jetbrains.python.impl.psi.types.PyTypeUtil;
import com.jetbrains.python.impl.psi.types.PyTypedDictType;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyAnnotation;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyBoolLiteralExpression;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyDelStatement;
import com.jetbrains.python.psi.PyDictLiteralExpression;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyImportStatementBase;
import com.jetbrains.python.psi.PyKeyValueExpression;
import com.jetbrains.python.psi.PyKeywordArgument;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.PyParameterList;
import com.jetbrains.python.psi.PyParenthesizedExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PySequenceExpression;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PySubscriptionExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.PyTupleExpression;
import com.jetbrains.python.psi.PyTypeParameter;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyNeverType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeVarType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.ast.ASTNode;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.lang.Couple;
import consulo.util.lang.Pair;
import consulo.util.lang.ref.Ref;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static com.jetbrains.python.impl.codeInsight.typing.PyTypedDictTypeProvider.Helper.isTypingTypedDictInheritor;
import static com.jetbrains.python.impl.psi.types.PyTypedDictType.TYPED_DICT_CLOSED_PARAMETER;
import static com.jetbrains.python.impl.psi.types.PyTypedDictType.TYPED_DICT_EXTRA_ITEMS_PARAMETER;
import static com.jetbrains.python.impl.psi.types.PyTypedDictType.TYPED_DICT_TOTAL_PARAMETER;
import static com.jetbrains.python.impl.psi.types.PyTypeUtilKt.isUnknown;

@ExtensionImpl
public class PyTypedDictInspection extends PyInspection {
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameTypedDict();
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
        Visitor(ProblemsHolder holder, TypeEvalContext context) {
            super(holder, context);
        }

        @Override
        public void visitPySubscriptionExpression(PySubscriptionExpression node) {
            if (node.getParent() instanceof PyAnnotation) {
                // Do not check it in TypeHints
                return;
            }
            PyType operandType = myTypeEvalContext.getType(node.getOperand());
            if (!(operandType instanceof PyTypedDictType typedDictType)) {
                return;
            }

            PyExpression indexExpression = node.getIndexExpression();
            List<String> indexExpressionValueOptions =
                PySubscriptionExpressionImpl.getIndexExpressionPossibleValues(indexExpression, myTypeEvalContext, String.class);
            if (indexExpressionValueOptions.isEmpty()) {
                if (!typedDictType.isDefinition() && indexExpression != null) {
                    PyType indexType = myTypeEvalContext.getType(indexExpression);
                    PyBuiltinCache builtinCache = PyBuiltinCache.getInstance(node);
                    boolean isPlainStringType = indexType != null &&
                        PyTypeChecker.match(builtinCache.getStrType(), indexType, myTypeEvalContext);

                    if (!isPlainStringType) {
                        String keyList = typedDictType.fields(myTypeEvalContext).keySet().stream()
                            .map(it -> "'" + it + "'")
                            .collect(Collectors.joining(", "));
                        registerProblem(indexExpression,
                            PyLocalize.inspTypeddictTypeddictKeyMustBeStringLiteralExpectedOne(keyList).get());
                    }
                }
                return;
            }

            Map<String, PyTypedDictType.FieldTypeAndTotality> fields = typedDictType.fields(myTypeEvalContext);
            List<String> nonMatchingFields = indexExpressionValueOptions.stream().filter(it -> !fields.containsKey(it)).toList();
            if (!nonMatchingFields.isEmpty() && isUnknown(typedDictType.extraItemsType(myTypeEvalContext))) {
                PyInspectionMessages.ProblemMessage message;
                if (nonMatchingFields.size() == 1) {
                    message = PyInspectionMessages.problemMessage(
                        a -> PyLocalize.inspTypeddictTypeddictHasNoKey(a[0], a[1]), typedDictType.getName(), nonMatchingFields.get(0)
                    );
                }
                else {
                    String nonMatchingFieldList = nonMatchingFields.stream().map(it -> "'" + it + "'").collect(Collectors.joining(", "));
                    message = PyInspectionMessages.problemMessage(
                        a -> PyLocalize.inspTypeddictTypeddictHasNoKeys(a[0], a[1]), typedDictType.getName(), nonMatchingFieldList
                    );
                }
                registerProblem(indexExpression, message);
            }
        }

        @Override
        public void visitPyTargetExpression(PyTargetExpression node) {
            PyExpression value = node.findAssignedValue();
            if (value instanceof PyCallExpression call && call.getCallee() != null &&
                PyTypedDictTypeProvider.Helper.isTypedDict(call.getCallee(), myTypeEvalContext)) {
                PyExpression[] arguments = call.getArguments();
                PyExpression typedDictName = PyPsiUtils.flattenParens(arguments.length > 0 ? arguments[0] : null);
                if (typedDictName instanceof PyStringLiteralExpression stringLiteral &&
                    !Objects.equals(node.getName(), stringLiteral.getStringValue())) {
                    registerProblem(typedDictName, PyLocalize.inspTypeddictFirstArgumentHasToMatchVariableName().get());
                }
            }
        }

        @Override
        public void visitPyArgumentList(PyArgumentList node) {
            if (node.getParent() instanceof PyClass parentClass && isTypingTypedDictInheritor(parentClass, myTypeEvalContext)) {
                PyKeywordArgument closedArgument = null;
                PyKeywordArgument extraItemsArgument = null;
                for (PyExpression argument : node.getArguments()) {
                    PyType type = myTypeEvalContext.getType(argument);
                    if (!isValidSuperclass(argument, type)) {
                        registerProblem(argument, PyLocalize.inspTypeddictTypeddictCannotInheritFromNonTypeddictBaseClass().get());
                    }
                    if (argument instanceof PyKeywordArgument keywordArgument) {
                        String keyword = keywordArgument.getKeyword();
                        PyExpression valueExpression = keywordArgument.getValueExpression();

                        if (TYPED_DICT_TOTAL_PARAMETER.equals(keyword)) {
                            if (valueExpression != null) {
                                validateBooleanParameter(valueExpression, TYPED_DICT_TOTAL_PARAMETER);
                            }
                        }
                        else if (TYPED_DICT_EXTRA_ITEMS_PARAMETER.equals(keyword)) {
                            extraItemsArgument = keywordArgument;
                            if (valueExpression != null) {
                                checkValueIsAType(valueExpression, valueExpression.getText());
                            }
                        }
                        else if (TYPED_DICT_CLOSED_PARAMETER.equals(keyword)) {
                            closedArgument = keywordArgument;
                            if (valueExpression != null) {
                                validateBooleanParameter(valueExpression, TYPED_DICT_CLOSED_PARAMETER);
                            }
                        }
                        else if (!PyNames.METACLASS.equals(keyword)) {
                            registerProblem(argument, PyInspectionMessages.problemMessage(
                                a -> PyLocalize.inspTypeddictUnexpectedArgumentFor__init_subclass__OfTypeddict(a[0]), keyword
                            ));
                        }
                    }
                }
                if (closedArgument != null && extraItemsArgument != null) {
                    registerProblem(extraItemsArgument, PyLocalize.inspTypeddictClosedAndExtraItemsMutuallyExclusive().get());
                }
            }
            else {
                PyCallExpression callExpression = node.getCallExpression();
                if (callExpression != null) {
                    PyExpression callee = callExpression.getCallee();
                    if (callee != null && PyTypedDictTypeProvider.Helper.isTypedDict(callee, myTypeEvalContext)) {
                        PyExpression[] callArguments = callExpression.getArguments();
                        PyExpression argument1 = callArguments.length > 1 ? callArguments[1] : null;
                        PyExpression fields = PyPsiUtils.flattenParens(argument1);
                        if (!(fields instanceof PyDictLiteralExpression dictLiteral)) {
                            registerProblem(argument1, PyLocalize.inspTypeddictExpectedADictionaryLiteral().get());
                            return;
                        }

                        for (PyExpression it : dictLiteral.getElements()) {
                            if (!(it instanceof PyKeyValueExpression keyValue)) {
                                return;
                            }

                            PyExpression fieldValue = keyValue.getValue();
                            checkValueIsAType(fieldValue, fieldValue != null ? fieldValue.getText() : null);
                        }

                        PyExpression closedArgument = callExpression.getKeywordArgument(TYPED_DICT_CLOSED_PARAMETER);
                        if (closedArgument != null) {
                            validateBooleanParameter(closedArgument, TYPED_DICT_CLOSED_PARAMETER);
                        }

                        PyExpression extraItemsArgument = callExpression.getKeywordArgument(TYPED_DICT_EXTRA_ITEMS_PARAMETER);
                        if (extraItemsArgument != null) {
                            checkValueIsAType(extraItemsArgument, extraItemsArgument.getText());
                        }

                        if (closedArgument != null && extraItemsArgument != null) {
                            registerProblem(extraItemsArgument, PyLocalize.inspTypeddictClosedAndExtraItemsMutuallyExclusive().get());
                        }

                        PyExpression totalityArgument = callExpression.getKeywordArgument(TYPED_DICT_TOTAL_PARAMETER);
                        if (totalityArgument != null) {
                            validateBooleanParameter(totalityArgument, TYPED_DICT_TOTAL_PARAMETER);
                        }
                    }
                }
            }
        }

        private boolean isValidSuperclass(PyExpression argument, @Nullable PyType type) {
            return argument instanceof PyKeywordArgument ||
                type instanceof PyTypedDictType ||
                PyTypedDictTypeProvider.Helper.isTypedDict(argument, myTypeEvalContext) ||
                argument instanceof PySubscriptionExpression subscription &&
                    myTypeEvalContext.getType(subscription.getOperand()) instanceof PyClassLikeType classLikeType &&
                    PyTypingTypeProvider.GENERIC.equals(classLikeType.getClassQName());
        }

        @Override
        public void visitPyClass(PyClass node) {
            if (!isTypingTypedDictInheritor(node, myTypeEvalContext)) {
                return;
            }

            if (node.getMetaClassExpression() != null) {
                registerProblem(node.getMetaClassExpression().getParent(),
                    PyLocalize.inspTypeddictSpecifyingMetaclassNotAllowedInTypeddict().get());
            }

            PyTypedDictType classTypedDictType = PyTypedDictTypeProvider.Helper.getTypedDictTypeForResolvedElement(node, myTypeEvalContext);
            Boolean isClosed = classTypedDictType != null ? classTypedDictType.isClosed() : null;
            PyType extraItemsType = classTypedDictType != null ? classTypedDictType.extraItemsType(myTypeEvalContext) : null;

            PyArgumentList superClassExpressionList = node.getSuperClassExpressionList();
            PyKeywordArgument closedArgument =
                superClassExpressionList != null ? superClassExpressionList.getKeywordArgument(TYPED_DICT_CLOSED_PARAMETER) : null;
            PyKeywordArgument extraItemsArgument =
                superClassExpressionList != null ? superClassExpressionList.getKeywordArgument(TYPED_DICT_EXTRA_ITEMS_PARAMETER) : null;

            Map<String, List<PyTypedDictType.FieldTypeAndTotality>> allAncestorsFields = new LinkedHashMap<>();
            boolean isClosedAncestor = false;
            PyTypedDictType extraItemsAncestor = null;

            List<PyTypedDictType> typedDictAncestors = new ArrayList<>();
            for (PyClassLikeType ancestorType : node.getAncestorTypes(myTypeEvalContext)) {
                if (ancestorType instanceof PyTypedDictType typedDict) {
                    typedDictAncestors.add(typedDict);
                }
            }
            for (PyTypedDictType typedDict : typedDictAncestors) {
                if (Boolean.FALSE.equals(isClosed) && typedDict.isClosed()) {
                    registerProblem(closedArgument, PyLocalize.inspTypeddictClosedCannotReopenClosedSuperclass().get());
                }

                if (!isUnknown(typedDict.extraItemsType(myTypeEvalContext))) {
                    if (!isUnknown(extraItemsType) && !typedDict.extraItemsQualifiers(myTypeEvalContext).isReadOnly()) {
                        registerProblem(extraItemsArgument, PyLocalize.inspTypeddictIncompatibleExtraItemsOverride().get());
                    }

                    if (Boolean.FALSE.equals(isClosed)) {
                        registerProblem(closedArgument, PyLocalize.inspTypeddictCannotSetClosedFalseWithExtraItems().get());
                    }
                    else {
                        if (!typedDict.extraItemsQualifiers(myTypeEvalContext).isReadOnly()) {
                            registerProblem(closedArgument, PyLocalize.inspTypeddictClosedRequiresReadonlyExtraItems().get());
                        }
                    }
                }

                if (typedDict.isClosed() || typedDict.extraItemsType(myTypeEvalContext) == PyNeverType.NEVER) {
                    isClosedAncestor = true;
                }
                if (extraItemsAncestor == null && !isUnknown(typedDict.extraItemsType(myTypeEvalContext))) {
                    extraItemsAncestor = typedDict;
                }

                for (Map.Entry<String, PyTypedDictType.FieldTypeAndTotality> field : typedDict.fields(myTypeEvalContext).entrySet()) {
                    String key = field.getKey();
                    PyTypedDictType.FieldTypeAndTotality value = field.getValue();
                    List<PyTypedDictType.FieldTypeAndTotality> listOfFieldsForKey =
                        allAncestorsFields.computeIfAbsent(key, k -> new ArrayList<>());

                    if (!listOfFieldsForKey.isEmpty()) {
                        PyTypedDictType.FieldTypeAndTotality firstBaseField = listOfFieldsForKey.get(0);
                        verifyTypedDictFieldsCompatibilityForMerging(firstBaseField, value, key, superClassExpressionList);
                    }
                    listOfFieldsForKey.add(value);
                }
            }

            if (classTypedDictType != null && classTypedDictType.extraItemsType(myTypeEvalContext) != null) {
                if (Boolean.TRUE.equals(classTypedDictType.extraItemsQualifiers(myTypeEvalContext).isRequired()) &&
                    classTypedDictType.extraItemsQualifiers(myTypeEvalContext).getHasExplicitRequiredQualifier()) {
                    registerProblem(extraItemsArgument, PyLocalize.inspTypeddictExtraItemsCannotBeRequired().get());
                }
                else if (Boolean.FALSE.equals(classTypedDictType.extraItemsQualifiers(myTypeEvalContext).isRequired())) {
                    registerProblem(extraItemsArgument, PyLocalize.inspTypeddictExtraItemsCannotBeNotRequired().get());
                }
            }

            boolean finalIsClosedAncestor = isClosedAncestor;
            PyTypedDictType finalExtraItemsAncestor = extraItemsAncestor;
            node.processClassLevelDeclarations((element, state) -> {
                if (!(element instanceof PyTargetExpression target)) {
                    if (element instanceof PyTypeParameter) {
                        return true;
                    }
                    registerProblem(element,
                        PyLocalize.inspTypeddictInvalidStatementInTypeddictDefinitionExpectedFieldNameFieldType().get());
                    return true;
                }
                if (target.hasAssignedValue()) {
                    registerProblem(target.findAssignedValue(),
                        PyLocalize.inspTypeddictRightHandSideValuesAreNotSupportedInTypeddict().get());
                    return true;
                }

                if (allAncestorsFields.containsKey(target.getName())) {
                    List<PyTypedDictType.FieldTypeAndTotality> fieldsForKey = allAncestorsFields.get(target.getName());
                    PyTypedDictType.FieldTypeAndTotality classField =
                        classTypedDictType != null ? classTypedDictType.fields(myTypeEvalContext).get(target.getName()) : null;
                    if (fieldsForKey != null && classField != null) {
                        for (PyTypedDictType.FieldTypeAndTotality ancestorField : fieldsForKey) {
                            validateTypedDictFieldOverride(ancestorField, classField, target);
                        }
                    }
                }
                else {
                    if (finalIsClosedAncestor) {
                        registerProblem(target, PyLocalize.inspTypeddictClosedExtraKeyNotAllowed(node.getName(), target.getName()).get());
                    }
                    else if (finalExtraItemsAncestor != null) {
                        PyTypedDictType.FieldTypeAndTotality fieldInfo =
                            classTypedDictType != null ? classTypedDictType.fields(myTypeEvalContext).get(target.getName()) : null;
                        PyType fieldType = fieldInfo != null ? fieldInfo.getType() : null;

                        String expectedTypeName =
                            PythonDocumentationProvider.getTypeName(finalExtraItemsAncestor.extraItemsType(myTypeEvalContext), myTypeEvalContext);
                        String actualTypeName = PythonDocumentationProvider.getTypeName(fieldType, myTypeEvalContext);

                        if (finalExtraItemsAncestor.extraItemsQualifiers(myTypeEvalContext).isReadOnly()) {
                            if (!PyTypeChecker.match(finalExtraItemsAncestor.extraItemsType(myTypeEvalContext), fieldType, myTypeEvalContext)) {
                                registerProblem(
                                    target,
                                    PyInspectionMessages.problemMessage(
                                        a -> PyLocalize.inspTypeCheckerTypeNotAssignable(a[0], a[1]), actualTypeName, expectedTypeName
                                    )
                                );
                            }
                        }
                        else {
                            if (fieldInfo != null && Boolean.TRUE.equals(fieldInfo.getQualifiers().isRequired())) {
                                registerProblem(target,
                                    PyInspectionMessages.problemMessage(
                                        a -> PyLocalize.inspTypeddictRequiredKeyUnknownToSuperclass(a[0], a[1]),
                                        target.getName(),
                                        finalExtraItemsAncestor.getName()
                                    ));
                            }
                            else if (!PyTypeChecker.match(finalExtraItemsAncestor.extraItemsType(myTypeEvalContext), fieldType, myTypeEvalContext) ||
                                !PyTypeChecker.match(fieldType, finalExtraItemsAncestor.extraItemsType(myTypeEvalContext), myTypeEvalContext)) {
                                registerProblem(target,
                                    PyInspectionMessages.problemMessage(
                                        a -> PyLocalize.inspTypeCheckerExpectedTypeGotTypeInstead(a[0], a[1]),
                                        expectedTypeName, actualTypeName
                                    ));
                            }
                        }
                    }
                }
                PyAnnotation annotation = target.getAnnotation();
                checkValueIsAType(annotation != null ? annotation.getValue() : null, target.getAnnotationValue());
                return true;
            });
        }

        @Override
        public void visitPyDelStatement(PyDelStatement node) {
            for (PyExpression target : node.getTargets()) {
                for (PyExpression expr : PyUtil.flattenedParensAndTuples(target)) {
                    if (!(expr instanceof PySubscriptionExpression subscription)) {
                        continue;
                    }
                    PyType type = myTypeEvalContext.getType(subscription.getOperand());
                    if (type instanceof PyTypedDictType typedDictType) {
                        for (String index : PySubscriptionExpressionImpl.getIndexExpressionPossibleValues(subscription.getIndexExpression(),
                            myTypeEvalContext,
                            String.class)) {
                            PyTypedDictType.FieldTypeAndTotality field = typedDictType.fields(myTypeEvalContext).get(index);
                            if (field != null && Boolean.TRUE.equals(field.getQualifiers().isRequired())) {
                                registerProblem(subscription.getIndexExpression(), PyInspectionMessages.problemMessage(
                                    a -> PyLocalize.inspTypeddictKeyCannotBeDeleted(a[0], a[1]), index, typedDictType.getName()
                                ));
                            }
                        }
                    }
                }
            }
        }

        @Override
        public void visitPyParameterList(PyParameterList node) {
            PyParameter[] parameters = node.getParameters();
            if (parameters.length == 0) {
                return;
            }

            PyNamedParameter kwContainer = parameters[parameters.length - 1].getAsNamed();
            if (kwContainer == null || !kwContainer.isKeywordContainer()) {
                return;
            }
            PyType kwContainerType = myTypeEvalContext.getType(kwContainer);

            if (kwContainerType instanceof PyTypedDictType typedDictType) {
                Set<String> fieldNames = typedDictType.fields(myTypeEvalContext).keySet();
                String typedDictName = typedDictType.getName();
                for (PyParameter parameter : parameters) {
                    PyNamedParameter named = parameter.getAsNamed();
                    if (named == null) {
                        continue;
                    }
                    if (!named.isPositionalOnly() && fieldNames.contains(named.getName())) {
                        registerProblem(named, PyInspectionMessages.problemMessage(
                            a -> PyLocalize.inspTypeddictParameterOverlapsWithTypedDict(a[0], a[1]), named.getName(), typedDictName
                        ));
                    }
                }
            }
        }

        @Override
        public void visitPyCallExpression(PyCallExpression node) {
            if (!(node.getCallee() instanceof PyReferenceExpression callee) || callee.getQualifier() == null) {
                return;
            }

            PyType nodeType = myTypeEvalContext.getType(callee.getQualifier());
            if (!(nodeType instanceof PyTypedDictType typedDictType)) {
                return;
            }
            PyExpression[] arguments = node.getArguments();

            if (PyNames.UPDATE.equals(callee.getName())) {
                if (arguments.length == 1 && arguments[0] instanceof PyReferenceExpression) {
                    if (PyUtil.resolveToTheTop(arguments[0]) instanceof PyTargetExpression resolvedArg) {
                        PyExpression assignedValue = resolvedArg.findAssignedValue();
                        if (assignedValue != null) {
                            arguments = new PyExpression[]{assignedValue};
                        }
                    }
                }
                if (arguments.length == 1 && arguments[0] instanceof PySequenceExpression sequenceExpression) {
                    arguments = sequenceExpression.getElements();
                }
                inspectUpdateSequenceArgument(node, arguments, typedDictType);
            }

            if (PyNames.CLEAR.equals(callee.getName()) || PyNames.POPITEM.equals(callee.getName())) {
                // PEP 728: "In this case, methods that are previously unavailable on a TypedDict are allowed,
                // with signatures matching dict[str, VT]" — when the TypedDict is assignable to dict[str, VT],
                // i.e. all items (including extra_items) are non-required, non-read-only, and consistent with VT.
                boolean isAssignableToMutableDict = !isUnknown(typedDictType.extraItemsType(myTypeEvalContext)) &&
                    !typedDictType.extraItemsQualifiers(myTypeEvalContext).isReadOnly() &&
                    typedDictType.fields(myTypeEvalContext).values().stream()
                        .noneMatch(it -> Boolean.TRUE.equals(it.getQualifiers().isRequired()) || it.getQualifiers().isReadOnly());
                if (!isAssignableToMutableDict) {
                    ASTNode nameElement = callee.getNameElement();
                    registerProblem(nameElement != null ? nameElement.getPsi() : null,
                        PyLocalize.inspTypeddictThisOperationMightBreakTypeddictConsistency().get(),
                        ProblemHighlightType.WARNING);
                }
            }

            if (PyNames.POP.equals(callee.getName())) {
                String key = arguments.length > 0 ? PyEvaluator.evaluate(arguments[0], String.class) : null;
                PyTypedDictType.FieldTypeAndTotality field = key != null ? typedDictType.fields(myTypeEvalContext).get(key) : null;
                if (field != null && Boolean.TRUE.equals(field.getQualifiers().isRequired())) {
                    ASTNode nameElement = callee.getNameElement();
                    registerProblem(nameElement != null ? nameElement.getPsi() : null, PyInspectionMessages.problemMessage(
                        a -> PyLocalize.inspTypeddictKeyCannotBeDeleted(a[0], a[1]), key, typedDictType.getName()
                    ));
                }
            }

            if (PyNames.SETDEFAULT.equals(callee.getName())) {
                String key = arguments.length > 0 ? PyEvaluator.evaluate(arguments[0], String.class) : null;
                PyTypedDictType.FieldTypeAndTotality field = key != null ? typedDictType.fields(myTypeEvalContext).get(key) : null;
                if (field != null && Boolean.FALSE.equals(field.getQualifiers().isRequired())) {
                    if (node.getArguments().length > 1) {
                        PyType valueType = myTypeEvalContext.getType(arguments[1]);
                        if (!PyTypeChecker.match(field.getType(), valueType, myTypeEvalContext)) {
                            String expectedTypeName = PythonDocumentationProvider.getTypeName(field.getType(), myTypeEvalContext);
                            String actualTypeName = PythonDocumentationProvider.getTypeName(valueType, myTypeEvalContext);
                            registerProblem(arguments[1],
                                PyInspectionMessages.problemMessage(
                                    a -> PyLocalize.inspTypeCheckerExpectedTypeGotTypeInstead(a[0], a[1]),
                                    expectedTypeName, actualTypeName
                                ));
                        }
                    }
                }
            }

            if (PyTypedDictTypeProvider.Helper.isGetMethodToOverride(node, myTypeEvalContext)) {
                PyExpression keyArgument = node.getArgument(0, "key", PyExpression.class);
                if (keyArgument == null) {
                    return;
                }
                String key = PyEvaluator.evaluate(keyArgument, String.class);
                if (key == null) {
                    registerProblem(keyArgument, PyLocalize.inspTypeddictKeyShouldBeString().get());
                    return;
                }
                if (!typedDictType.fields(myTypeEvalContext).containsKey(key) && isUnknown(typedDictType.extraItemsType(myTypeEvalContext))) {
                    registerProblem(keyArgument, PyInspectionMessages.problemMessage(
                        a -> PyLocalize.inspTypeddictTypeddictHasNoKey(a[0], a[1]), typedDictType.getName(), key
                    ));
                }
            }
        }

        @Override
        public void visitPyAssignmentStatement(PyAssignmentStatement node) {
            List<Pair<PyExpression, PyExpression>> targetsToValuesMapping = node.getTargetsToValuesMapping();
            for (PyExpression target : node.getTargets()) {
                if (!(target instanceof PySubscriptionExpression subscription)) {
                    continue;
                }
                PyType targetType = myTypeEvalContext.getType(subscription.getOperand());
                if (!(targetType instanceof PyTypedDictType typedDictType)) {
                    continue;
                }
                for (String indexString : PySubscriptionExpressionImpl.getIndexExpressionPossibleValues(subscription.getIndexExpression(),
                    myTypeEvalContext,
                    String.class)) {
                    PyTypedDictType.FieldTypeAndTotality field = typedDictType.fields(myTypeEvalContext).get(indexString);
                    if (field != null && field.getQualifiers().isReadOnly()) {
                        registerProblem(target, PyLocalize.inspTypeddictTypeddictFieldIsReadonly(indexString).get());
                    }
                    else if (field == null && typedDictType.extraItemsQualifiers(myTypeEvalContext).isReadOnly()) {
                        registerProblem(target, PyLocalize.inspTypeddictTypeddictFieldIsReadonly(indexString).get());
                    }

                    PyType expected = typedDictType.getElementType(indexString, myTypeEvalContext);
                    List<PyExpression> actualExpressions = new ArrayList<>();
                    for (Pair<PyExpression, PyExpression> pair : targetsToValuesMapping) {
                        if (pair.getFirst() == target) {
                            actualExpressions.add(pair.getSecond());
                        }
                    }
                    for (PyExpression actual : actualExpressions) {
                        PyType actualType = myTypeEvalContext.getType(actual);
                        if (!PyTypeChecker.match(expected, actualType, myTypeEvalContext)) {
                            String expectedTypeName = PythonDocumentationProvider.getTypeName(expected, myTypeEvalContext);
                            String actualTypeName = PythonDocumentationProvider.getTypeName(actualType, myTypeEvalContext);
                            registerProblem(actual,
                                PyInspectionMessages.problemMessage(
                                    a -> PyLocalize.inspTypeCheckerExpectedTypeGotTypeInstead(a[0], a[1]),
                                    expectedTypeName, actualTypeName
                                ));
                        }
                    }
                }
            }

            for (Pair<PyExpression, PyExpression> pair : node.getTargetsToValuesMapping()) {
                PyExpression target = pair.getFirst();
                PyExpression value = pair.getSecond();
                PyType targetTypeRaw = myTypeEvalContext.getType(target);
                if (!(targetTypeRaw instanceof PyTypedDictType targetType)) {
                    continue;
                }

                if (!(myTypeEvalContext.getType(value) instanceof PyTypedDictType assignedType)) {
                    continue;
                }

                PyType expectedExtraItemsType = targetType.extraItemsType(myTypeEvalContext);
                if (isUnknown(expectedExtraItemsType)) {
                    continue;
                }

                if (!isUnknown(assignedType.extraItemsType(myTypeEvalContext))) {
                    if (!PyTypeChecker.match(expectedExtraItemsType, assignedType.extraItemsType(myTypeEvalContext), myTypeEvalContext)) {
                        registerProblem(
                            target,
                            PyInspectionMessages.problemMessage(
                                a -> PyLocalize.inspTypeddictTypeNotAssignableToExtraItems(a[0], a[1]),
                                CodifiedParam.ofType(assignedType.extraItemsType(myTypeEvalContext), target, myTypeEvalContext),
                                CodifiedParam.ofType(expectedExtraItemsType, target, myTypeEvalContext)
                            )
                        );
                    }
                }
                else {
                    boolean expectedIsReadOnly = targetType.extraItemsQualifiers(myTypeEvalContext).isReadOnly();
                    if (!expectedIsReadOnly) {
                        registerProblem(
                            target,
                            PyInspectionMessages.problemMessage(
                                a -> PyLocalize.inspTypeddictImplicitReadonlyExtraItemsIncompatible(a[0], a[1]),
                                assignedType.getName(),
                                CodifiedParam.ofType(targetType.extraItemsType(myTypeEvalContext), target, myTypeEvalContext)
                            )
                        );
                    }
                }

                Map<String, PyTypedDictType.FieldTypeAndTotality> targetFields = targetType.fields(myTypeEvalContext);
                Map<String, PyTypedDictType.FieldTypeAndTotality> newAttributes = new LinkedHashMap<>();
                for (Map.Entry<String, PyTypedDictType.FieldTypeAndTotality> entry : assignedType.fields(myTypeEvalContext).entrySet()) {
                    if (!targetFields.containsKey(entry.getKey())) {
                        newAttributes.put(entry.getKey(), entry.getValue());
                    }
                }
                for (Map.Entry<String, PyTypedDictType.FieldTypeAndTotality> entry : newAttributes.entrySet()) {
                    String key = entry.getKey();
                    PyTypedDictType.FieldTypeAndTotality newField = entry.getValue();
                    PyTypedDictType.TypedDictFieldQualifiers extraItemsQualifiers = targetType.extraItemsQualifiers(myTypeEvalContext);
                    PyType newFieldType = assignedType.getElementType(key, myTypeEvalContext);

                    if (!extraItemsQualifiers.isReadOnly() && Boolean.TRUE.equals(newField.getQualifiers().isRequired())) {
                        registerProblem(
                            target,
                            PyInspectionMessages.problemMessage(
                                a -> PyLocalize.inspTypeddictFieldNotRequiredInTargetButRequiredInSource(a[0], a[1], a[2]),
                                key,
                                targetType.getName(),
                                assignedType.getName()
                            )
                        );
                    }

                    String expectedTypeName = PythonDocumentationProvider.getTypeName(expectedExtraItemsType, myTypeEvalContext);
                    String actualTypeName = PythonDocumentationProvider.getTypeName(newFieldType, myTypeEvalContext);

                    if (Boolean.TRUE.equals(extraItemsQualifiers.isRequired()) && !extraItemsQualifiers.isReadOnly()) {
                        if (!PyTypeChecker.match(expectedExtraItemsType, newFieldType, myTypeEvalContext) ||
                            !PyTypeChecker.match(newFieldType, expectedExtraItemsType, myTypeEvalContext)) {
                            registerProblem(
                                target,
                                PyInspectionMessages.problemMessage(
                                    a -> PyLocalize.inspTypeCheckerExpectedTypeGotTypeInstead(a[0], a[1]),
                                    expectedTypeName,
                                    actualTypeName
                                )
                            );
                        }
                    }
                    else {
                        if (!PyTypeChecker.match(expectedExtraItemsType, newFieldType, myTypeEvalContext)) {
                            registerProblem(
                                target,
                                PyInspectionMessages.problemMessage(
                                    a -> PyLocalize.inspTypeCheckerTypeNotAssignable(a[0], a[1]), actualTypeName, expectedTypeName
                                ));
                        }
                    }
                }
            }
        }

        public boolean isTypeDictQualifier(PyReferenceExpression node) {
            return PyTypingTypeProvider.resolveToQualifiedNames(node, myTypeEvalContext).stream()
                .anyMatch(it -> PyTypingTypeProvider.TYPE_DICT_QUALIFIERS.contains(it));
        }

        @Override
        public void visitPyReferenceExpression(PyReferenceExpression node) {
            if (PsiTreeUtil.getParentOfType(node, PyImportStatementBase.class) == null) {
                boolean isTypeDictQualifier = isTypeDictQualifier(node);
                if (isTypeDictQualifier) {
                    String qualifierName = node.getName();
                    PyClass classParent = PsiTreeUtil.getParentOfType(node, PyClass.class);
                    PyCallExpression callParent = PsiTreeUtil.getParentOfType(node, PyCallExpression.class);
                    if (classParent == null) {
                        if (callParent == null) {
                            registerProblem(node, PyInspectionMessages.problemMessage(
                                a -> PyLocalize.inspTypeddictQualifiersCannotBeUsedOutsideTypeddictDefinition(a[0]), qualifierName
                            ));
                        }
                        else {
                            PyExpression callParentCallee = callParent.getCallee();
                            if (callParentCallee != null &&
                                PyTypingTypeProvider.resolveToQualifiedNames(callParentCallee, myTypeEvalContext).stream()
                                    .noneMatch(qualifiedName -> PyTypingTypeProvider.TYPED_DICT.equals(qualifiedName) ||
                                        PyTypingTypeProvider.TYPED_DICT_EXT.equals(qualifiedName))) {
                                registerProblem(node, PyInspectionMessages.problemMessage(
                                    a -> PyLocalize.inspTypeddictQualifiersCannotBeUsedOutsideTypeddictDefinition(a[0]), qualifierName
                                ));
                            }
                        }
                    }
                    else {
                        if (!isTypingTypedDictInheritor(classParent, myTypeEvalContext)) {
                            registerProblem(node, PyInspectionMessages.problemMessage(
                                a -> PyLocalize.inspTypeddictQualifiersCannotBeUsedOutsideTypeddictDefinition(a[0]), qualifierName
                            ));
                        }
                    }

                    if (node.getParent() instanceof PySubscriptionExpression parentSubscription &&
                        parentSubscription.getIndexExpression() instanceof PyTupleExpression) {
                        registerProblem(parentSubscription.getIndexExpression(), PyInspectionMessages.problemMessage(
                            a -> PyLocalize.inspTypeddictRequiredNotrequiredMustHaveExactlyOneTypeArgument(a[0]), qualifierName
                        ));
                    }
                }
            }
        }

        /**
         * Checks that {@code expression} with {@code strType} name is a type
         */
        private void checkValueIsAType(@Nullable PyExpression expression, @Nullable String strType) {
            if (expression == null || strType == null) {
                registerProblem(expression, PyLocalize.inspTypeddictValueMustBeType().get(), ProblemHighlightType.WEAK_WARNING);
                return;
            }
            if (expression instanceof PySubscriptionExpression subscription && subscription.getOperand() instanceof PyReferenceExpression) {
                List<TypedDictFieldQualifier> qualifiers =
                    PyTypedDictTypeProvider.Helper.getTypedDictFieldQualifiers(subscription, myTypeEvalContext);
                if (qualifiers.stream().filter(it -> it == TypedDictFieldQualifier.REQUIRED || it == TypedDictFieldQualifier.NOT_REQUIRED)
                    .count() > 1) {
                    registerProblem(expression, PyLocalize.inspTypeddictRequiredAndNotRequiredCannotBeNested().get());
                }
                if (qualifiers.stream().filter(it -> it == TypedDictFieldQualifier.READ_ONLY).count() > 1) {
                    registerProblem(expression, PyLocalize.inspTypeddictReadOnlyCannotBeNested().get());
                }
                return;
            }
            Ref<PyType> typeRef = expression instanceof PyReferenceExpression
                ? PyTypingTypeProvider.getType(expression, myTypeEvalContext)
                : PyTypingTypeProvider.getStringBasedType(strType, expression, myTypeEvalContext);
            PyType type = typeRef != null ? typeRef.get() : null;
            if (type == null && PyTypingTypeProvider.resolveToQualifiedNames(expression, myTypeEvalContext).stream()
                .noneMatch(qualifiedName -> PyTypingTypeProvider.ANY.equals(qualifiedName))) {
                registerProblem(expression, PyLocalize.inspTypeddictValueMustBeType().get(), ProblemHighlightType.WEAK_WARNING);
            }
        }

        private void validateBooleanParameter(PyExpression parameterValue, String parameterName) {
            if (LanguageLevel.forElement(parameterValue.getOriginalElement()).isPy3K() && !(parameterValue instanceof PyBoolLiteralExpression) ||
                !List.of(PyNames.TRUE, PyNames.FALSE).contains(parameterValue.getText())) {
                registerProblem(
                    parameterValue,
                    PyInspectionMessages.problemMessage(a -> PyLocalize.inspTypeddictParameterMustBeBoolean(a[0]), parameterName)
                );
            }
        }

        private boolean verifyTypedDictFieldsCompatibilityForMerging(PyTypedDictType.FieldTypeAndTotality firstBaseField,
                                                                     PyTypedDictType.FieldTypeAndTotality secondBaseField,
                                                                     @Nullable String fieldName,
                                                                     @Nullable PyArgumentList errorElement) {
            if (firstBaseField.isReadOnly() != secondBaseField.isReadOnly() ||
                !areFieldTypesCompatible(firstBaseField, secondBaseField)) {
                if (errorElement != null) {
                    registerProblem(
                        errorElement,
                        PyInspectionMessages.problemMessage(
                            a -> PyLocalize.inspTypeddictBaseClassesDefineFieldIncompatibly(a[0]),
                            fieldName
                        )
                    );
                }
                return false;
            }

            if (!firstBaseField.isRequired() && secondBaseField.isRequired()) {
                if (errorElement != null) {
                    registerProblem(
                        errorElement,
                        PyLocalize.inspTypeddictItemCannotRedefineAsNotRequired(fieldName).get()
                    );
                }
                return false;
            }

            return true;
        }

        private boolean areFieldTypesCompatible(PyTypedDictType.FieldTypeAndTotality first,
                                                PyTypedDictType.FieldTypeAndTotality second) {
            boolean bothReadOnly = first.isReadOnly() && second.isReadOnly();

            if (bothReadOnly) {
                return PyTypeChecker.match(second.getType(), first.getType(), myTypeEvalContext);
            }
            else {
                return PyTypeUtil.isSameType(first.getType(), second.getType(), myTypeEvalContext);
            }
        }

        private boolean validateTypedDictFieldOverride(PyTypedDictType.FieldTypeAndTotality expected,
                                                       PyTypedDictType.FieldTypeAndTotality actual,
                                                       @Nullable PyTargetExpression fieldElement) {
            boolean expectedIsReadOnly = expected.getQualifiers().isReadOnly();
            boolean actualIsReadOnly = actual.getQualifiers().isReadOnly();
            Boolean expectedIsRequired = expected.getQualifiers().isRequired();
            Boolean actualIsRequired = actual.getQualifiers().isRequired();

            if (!expectedIsReadOnly && Boolean.TRUE.equals(expectedIsRequired) && actualIsReadOnly) {
                if (fieldElement != null) {
                    registerProblem(
                        fieldElement,
                        PyInspectionMessages.problemMessage(
                            a -> PyLocalize.inspTypeddictFieldCannotOverrideMutableRequiredAsReadonly(a[0]), fieldElement.getName()
                        )
                    );
                }
                return false;
            }

            if (!expectedIsReadOnly && Boolean.TRUE.equals(expectedIsRequired) && Boolean.FALSE.equals(actualIsRequired)) {
                if (fieldElement != null) {
                    registerProblem(
                        fieldElement,
                        PyInspectionMessages.problemMessage(
                            a -> PyLocalize.inspTypeddictFieldCannotOverrideMutableRequiredAsNotrequired(a[0]), fieldElement.getName()
                        )
                    );
                }
                return false;
            }

            if (expectedIsReadOnly && Boolean.TRUE.equals(expectedIsRequired) && Boolean.FALSE.equals(actualIsRequired)) {
                if (fieldElement != null) {
                    registerProblem(
                        fieldElement,
                        PyInspectionMessages.problemMessage(
                            a -> PyLocalize.inspTypeddictFieldCannotOverrideReadonlyRequiredAsNotrequired(a[0]), fieldElement.getName()
                        )
                    );
                }
                return false;
            }

            if (!areTypedDictFieldTypesCompatible(expected, actual)) {
                if (fieldElement != null) {
                    registerProblem(
                        fieldElement,
                        PyInspectionMessages.problemMessage(
                            a -> PyLocalize.inspTypeddictFieldTypeMismatch(a[0], a[1]),
                            CodifiedParam.ofType(actual.getType(), fieldElement, myTypeEvalContext),
                            CodifiedParam.ofType(expected.getType(), fieldElement, myTypeEvalContext)
                        )
                    );
                }
                return false;
            }

            return true;
        }

        private boolean areTypedDictFieldTypesCompatible(PyTypedDictType.FieldTypeAndTotality expected,
                                                         PyTypedDictType.FieldTypeAndTotality actual) {
            PyType expectedType = expected.getType();
            PyType actualType = actual.getType();

            if (expectedType instanceof PyClassType expectedClassType && expectedClassType.isParameterized() &&
                actualType instanceof PyClassType actualClassType && actualClassType.isParameterized()) {
                PyClassType expectedGenericDef = PyTypeChecker.findGenericDefinitionType(expectedClassType.getPyClass(), myTypeEvalContext);
                if (!PyTypeChecker.match(expectedGenericDef, actualType, myTypeEvalContext)) {
                    return false;
                }
                //todo: remove when proper variance-aware inference is supported
                if (expectedGenericDef != null) {
                    List<PyType> expectedGenericParams = expectedGenericDef.getTypeArguments();
                    PyTypeParameterMapping expectedMapping =
                        PyTypeParameterMapping.mapByShape(expectedGenericParams, expectedClassType.getTypeArguments());
                    PyTypeParameterMapping actualMapping =
                        PyTypeParameterMapping.mapByShape(expectedGenericParams, actualClassType.getTypeArguments());
                    if (expectedMapping != null && actualMapping != null) {
                        Map<PyType, PyType> l = new LinkedHashMap<>();
                        for (Couple<PyType> couple : expectedMapping.getMappedTypes()) {
                            l.put(couple.getFirst(), couple.getSecond());
                        }
                        Map<PyType, PyType> r = new LinkedHashMap<>();
                        for (Couple<PyType> couple : actualMapping.getMappedTypes()) {
                            r.put(couple.getFirst(), couple.getSecond());
                        }

                        Collection<PyType> commonKeys = new ArrayList<>(l.keySet());
                        commonKeys.retainAll(r.keySet());
                        for (PyType typeVar : commonKeys) {
                            PyType first = l.get(typeVar);
                            PyType second = r.get(typeVar);
                            if (typeVar instanceof PyTypeVarType typeVarType) {
                                switch (typeVarType.getVariance()) {
                                    case INVARIANT -> {
                                        if (!PyTypeUtil.isSameType(first, second, myTypeEvalContext)) {
                                            return false;
                                        }
                                    }
                                    case COVARIANT -> {
                                        if (!PyTypeChecker.match(first, second, myTypeEvalContext)) {
                                            return false;
                                        }
                                    }
                                    case CONTRAVARIANT -> {
                                        if (!PyTypeChecker.match(second, first, myTypeEvalContext)) {
                                            return false;
                                        }
                                    }
                                    default -> {
                                        // Variance inference is not yet supported
                                    }
                                }
                            }
                        }
                    }
                }
            }
            return PyTypeChecker.match(expectedType, actualType, myTypeEvalContext);
        }

        private void inspectUpdateSequenceArgument(PyCallExpression updateCall,
                                                   PyExpression[] sequenceElements,
                                                   PyTypedDictType typedDictType) {
            for (PyExpression it : sequenceElements) {
                PsiElement key = null;
                String keyAsString = null;
                PyExpression value = null;

                if (it instanceof PyKeyValueExpression keyValue && keyValue.getKey() instanceof PyStringLiteralExpression keyLiteral) {
                    key = keyValue.getKey();
                    keyAsString = keyLiteral.getStringValue();
                    value = keyValue.getValue();
                }
                else if (it instanceof PyParenthesizedExpression) {
                    PyExpression expression = PyPsiUtils.flattenParens(it);
                    if (expression == null) {
                        continue;
                    }

                    if (expression instanceof PyTupleExpression tuple && tuple.getElements().length == 2 &&
                        tuple.getElements()[0] instanceof PyStringLiteralExpression firstLiteral) {
                        key = tuple.getElements()[0];
                        keyAsString = firstLiteral.getStringValue();
                        value = tuple.getElements()[1];
                    }
                }
                else if (it instanceof PyKeywordArgument keywordArgument && keywordArgument.getValueExpression() != null) {
                    ASTNode keywordNode = keywordArgument.getKeywordNode();
                    key = keywordNode != null ? keywordNode.getPsi() : null;
                    keyAsString = keywordArgument.getKeyword();
                    value = keywordArgument.getValueExpression();
                }
                else {
                    continue;
                }

                Map<String, PyTypedDictType.FieldTypeAndTotality> fields = typedDictType.fields(myTypeEvalContext);
                if (value == null) {
                    continue;
                }
                if (keyAsString == null) {
                    registerProblem(key, PyLocalize.inspTypeddictCannotAddNonStringKeyToTypeddict(typedDictType.getName()).get());
                    continue;
                }
                if (!fields.containsKey(keyAsString)) {
                    String finalKeyAsString = keyAsString;
                    registerProblem(key, PyInspectionMessages.problemMessage(
                        a -> PyLocalize.inspTypeddictTypeddictCannotHaveKey(a[0], a[1]), typedDictType.getName(), finalKeyAsString
                    ));
                    continue;
                }
                if (fields.get(keyAsString).getQualifiers().isReadOnly()) {
                    PsiElement warningHolder = updateCall;
                    if (updateCall.getCallee() instanceof PyReferenceExpression calleeReference) {
                        ASTNode nameElement = calleeReference.getNameElement();
                        if (nameElement != null && nameElement.getPsi() != null) {
                            warningHolder = nameElement.getPsi();
                        }
                    }
                    registerProblem(warningHolder, PyLocalize.inspTypeddictTypeddictFieldIsReadonly(keyAsString).get());
                }
                PyType valueType = myTypeEvalContext.getType(value);
                if (!PyTypeChecker.match(fields.get(keyAsString).getType(), valueType, myTypeEvalContext)) {
                    String expectedTypeName = PythonDocumentationProvider.getTypeName(fields.get(keyAsString).getType(), myTypeEvalContext);
                    String actualTypeName = PythonDocumentationProvider.getTypeName(valueType, myTypeEvalContext);
                    registerProblem(value, PyInspectionMessages.problemMessage(
                        a -> PyLocalize.inspTypeCheckerExpectedTypeGotTypeInstead(a[0], a[1]),
                        expectedTypeName, actualTypeName
                    ));
                }
            }
        }
    }
}
