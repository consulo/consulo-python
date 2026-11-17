// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.search;

import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.Property;
import com.jetbrains.python.psi.PyCallable;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;

import java.util.function.Predicate;

/**
 * JB PySuperMethodsSearchExecutor (Consulo: {@link PySuperMethodsSearchExecutor} is the extension point).
 */
@ExtensionImpl
public final class DefaultPySuperMethodsSearchExecutor implements PySuperMethodsSearchExecutor {
    @Override
    public boolean execute(PySuperMethodsSearch.SearchParameters queryParameters, Predicate<? super PsiElement> consumer) {
        PyFunction func = queryParameters.getDerivedMethod();
        String name = func.getName();
        PyClass containingClass = func.getContainingClass();
        TypeEvalContext context = queryParameters.getContext();
        if (name != null && containingClass != null) {
            for (PyClass superClass : containingClass.getAncestorClasses(context)) {
                PyFunction superMethod = superClass.findMethodByName(name, false, context);

                Property property = func.getProperty();
                Property superProperty = superMethod == null ? null : superMethod.getProperty();
                if (property != null && superProperty != null) {
                    AccessDirection direction = PyUtil.getPropertyAccessDirection(func);
                    PyCallable callable = superProperty.getByDirection(direction).valueOrNull();
                    superMethod = (callable instanceof PyFunction) ? (PyFunction)callable : null;
                }

                if (superMethod != null) {
                    boolean consumerWantsMore = consumer.test(superMethod);
                    if (!queryParameters.isDeepSearch() || !consumerWantsMore) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
}
