// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.search;

import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.Property;
import com.jetbrains.python.psi.PyCallable;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.ReadAction;

import java.util.function.Predicate;

/**
 * JB PyOverridingMethodsSearchExecutor (Consulo: {@link PyOverridingMethodsSearchExecutor} is the extension point).
 */
@ExtensionImpl
public final class DefaultPyOverridingMethodsSearchExecutor implements PyOverridingMethodsSearchExecutor {
    @Override
    public boolean execute(PyOverridingMethodsSearch.SearchParameters queryParameters, Predicate<? super PyFunction> consumer) {
        PyFunction baseMethod = queryParameters.getFunction();

        PyClass containingClass = ReadAction.compute(() -> baseMethod.getContainingClass());

        return PyClassInheritorsSearch.search(containingClass, queryParameters.isCheckDeep()).forEach(pyClass -> {
            PyFunction overridingMethod = ReadAction.compute(() -> {
                PyFunction o = pyClass.findMethodByName(baseMethod.getName(), false, null);
                if (o != null) {
                    Property baseProperty = baseMethod.getProperty();
                    Property overridingProperty = o.getProperty();
                    if (baseProperty != null && overridingProperty != null) {
                        AccessDirection direction = PyUtil.getPropertyAccessDirection(baseMethod);
                        PyCallable callable = overridingProperty.getByDirection(direction).valueOrNull();
                        o = (callable instanceof PyFunction) ? (PyFunction)callable : null;
                    }
                }
                return o;
            });
            //noinspection SimplifiableIfStatement
            if (overridingMethod != null) {
                return consumer.test(overridingMethod);
            }
            return true;
        });
    }
}
