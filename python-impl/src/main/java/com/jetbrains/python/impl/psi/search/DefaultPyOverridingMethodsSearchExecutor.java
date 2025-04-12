/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.jetbrains.python.psi.*;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.AccessRule;
import jakarta.annotation.Nonnull;

import java.util.function.Predicate;

/**
 * @author yole
 */
@ExtensionImpl
public class DefaultPyOverridingMethodsSearchExecutor implements PyOverridingMethodsSearchExecutor {
    @Override
    public boolean execute(
        @Nonnull PyOverridingMethodsSearch.SearchParameters queryParameters,
        @Nonnull Predicate<? super PyFunction> consumer
    ) {
        PyFunction baseMethod = queryParameters.getFunction();

        PyClass containingClass = AccessRule.read(baseMethod::getContainingClass);

        return PyClassInheritorsSearch.search(containingClass, queryParameters.isCheckDeep()).forEach(pyClass -> {
            PyFunction overridingMethod = AccessRule.read(() -> {
                PyFunction func = pyClass.findMethodByName(baseMethod.getName(), false, null);
                if (func != null) {
                    Property baseProperty = baseMethod.getProperty();
                    Property overridingProperty = func.getProperty();
                    if (baseProperty != null && overridingProperty != null) {
                        AccessDirection direction = PyUtil.getPropertyAccessDirection(baseMethod);
                        PyCallable callable = overridingProperty.getByDirection(direction).valueOrNull();
                        func = callable instanceof PyFunction function ? function : null;
                    }
                }

                return func;
            });

            return overridingMethod == null || consumer.test(overridingMethod);
        });
    }
}
