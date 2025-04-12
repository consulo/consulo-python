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
import consulo.application.ReadAction;
import consulo.application.util.function.Processor;

import jakarta.annotation.Nonnull;

/**
 * @author yole
 */
@ExtensionImpl
public class DefaultPyOverridingMethodsSearchExecutor implements PyOverridingMethodsSearchExecutor {
    @Override
    public boolean execute(
        @Nonnull final PyOverridingMethodsSearch.SearchParameters queryParameters,
        @Nonnull final Processor<? super PyFunction> consumer
    ) {
        final PyFunction baseMethod = queryParameters.getFunction();

        final PyClass containingClass = ReadAction.compute(baseMethod::getContainingClass);

        return PyClassInheritorsSearch.search(containingClass, queryParameters.isCheckDeep()).forEach(pyClass -> {
            PyFunction overridingMethod = ReadAction.compute(() -> {
                PyFunction func = pyClass.findMethodByName(baseMethod.getName(), false, null);
                if (func != null) {
                    final Property baseProperty = baseMethod.getProperty();
                    final Property overridingProperty = func.getProperty();
                    if (baseProperty != null && overridingProperty != null) {
                        final AccessDirection direction = PyUtil.getPropertyAccessDirection(baseMethod);
                        final PyCallable callable = overridingProperty.getByDirection(direction).valueOrNull();
                        func = (callable instanceof PyFunction) ? (PyFunction)callable : null;
                    }
                }

                return func;
            });

            //noinspection SimplifiableIfStatement
            if (overridingMethod != null) {
                return consumer.process(overridingMethod);
            }
            return true;
        });
    }
}
