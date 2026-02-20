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
package com.jetbrains.python.psi.types;

import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyTypeProvider;
import consulo.language.psi.PsiElement;
import consulo.util.collection.FactoryMap;
import consulo.util.lang.ObjectUtil;
import consulo.util.lang.ref.Ref;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * @author yole
 */
public class PyTypeProviderBase implements PyTypeProvider {

  private final ReturnTypeCallback mySelfTypeCallback = (callSite,
                                                         qualifierType,
                                                         context) -> Optional.ofNullable(ObjectUtil.tryCast(qualifierType,
                                                                                                            PyClassType.class))
                                                                             .map(PyClassType::getPyClass)
                                                                             .map(pyClass -> PyPsiFacade.getInstance(pyClass.getProject())
                                                                                                        .createClassType(pyClass, false))
                                                                             .orElse(null);

  @SuppressWarnings({"MismatchedQueryAndUpdateOfCollection"})
  private final Map<String, ReturnTypeDescriptor> myMethodToReturnTypeMap = FactoryMap.create(s -> new ReturnTypeDescriptor());

  @Nullable
  @Override
  public PyType getReferenceExpressionType(@Nonnull PyReferenceExpression referenceExpression, @Nonnull TypeEvalContext context) {
    return null;
  }

  @Override
  public PyType getReferenceType(@Nonnull PsiElement referenceTarget, TypeEvalContext context, @Nullable PsiElement anchor) {
    return null;
  }

  @Override
  @Nullable
  public Ref<PyType> getParameterType(@Nonnull PyNamedParameter param, @Nonnull PyFunction func, @Nonnull TypeEvalContext context) {
    return null;
  }

  @Nullable
  @Override
  public Ref<PyType> getReturnType(@Nonnull PyCallable callable, @Nonnull TypeEvalContext context) {
    return null;
  }

  @Nullable
  @Override
  public Ref<PyType> getCallType(@Nonnull PyFunction function, @Nullable PyCallSiteExpression callSite, @Nonnull TypeEvalContext context) {
    ReturnTypeDescriptor descriptor;
    synchronized (myMethodToReturnTypeMap) {
      descriptor = myMethodToReturnTypeMap.get(function.getName());
    }
    if (descriptor != null) {
      return descriptor.get(function, callSite, context);
    }
    return null;
  }

  @Nullable
  @Override
  public PyType getContextManagerVariableType(PyClass contextManager, PyExpression withExpression, TypeEvalContext context) {
    return null;
  }

  @Nullable
  @Override
  public PyType getCallableType(@Nonnull PyCallable callable, @Nonnull TypeEvalContext context) {
    return null;
  }

  protected void registerSelfReturnType(@Nonnull String classQualifiedName, @Nonnull Collection<String> methods) {
    registerReturnType(classQualifiedName, methods, mySelfTypeCallback);
  }

  protected void registerReturnType(@Nonnull String classQualifiedName,
                                    @Nonnull Collection<String> methods,
                                    @Nonnull ReturnTypeCallback callback) {
    synchronized (myMethodToReturnTypeMap) {
      for (String method : methods) {
        myMethodToReturnTypeMap.get(method).put(classQualifiedName, callback);
      }
    }
  }

  protected interface ReturnTypeCallback {

    @Nullable
    PyType getType(@Nullable PyCallSiteExpression callSite, @Nullable PyType qualifierType, @Nonnull TypeEvalContext context);
  }

  private static class ReturnTypeDescriptor {

    private final Map<String, ReturnTypeCallback> myStringToReturnTypeMap = new HashMap<>();

    public void put(@Nonnull String classQualifiedName, @Nonnull ReturnTypeCallback callback) {
      myStringToReturnTypeMap.put(classQualifiedName, callback);
    }

    @Nullable
    public Ref<PyType> get(@Nonnull PyFunction function, @Nullable PyCallSiteExpression callSite, @Nonnull TypeEvalContext context) {
      return Optional.ofNullable(function.getContainingClass())
                     .map(pyClass -> myStringToReturnTypeMap.get(pyClass.getQualifiedName()))
                     .map(typeCallback -> typeCallback.getType(callSite,
                                                               getQualifierType(callSite, context), context))
                     .map(Ref::create)
                     .orElse(null);
    }

    @Nullable
    private static PyType getQualifierType(@Nullable PyCallSiteExpression callSite, @Nonnull TypeEvalContext context) {
      PyExpression callee = callSite instanceof PyCallExpression ? ((PyCallExpression)callSite).getCallee() : null;
      PyExpression qualifier = callee instanceof PyQualifiedExpression ? ((PyQualifiedExpression)callee).getQualifier() : null;

      return qualifier != null ? context.getType(qualifier) : null;
    }
  }
}
