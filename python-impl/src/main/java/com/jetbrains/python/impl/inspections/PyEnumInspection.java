// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.codeInsight.stdlib.PyStdlibTypeProvider;
import com.jetbrains.python.impl.codeInsight.stdlib.PyStdlibTypeProvider.EnumAttributeInfo;
import com.jetbrains.python.impl.codeInsight.stdlib.PyStdlibTypeProvider.EnumAttributeKind;
import com.jetbrains.python.impl.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.impl.inspections.PyInspectionMessages.CodifiedParam;
import com.jetbrains.python.impl.psi.PyKnownDecorator;
import com.jetbrains.python.impl.psi.PyKnownDecoratorUtil;
import com.jetbrains.python.impl.psi.impl.PyClassImpl;
import com.jetbrains.python.impl.psi.types.PyLiteralType;
import com.jetbrains.python.impl.psi.types.PyTypeChecker;
import com.jetbrains.python.impl.pyi.PyiUtil;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyNumericLiteralExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.PyTupleExpression;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiNameIdentifierOwner;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.lang.Pair;
import consulo.util.lang.ref.Ref;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ExtensionImpl
public class PyEnumInspection extends PyInspection {
    private static final Set<String> SUNDER_NAMES_311 = Set.of(
        "_order_", "_generate_next_value_", "_numeric_repr_", "_missing_", "_ignore_",
        "_iter_member_", "_iter_member_by_value_", "_iter_member_by_def_"
    );

    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameEnum();
    }

    @Override
    public LocalizeValue getGroupDisplayName() {
        return PyLocalize.inspGroupPythonClassStructure();
    }

    @Override
    public PsiElementVisitor buildVisitor(ProblemsHolder holder,
                                          boolean isOnTheFly,
                                          LocalInspectionToolSession session,
                                          Object state) {
        return new PyInspectionVisitor(holder, PyInspectionVisitor.getContext(session)) {
            @Override
            public void visitPyClass(PyClass node) {
                validateSuperClasses(node);
                if (!PyStdlibTypeProvider.isCustomEnum(node, myTypeEvalContext)) {
                    return;
                }

                // Every check below reads the kind of each attribute, so read it once
                List<Pair<PyTargetExpression, EnumAttributeInfo>> attributes = new ArrayList<>();
                for (PyTargetExpression attribute : node.getClassAttributes()) {
                    EnumAttributeInfo info = PyStdlibTypeProvider.getEnumAttributeInfo(node, attribute, myTypeEvalContext);
                    if (info != null) {
                        attributes.add(Pair.create(attribute, info));
                    }
                }
                validateEnumMembers(node, attributes);
                // A stub declares an enum but does not run its class body, so a definition error cannot occur there
                if (PyiUtil.isInsideStub(node)) {
                    return;
                }
                validateEnumBaseOrder(node);
                validateSunderNames(node);
                validateUniqueValues(node, attributes);
            }

            private void validateSuperClasses(PyClass node) {
                for (PyExpression superClassExpression : PyClassImpl.getUnfoldedSuperClassExpressions(node)) {
                    PyType superClassType = myTypeEvalContext.getType(superClassExpression);
                    if (superClassType instanceof PyClassType classType) {
                        PyClass superClass = classType.getPyClass();
                        if (PyStdlibTypeProvider.isFinalEnum(superClass, myTypeEvalContext)) {
                            registerProblem(superClassExpression,
                                            PyInspectionMessages.problemMessage(
                                                msgArgs -> PyLocalize.inspEnumEnumClassIsFinalAndCannotBeSubclassed(msgArgs[0]),
                                                CodifiedParam.ofReference(superClass)),
                                            ProblemHighlightType.GENERIC_ERROR);
                        }
                    }
                }
            }

            /**
             * The enum base must be the last base class, as in {@code class E(Mixin, Enum)}.
             */
            private void validateEnumBaseOrder(PyClass pyClass) {
                List<PyExpression> superClassExpressions = PyClassImpl.getUnfoldedSuperClassExpressions(pyClass);
                if (superClassExpressions.isEmpty()) {
                    return;
                }
                PyClass lastBaseClass = getBaseClass(superClassExpressions.get(superClassExpressions.size() - 1));
                if (lastBaseClass == null) {
                    return;
                }
                // The enum machinery accepts any enum as the last base. Another order of two enums breaks the MRO instead.
                if (PyStdlibTypeProvider.isEnum(lastBaseClass, myTypeEvalContext)) {
                    return;
                }

                for (int i = superClassExpressions.size() - 1; i >= 0; i--) {
                    PyExpression base = superClassExpressions.get(i);
                    PyClass baseClass = getBaseClass(base);
                    if (baseClass == null) {
                        continue;
                    }
                    if (!PyStdlibTypeProvider.isEnum(baseClass, myTypeEvalContext)) {
                        continue;
                    }
                    registerProblem(base,
                                    PyInspectionMessages.problemMessage(
                                        msgArgs -> PyLocalize.inspEnumEnumBaseClassMustBeLast(msgArgs[0]),
                                        CodifiedParam.ofReference(baseClass)),
                                    ProblemHighlightType.GENERIC_ERROR);
                    return;
                }
            }

            private @Nullable PyClass getBaseClass(PyExpression superClassExpression) {
                return myTypeEvalContext.getType(superClassExpression) instanceof PyClassType classType && classType.isDefinition()
                    ? classType.getPyClass()
                    : null;
            }

            private void validateEnumMembers(PyClass pyClass, List<Pair<PyTargetExpression, EnumAttributeInfo>> attributes) {
                PyType declaredType = getDeclaredEnumMemberType(pyClass, myTypeEvalContext);

                for (Pair<PyTargetExpression, EnumAttributeInfo> pair : attributes) {
                    PyTargetExpression attribute = pair.getFirst();
                    EnumAttributeInfo info = pair.getSecond();
                    if (info.attributeKind() == EnumAttributeKind.NONMEMBER) {
                        continue;
                    }

                    if (declaredType != null && info.attributeKind() == EnumAttributeKind.MEMBER) {
                        PyExpression value = attribute.findAssignedValue();
                        if (PyPsiUtils.flattenParens(value) instanceof PyTupleExpression) {
                            // TODO: > Type checkers may validate consistency between assigned tuple values and the constructor signature
                        }
                        else if (!PyTypeChecker.match(declaredType, info.assignedValueType(), myTypeEvalContext)) {
                            registerProblem(value, PyInspectionMessages.problemMessage(
                                msgArgs -> PyLocalize.inspEnumTypeIsNotAssignableToDeclaredType(msgArgs[0], msgArgs[1]),
                                CodifiedParam.ofType(info.assignedValueType(), attribute, myTypeEvalContext),
                                CodifiedParam.ofType(declaredType, attribute, myTypeEvalContext, true)));
                        }
                    }

                    PyExpression typeHint = PyTypingTypeProvider.getAnnotationValue(attribute, myTypeEvalContext);
                    if (typeHint != null) {
                        registerProblem(typeHint, PyLocalize.inspEnumTypeAnnotationsAreNotAllowedForEnumMembers().get());
                    }
                }
            }

            /**
             * The enum class body rejects a {@code _sunder_} name that the enum machinery does not use.
             */
            private void validateSunderNames(PyClass pyClass) {
                Set<String> allowedNames = allowedSunderNames(LanguageLevel.forElement(pyClass));
                List<PyElement> definitions = new ArrayList<>();
                for (PyTargetExpression attribute : pyClass.getClassAttributes()) {
                    if (attribute.hasAssignedValue()) {
                        definitions.add(attribute);
                    }
                }
                definitions.addAll(Arrays.asList(pyClass.getMethods()));
                definitions.addAll(Arrays.asList(pyClass.getNestedClasses()));
                for (PyElement definition : definitions) {
                    String name = definition.getName();
                    if (name == null) {
                        continue;
                    }
                    if (!PyNames.isSunder(name) || allowedNames.contains(name)) {
                        continue;
                    }
                    if (name.startsWith("_repr_") && LanguageLevel.forElement(pyClass).isAtLeast(LanguageLevel.PYTHON313)) {
                        continue;
                    }
                    registerProblem(nameIdentifierOrSelf(definition),
                                    PyInspectionMessages.problemMessage(msgArgs -> PyLocalize.inspEnumSunderNameIsReserved(msgArgs[0]), name),
                                    ProblemHighlightType.GENERIC_ERROR);
                }
            }

            /**
             * An enum class decorated with {@code @enum.unique} rejects a member alias and a member with the value of another member.
             */
            private void validateUniqueValues(PyClass pyClass, List<Pair<PyTargetExpression, EnumAttributeInfo>> attributes) {
                if (!PyKnownDecoratorUtil.getKnownDecorators(pyClass, myTypeEvalContext).contains(PyKnownDecorator.ENUM_UNIQUE)) {
                    return;
                }

                Map<Object, String> membersByValue = new HashMap<>();
                for (Pair<PyTargetExpression, EnumAttributeInfo> pair : attributes) {
                    PyTargetExpression attribute = pair.getFirst();
                    EnumAttributeInfo info = pair.getSecond();
                    String name = attribute.getName();
                    if (name == null) {
                        continue;
                    }
                    String original = switch (info.attributeKind()) {
                        case MEMBER_ALIAS -> info.assignedValueType() instanceof PyLiteralType literalType ? literalType.getEnumMemberName() : null;
                        case MEMBER -> {
                            Object key = memberValueKey(attribute, info);
                            yield key != null ? membersByValue.putIfAbsent(key, name) : null;
                        }
                        case NONMEMBER -> null;
                    };
                    if (original == null) {
                        continue;
                    }
                    PsiElement anchor = attribute.findAssignedValue();
                    registerProblem(anchor != null ? anchor : attribute,
                                    PyInspectionMessages.problemMessage(
                                        msgArgs -> PyLocalize.inspEnumUniqueEnumHasDuplicateValue(msgArgs[0], msgArgs[1]), name, original),
                                    ProblemHighlightType.GENERIC_ERROR);
                }
            }

            /**
             * A key that equal member values share. Python compares the values, so {@code 1}, {@code True} and {@code 1.0} are one value.
             * Returns {@code null} for a value that the inspection cannot compare.
             */
            private @Nullable Object memberValueKey(PyTargetExpression attribute, EnumAttributeInfo info) {
                PyLiteralType literalType = info.assignedValueType() instanceof PyLiteralType type ? type : null;
                if (literalType != null) {
                    BigInteger intValue = literalType.getIntValue();
                    if (intValue != null) {
                        return new BigDecimal(intValue).stripTrailingZeros();
                    }
                    Boolean boolValue = literalType.getBoolValue();
                    if (boolValue != null) {
                        return boolValue ? BigDecimal.ONE : BigDecimal.ZERO;
                    }
                    // A str and a bytes literal with the same text differ
                    String stringValue = literalType.getStringValue();
                    if (stringValue != null) {
                        return Pair.create(literalType.getPyClass(), stringValue);
                    }
                }
                if (!(PyPsiUtils.flattenParens(attribute.findAssignedValue()) instanceof PyNumericLiteralExpression number)) {
                    return null;
                }
                if (number.getNode().getElementType() == PyElementTypes.IMAGINARY_LITERAL_EXPRESSION) {
                    return null;
                }
                return number.getBigDecimalValue().stripTrailingZeros();
            }
        };
    }

    /**
     * The {@code _sunder_} names that the enum machinery of {@code level} uses. Every other one is reserved.
     */
    private static Set<String> allowedSunderNames(LanguageLevel level) {
        if (level.isAtLeast(LanguageLevel.PYTHON313)) {
            Set<String> result = new HashSet<>(SUNDER_NAMES_311);
            result.add("_add_alias_");
            result.add("_add_value_alias_");
            return result;
        }
        else if (level.isAtLeast(LanguageLevel.PYTHON311)) {
            return SUNDER_NAMES_311;
        }
        else {
            return Set.of("_order_", "_create_pseudo_member_", "_generate_next_value_", "_missing_", "_ignore_");
        }
    }

    private static PsiElement nameIdentifierOrSelf(PyElement element) {
        PsiElement nameIdentifier = element instanceof PsiNameIdentifierOwner owner ? owner.getNameIdentifier() : null;
        return nameIdentifier != null ? nameIdentifier : element;
    }

    private static @Nullable PyType getDeclaredEnumMemberType(PyClass enumClass, TypeEvalContext context) {
        PyTargetExpression targetExpression = enumClass.findClassAttribute("_value_", true, context);
        if (targetExpression == null) {
            return null;
        }
        PyExpression annotationValue = PyTypingTypeProvider.getAnnotationValue(targetExpression, context);
        if (annotationValue == null) {
            return null;
        }
        // TODO consulo: Ref.deref missing
        Ref<PyType> typeRef = PyTypingTypeProvider.getType(annotationValue, context);
        return typeRef != null ? typeRef.get() : null;
    }
}
