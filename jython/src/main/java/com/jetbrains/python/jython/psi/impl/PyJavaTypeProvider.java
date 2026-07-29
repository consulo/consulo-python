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
package com.jetbrains.python.jython.psi.impl;

import com.intellij.java.language.psi.*;
import com.jetbrains.python.impl.psi.impl.ParamHelper;
import com.jetbrains.python.impl.psi.search.PySuperMethodsSearch;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyParameterList;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeProviderBase;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import consulo.util.lang.ref.SimpleReference;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
@ExtensionImpl
public class PyJavaTypeProvider extends PyTypeProviderBase {
  @Nullable
  @Override
  @RequiredReadAction
  public PyType getReferenceType(PsiElement referenceTarget, TypeEvalContext context, @Nullable PsiElement anchor) {
    if (referenceTarget instanceof PsiClass psiClass) {
      return new PyJavaClassType(psiClass, true);
    }
    if (referenceTarget instanceof PsiJavaPackage javaPackage) {
        return new PyJavaPackageType(javaPackage, anchor == null ? null : anchor.getModule());
    }
    if (referenceTarget instanceof PsiMethod method) {
      return new PyJavaMethodType(method);
    }
    if (referenceTarget instanceof PsiField field) {
      return asPyType(field.getType());
    }
    return null;
  }

  @Nullable
  public static PyType asPyType(PsiType type) {
    if (type instanceof PsiClassType classType) {
      PsiClass psiClass = classType.resolve();
      if (psiClass != null) {
        return new PyJavaClassType(psiClass, false);
      }
    }
    return null;
  }

  @Override
  @RequiredReadAction
  public SimpleReference<PyType> getParameterType(PyNamedParameter param, PyFunction func, TypeEvalContext context) {
    if (!(param.getParent() instanceof PyParameterList paramList)) {
      return null;
    }
    List<PyNamedParameter> params = ParamHelper.collectNamedParameters(paramList);
    int index = params.indexOf(param);
    if (index < 0) {
      return null;
    }
    List<PyType> superMethodParameterTypes = new ArrayList<>();
    PySuperMethodsSearch.search(func, context).forEach(psiElement -> {
      if (psiElement instanceof PsiMethod method) {
        PsiParameter[] psiParameters = method.getParameterList().getParameters();
        int javaIndex = method.isStatic() ? index : index - 1; // adjust for 'self' parameter
        if (javaIndex < psiParameters.length && psiParameters[javaIndex].getType() instanceof PsiClassType paramClassType) {
          PsiClass psiClass = paramClassType.resolve();
          if (psiClass != null) {
              superMethodParameterTypes.add(new PyJavaClassType(psiClass, false));
          }
        }
      }
      return true;
    });
    if (superMethodParameterTypes.size() > 0) {
      PyType type = superMethodParameterTypes.get(0);
      if (type != null) {
        return SimpleReference.create(type);
      }
    }
    return null;
  }
}
