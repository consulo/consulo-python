// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.impl.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.impl.documentation.PythonDocumentationProvider;
import com.jetbrains.python.impl.inspections.PyInspectionMessages.ProblemMessage;
import com.jetbrains.python.impl.psi.types.PyClassTypeImpl;
import com.jetbrains.python.impl.psi.types.PyTypeUtil;
import com.jetbrains.python.impl.psi.types.PyTypedDictType;
import com.jetbrains.python.impl.psi.types.PyUnionType;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyCallable;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.types.PyCallableType;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.access.RequiredWriteAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.InspectionToolState;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.lang.ref.Ref;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@ExtensionImpl
public class PyInvalidCastInspection extends PyInspection {
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameInvalidCast();
    }

    @Override
    public LocalizeValue getGroupDisplayName() {
        return PyLocalize.inspGroupPythonTypeHinting();
    }

    @Override
    public InspectionToolState<?> createStateProvider() {
        return new PyInvalidCastInspectionState();
    }

    @Override
    public PsiElementVisitor buildVisitor(ProblemsHolder holder,
                                          boolean isOnTheFly,
                                          LocalInspectionToolSession session,
                                          Object state) {
        TypeEvalContext context = PyInspectionVisitor.getContext(session);
        PyInvalidCastInspectionState inspectionState = (PyInvalidCastInspectionState) state;
        boolean ignoreGenericVariance = inspectionState.ignoreGenericVariance;
        boolean ignoreTypedDictStructure = inspectionState.ignoreTypedDictStructure;
        return new PyInspectionVisitor(holder, context) {
            @Override
            public void visitPyCallExpression(PyCallExpression callExpression) {
                PyExpression callee = callExpression.getCallee();
                if (callee == null) {
                    return;
                }
                List<PyCallableType> callables = PyTypeUtil.getCallableItems(context.getType(callee));
                boolean isCastCall = false;
                for (PyCallableType it : callables) {
                    PyCallable callable = it.getCallable();
                    String qualifiedName = callable != null ? callable.getQualifiedName() : null;
                    if (PyTypingTypeProvider.CAST.equals(qualifiedName) || PyTypingTypeProvider.CAST_EXT.equals(qualifiedName)) {
                        isCastCall = true;
                        break;
                    }
                }
                if (!isCastCall) {
                    return;
                }

                PyExpression[] args = callExpression.getArguments();
                if (args.length != 2) {
                    return;
                }
                Ref<PyType> targetTypeRef = PyTypingTypeProvider.getType(args[0], myTypeEvalContext);
                PyType targetType = PyTypeUtil.derefOrUnknown(targetTypeRef);
                PyType actualType = myTypeEvalContext.getType(args[1]);

                if (PyTypeUtil.isSubtypeRelated(targetType, actualType, myTypeEvalContext)) {
                    return;
                }

                // Treat a TypedDict as a plain 'dict' when the corresponding option is enabled, so that e.g. a
                // 'dict[str, object]' may be cast to a TypedDict and vice versa.
                if (ignoreTypedDictStructure &&
                    PyTypeUtil.isSubtypeRelated(eraseTypedDictStructure(targetType), eraseTypedDictStructure(actualType), myTypeEvalContext)) {
                    return;
                }

                // The types become subtype-related once the variance of their generic arguments is ignored, so the mismatch
                // is purely about variance (e.g. casting the invariant 'list[int]' to 'list[object]'). Such casts are only
                // reported when the "ignore generic variance" option is disabled, and get a dedicated message.
                boolean relatedIgnoringVariance = isSubtypeRelatedIgnoringVariance(targetType, actualType, myTypeEvalContext);
                if (relatedIgnoringVariance && ignoreGenericVariance) {
                    return;
                }

                String fromName = PythonDocumentationProvider.getTypeName(actualType, myTypeEvalContext);
                String toName = PythonDocumentationProvider.getVerboseTypeName(targetType, myTypeEvalContext);

                String suggestedName = computeSuggestedIntermediateTypeName(targetType, actualType, myTypeEvalContext);

                ProblemMessage message = relatedIgnoringVariance
                    ? PyInspectionMessages.problemMessage(msgArgs -> PyLocalize.inspInvalidCastVarianceMessage(msgArgs[0], msgArgs[1]),
                                                          fromName, toName, suggestedName)
                    : PyInspectionMessages.problemMessage(msgArgs -> PyLocalize.inspInvalidCastMessage(msgArgs[0], msgArgs[1], msgArgs[2]),
                                                          fromName, toName, suggestedName);

                registerProblem(
                    callExpression,
                    message,
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                    new AddIntermediateCastQuickFix(suggestedName)
                );
            }
        };
    }

    // JB: PsiUpdateModCommandQuickFix (ModCommand API is not available in Consulo)
    private static class AddIntermediateCastQuickFix implements LocalQuickFix {
        private final String myTypeText;

        AddIntermediateCastQuickFix(String typeText) {
            myTypeText = typeText;
        }

        @Override
        public LocalizeValue getName() {
            return PyLocalize.qfixAddIntermediateCast(myTypeText);
        }

        @Override
        @RequiredWriteAction
        public void applyFix(Project project, ProblemDescriptor descriptor) {
            PsiElement element = descriptor.getPsiElement();
            if (!(element instanceof PyCallExpression call)) {
                return;
            }
            PyExpression[] args = call.getArguments();
            if (args.length != 2) {
                return;
            }
            PyExpression expr = args[1];
            if (expr == null) {
                return;
            }

            PyExpression callee = call.getCallee();
            String calleeText = callee != null ? callee.getText() : "cast";
            LanguageLevel langLevel = LanguageLevel.forElement(call);
            PyElementGenerator generator = PyElementGenerator.getInstance(project);
            String castExprText = calleeText + "(" + myTypeText + ", " + expr.getText() + ")";
            PyExpression newExpr = generator.createExpressionFromText(langLevel, castExprText);
            expr.replace(newExpr);
        }
    }

    /**
     * Checks whether two types are subtype-related while ignoring the variance of generic type arguments: two
     * parameterized types of related base classes are considered related as long as their corresponding type arguments
     * are themselves subtype-related, regardless of the direction required by the declared variance
     * (e.g. {@code list[int]} and {@code list[object]} are related, but {@code list[str]} and {@code list[int]} are not).
     * This makes the check insensitive to whether a generic parameter is invariant, covariant, or contravariant. Union
     * members are distributed over.
     */
    private static boolean isSubtypeRelatedIgnoringVariance(@Nullable PyType t1, @Nullable PyType t2, TypeEvalContext context) {
        if (t1 instanceof PyUnionType union1) {
            return union1.getMembers().stream().anyMatch(it -> isSubtypeRelatedIgnoringVariance(it, t2, context));
        }
        if (t2 instanceof PyUnionType union2) {
            return union2.getMembers().stream().anyMatch(it -> isSubtypeRelatedIgnoringVariance(t1, it, context));
        }

        if (t1 instanceof PyClassType classType1 && t2 instanceof PyClassType classType2
            && classType1.isParameterized() && classType2.isParameterized()) {
            PyClassTypeImpl base1 = new PyClassTypeImpl(classType1.getPyClass(), classType1.isDefinition());
            PyClassTypeImpl base2 = new PyClassTypeImpl(classType2.getPyClass(), classType2.isDefinition());
            if (!PyTypeUtil.isSubtypeRelated(base1, base2, context)) {
                return false;
            }

            List<PyType> args1 = classType1.getTypeArguments();
            List<PyType> args2 = classType2.getTypeArguments();
            // Different arity means we can't align the arguments; the base-class relation is enough to consider them related.
            if (args1.size() != args2.size()) {
                return true;
            }
            for (int i = 0; i < args1.size(); i++) {
                if (!isSubtypeRelatedIgnoringVariance(args1.get(i), args2.get(i), context)) {
                    return false;
                }
            }
            return true;
        }

        return PyTypeUtil.isSubtypeRelated(t1, t2, context);
    }

    /**
     * Replaces TypedDict types with their underlying {@code dict} class, so that the subtype-relation check ignores the structural
     * details of a TypedDict and treats it as a plain dictionary (e.g. a {@code dict[str, object]} may be cast to a TypedDict).
     * Union members are erased element-wise.
     */
    private static @Nullable PyType eraseTypedDictStructure(@Nullable PyType type) {
        if (type instanceof PyUnionType unionType) {
            return unionType.map(PyInvalidCastInspection::eraseTypedDictStructure);
        }
        if (type instanceof PyTypedDictType typedDictType) {
            return new PyClassTypeImpl(typedDictType.getPyClass(), typedDictType.isDefinition());
        }
        return type;
    }

    private static String computeSuggestedIntermediateTypeName(@Nullable PyType targetType,
                                                               @Nullable PyType actualType,
                                                               TypeEvalContext context) {
        String objectName = "object";

        PyClassLikeType left = toNonCollectionClassLike(actualType);
        PyClassLikeType right = toNonCollectionClassLike(targetType);
        if (left != null && right != null) {
            List<PyClassLikeType> leftMro = mro(left, context);
            List<String> rightQNames = new ArrayList<>();
            for (PyClassLikeType it : mro(right, context)) {
                String classQName = it.getClassQName();
                if (classQName != null) {
                    rightQNames.add(classQName);
                }
            }
            Set<String> rightSet = new HashSet<>(rightQNames);

            for (PyClassLikeType t : leftMro) {
                String qn = t.getClassQName();
                if (qn != null && rightSet.contains(qn)) {
                    String name = t.getName();
                    if (name != null && !name.equals(objectName)) {
                        return name;
                    }
                }
            }
        }

        return objectName;
    }

    private static @Nullable PyClassLikeType toNonCollectionClassLike(@Nullable PyType t) {
        if (t instanceof PyClassType classType && classType.isParameterized()) {
            return null; // avoid suggesting collection classes like 'list' as an intermediate type
        }
        if (t instanceof PyClassLikeType classLikeType) {
            return classLikeType;
        }
        return null;
    }

    private static List<PyClassLikeType> mro(PyClassLikeType t, TypeEvalContext context) {
        List<PyClassLikeType> result = new ArrayList<>();
        result.add(t);
        for (PyClassLikeType ancestor : t.getAncestorTypes(context)) {
            if (ancestor != null) {
                result.add(ancestor);
            }
        }
        return result;
    }
}
