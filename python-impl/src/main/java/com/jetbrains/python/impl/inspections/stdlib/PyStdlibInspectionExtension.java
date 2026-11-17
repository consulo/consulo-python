// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.stdlib;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.codeInsight.PyDataclassesKt;
import com.jetbrains.python.impl.codeInsight.stdlib.PyDataclassNames.Dataclasses;
import com.jetbrains.python.impl.codeInsight.stdlib.PyNamedTupleTypeProvider;
import com.jetbrains.python.impl.psi.types.PyNamedTupleType;
import com.jetbrains.python.inspections.PyInspectionExtension;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.types.PyCallableParameter;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;

import java.util.List;

@ExtensionImpl
public class PyStdlibInspectionExtension extends PyInspectionExtension {
    @Override
    public boolean ignoreInitNewSignatures(PyFunction original, PyFunction complementary) {
        PyClass containingClass = complementary.getContainingClass();
        String qName = containingClass != null ? containingClass.getQualifiedName() : null;
        return PyNames.TYPE_ENUM.equals(qName) || PyNames.FQN.TYPE.equals(qName);
    }

    @Override
    public boolean ignoreUnresolvedMember(PyType type, String name, TypeEvalContext context) {
        if (type instanceof PyClassLikeType classLikeType) {
            if (type instanceof PyNamedTupleType namedTupleType && ignoredUnresolvedNamedTupleMember(namedTupleType, name)) {
                return true;
            }
            for (PyClassLikeType ancestorType : classLikeType.getAncestorTypes(context)) {
                if (ancestorType instanceof PyNamedTupleType namedTupleType && ignoredUnresolvedNamedTupleMember(namedTupleType, name)) {
                    return true;
                }
            }
            return false;
        }

        return false;
    }

    @Override
    public boolean ignoreProtectedSymbol(PyReferenceExpression expression, TypeEvalContext context) {
        PyExpression qualifier = expression.getQualifier();
        String referencedName = expression.getReferencedName();
        return qualifier != null &&
            referencedName != null &&
            PyNamedTupleType.getSpecialAttributes(LanguageLevel.forElement(expression)).contains(referencedName) &&
            PyNamedTupleTypeProvider.Helper.isNamedTuple(context.getType(qualifier), context);
    }

    @Override
    public boolean ignoreMethodParameters(PyFunction function, TypeEvalContext context) {
        String name = function.getName();
        if ("__prepare__".equals(name)) {
            List<PyCallableParameter> parameters = function.getParameters(context);
            return parameters.size() == 3 && parameters.stream().noneMatch(it -> it.isKeywordContainer() || it.isPositionalContainer());
        }
        else if (Dataclasses.DUNDER_POST_INIT.equals(name)) {
            PyClass cls = function.getContainingClass();
            return cls != null && PyDataclassesKt.parseStdDataclassParameters(cls, context) != null;
        }
        else {
            return false;
        }
    }

    private static boolean ignoredUnresolvedNamedTupleMember(PyNamedTupleType type, String name) {
        return name.equals(PyNames.SLOTS) || type.getFields().containsKey(name);
    }
}
