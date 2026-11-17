// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.impl.codeInsight.PyDataclassParameters;
import com.jetbrains.python.impl.codeInsight.PyDataclassesKt;
import com.jetbrains.python.impl.codeInsight.stdlib.PyAttrsDataclassType;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.inspections.PyInspectionExtension;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.util.collection.ContainerUtil;

import java.util.Objects;

@ExtensionImpl
public class PyThirdPartyInspectionExtension extends PyInspectionExtension {

    @Override
    public boolean ignoreMethodParameters(PyFunction function, TypeEvalContext context) {
        PyClass cls = function.getContainingClass();
        if (cls != null) {
            // zope.interface.Interface inheritor could have any parameters
            String interfaceQName = "zope.interface.interface.Interface";
            if (cls.isSubclass(interfaceQName, context)) {
                return true;
            }

            // Checking for subclassing above does not help while zope.interface.Interface is defined as target with call expression assigned
            PyResolveContext resolveContext = PyResolveContext.defaultContext(context);
            for (PyExpression expression : cls.getSuperClassExpressions()) {
                if (resolvesTo(expression, interfaceQName, resolveContext)) {
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public boolean ignoreUnresolvedMember(PyType type, String name, TypeEvalContext context) {
        if (!name.equals("__attrs_attrs__") || !(type instanceof PyClassType classType)) {
            return false;
        }
        PyDataclassParameters parameters = PyDataclassesKt.parseDataclassParameters(classType.getPyClass(), context);
        return parameters != null && parameters.getType() == PyAttrsDataclassType.INSTANCE;
    }

    private static boolean resolvesTo(PyExpression expression, String qualifiedName, PyResolveContext resolveContext) {
        return ContainerUtil.exists(PyUtil.multiResolveTopPriority(expression, resolveContext),
            it -> it instanceof PyElement element && Objects.equals(QualifiedNameFinder.getQualifiedName(element), qualifiedName));
    }
}
