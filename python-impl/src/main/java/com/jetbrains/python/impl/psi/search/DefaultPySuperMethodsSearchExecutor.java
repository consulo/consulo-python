/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.impl.psi.search;

import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.types.PyTypeUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import jakarta.annotation.Nonnull;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

/**
 * @author yole
 */
@ExtensionImpl
public class DefaultPySuperMethodsSearchExecutor implements PySuperMethodsSearchExecutor {
    @Override
    @RequiredReadAction
    public boolean execute(
        @Nonnull PySuperMethodsSearch.SearchParameters queryParameters,
        @Nonnull Predicate<? super PsiElement> consumer
    ) {
        PyFunction func = queryParameters.getDerivedMethod();
        String name = func.getName();
        PyClass containingClass = func.getContainingClass();
        Set<PyClass> foundMethodContainingClasses = new HashSet<>();
        TypeEvalContext context = queryParameters.getContext();
        if (name != null && containingClass != null) {
            for (PyClass superClass : containingClass.getAncestorClasses(context)) {
                if (!queryParameters.isDeepSearch()) {
                    boolean isAlreadyFound = false;
                    for (PyClass alreadyFound : foundMethodContainingClasses) {
                        if (alreadyFound.isSubclass(superClass, context)) {
                            isAlreadyFound = true;
                        }
                    }
                    if (isAlreadyFound) {
                        continue;
                    }
                }
                PyFunction superMethod = superClass.findMethodByName(name, false, null);
                if (superMethod != null) {
                    Property property = func.getProperty();
                    Property superProperty = superMethod.getProperty();
                    if (property != null && superProperty != null) {
                        AccessDirection direction = PyUtil.getPropertyAccessDirection(func);
                        PyCallable callable = superProperty.getByDirection(direction).valueOrNull();
                        superMethod = callable instanceof PyFunction function ? function : null;
                    }
                }

                if (superMethod == null && context != null) {
                    // If super method still not found and we have context, we may use it to find method
                    PyClassLikeType classLikeType = PyUtil.as(context.getType(superClass), PyClassLikeType.class);
                    if (classLikeType != null) {
                        for (PyFunction function : PyTypeUtil.getMembersOfType(classLikeType, PyFunction.class, true, context)) {
                            String elemName = function.getName();
                            if (elemName != null && elemName.equals(func.getName())) {
                                consumer.test(function);
                            }
                        }
                    }
                }
                if (superMethod != null) {
                    foundMethodContainingClasses.add(superClass);
                    if (!consumer.test(superMethod)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
}
