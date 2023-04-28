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
package com.jetbrains.python.psi.search;

import com.jetbrains.python.psi.*;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.AccessToken;
import consulo.application.ApplicationManager;
import consulo.application.util.function.Computable;
import consulo.application.util.function.Processor;

import javax.annotation.Nonnull;

/**
 * @author yole
 */
@ExtensionImpl
public class DefaultPyOverridingMethodsSearchExecutor implements PyOverridingMethodsSearchExecutor {
  @Override
  public boolean execute(@Nonnull final PyOverridingMethodsSearch.SearchParameters queryParameters,
                         @Nonnull final Processor<? super PyFunction> consumer) {
    final PyFunction baseMethod = queryParameters.getFunction();

    final PyClass containingClass =
      ApplicationManager.getApplication().runReadAction((Computable<PyClass>)() -> baseMethod.getContainingClass());

    return PyClassInheritorsSearch.search(containingClass, queryParameters.isCheckDeep()).forEach(pyClass -> {
      final AccessToken accessToken = ApplicationManager.getApplication().acquireReadActionLock();
      PyFunction overridingMethod;
      try {
        overridingMethod = pyClass.findMethodByName(baseMethod.getName(), false, null);
        if (overridingMethod != null) {
          final Property baseProperty = baseMethod.getProperty();
          final Property overridingProperty = overridingMethod.getProperty();
          if (baseProperty != null && overridingProperty != null) {
            final AccessDirection direction = PyUtil.getPropertyAccessDirection(baseMethod);
            final PyCallable callable = overridingProperty.getByDirection(direction).valueOrNull();
            overridingMethod = (callable instanceof PyFunction) ? (PyFunction)callable : null;
          }
        }
      }
      finally {
        accessToken.finish();
      }
      //noinspection SimplifiableIfStatement
      if (overridingMethod != null) {
        return consumer.process(overridingMethod);
      }
      return true;
    });
  }
}
