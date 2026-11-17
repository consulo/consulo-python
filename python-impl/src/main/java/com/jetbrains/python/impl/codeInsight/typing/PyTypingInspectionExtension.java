// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.typing;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import com.jetbrains.python.impl.psi.impl.references.PyOperatorReference;
import com.jetbrains.python.inspections.PyInspectionExtension;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PySubscriptionExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiReference;
import consulo.language.psi.ResolveResult;

import java.util.Arrays;
import java.util.Objects;

@ExtensionImpl
public class PyTypingInspectionExtension extends PyInspectionExtension {
    @Override
    public boolean ignoreUnresolvedReference(PyElement node, PsiReference reference, TypeEvalContext context) {
        if (node instanceof PySubscriptionExpression subscription &&
            reference instanceof PyOperatorReference &&
            PyNames.GETITEM.equals(subscription.getReferencedName())) {
            PyExpression operand = subscription.getOperand();
            PyType type = context.getType(operand);

            if (type instanceof PyClassLikeType classLikeType && classLikeType.isDefinition()) {
                if (isGenericItselfOrDescendant(classLikeType, context)) {
                    // `true` is not returned for the cases like `typing.List[int]`
                    // because these types contain builtins as a class
                    if (!isBuiltin(classLikeType)) {
                        return true;
                    }

                    // here is the check that current element is like `typing.List[int]`
                    // but be careful: builtin collections inherit `typing.Generic` in typeshed
                    if (operand instanceof PyReferenceExpression referenceExpression) {
                        PyResolveContext resolveContext = PyResolveContext.defaultContext(context);
                        ResolveResult[] resolveResults = referenceExpression.getReference(resolveContext).multiResolve(false);

                        if (Arrays.stream(resolveResults)
                            .map(ResolveResult::getElement)
                            .anyMatch(it -> it instanceof PyTargetExpression target &&
                                PyTypingTypeProvider.BUILTIN_COLLECTION_CLASSES.containsKey(target.getQualifiedName()))) {
                            return true;
                        }
                    }
                }
                PyClassType typeType = PyBuiltinCache.getInstance(operand).getTypeType();
                if (Objects.equals(typeType != null ? typeType.toClass() : null, classLikeType)) {
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean isGenericItselfOrDescendant(PyClassLikeType type, TypeEvalContext context) {
        return PyTypingTypeProvider.GENERIC_CLASSES.contains(type.getClassQName()) || PyTypingTypeProvider.isGeneric(type, context);
    }

    private static boolean isBuiltin(PyClassLikeType type) {
        return type instanceof PyClassType classType && PyBuiltinCache.getInstance(classType.getPyClass()).isBuiltin(classType.getPyClass());
    }
}
