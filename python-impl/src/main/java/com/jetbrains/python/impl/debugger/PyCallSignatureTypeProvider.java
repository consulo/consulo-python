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
package com.jetbrains.python.impl.debugger;

import com.jetbrains.python.debugger.PySignature;
import com.jetbrains.python.impl.psi.types.PyDynamicallyEvaluatedType;
import com.jetbrains.python.impl.psi.types.PyTypeParser;
import com.jetbrains.python.psi.PyCallable;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeProviderBase;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.util.lang.ref.Ref;

import jakarta.annotation.Nonnull;

/**
 * @author traff
 */
@ExtensionImpl
public class PyCallSignatureTypeProvider extends PyTypeProviderBase {
  @Override
  public Ref<PyType> getParameterType(@Nonnull final PyNamedParameter param,
                                      @Nonnull final PyFunction func,
                                      @Nonnull TypeEvalContext context) {
    final String name = param.getName();
    if (name != null) {
      final String typeName = PySignatureCacheManager.getInstance(param.getProject()).findParameterType(func, name);
      if (typeName != null) {
        final PyType type = PyTypeParser.getTypeByName(param, typeName);
        if (type != null) {
          return Ref.create(PyDynamicallyEvaluatedType.create(type));
        }
      }
    }
    return null;
  }

  @Override
  public Ref<PyType> getReturnType(@Nonnull final PyCallable callable, @Nonnull TypeEvalContext context) {
    if (callable instanceof PyFunction) {
      PyFunction function = (PyFunction)callable;
      PySignature signature = PySignatureCacheManager.getInstance(function.getProject()).findSignature(function);
      if (signature != null && signature.getReturnType() != null) {
        final String typeName = signature.getReturnType().getTypeQualifiedName();
        if (typeName != null) {
          final PyType type = PyTypeParser.getTypeByName(function, typeName);
          if (type != null) {
            return Ref.create(PyDynamicallyEvaluatedType.create(type));
          }
        }
      }
    }
    return null;
  }
}
