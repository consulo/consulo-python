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
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyParameterList;
import com.jetbrains.python.impl.psi.search.PySuperMethodsSearch;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeProviderBase;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import consulo.language.util.ModuleUtilCore;
import consulo.util.lang.ref.Ref;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
@ExtensionImpl
public class PyJavaTypeProvider extends PyTypeProviderBase {
  @Nullable
  public PyType getReferenceType(@Nonnull PsiElement referenceTarget, TypeEvalContext context, @Nullable PsiElement anchor) {
    if (referenceTarget instanceof PsiClass) {
      return new PyJavaClassType((PsiClass)referenceTarget, true);
    }
    if (referenceTarget instanceof PsiJavaPackage) {
      return new PyJavaPackageType((PsiJavaPackage)referenceTarget, anchor == null ? null : ModuleUtilCore.findModuleForPsiElement(anchor));
    }
    if (referenceTarget instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)referenceTarget;
      return new PyJavaMethodType(method);
    }
    if (referenceTarget instanceof PsiField) {
      return asPyType(((PsiField)referenceTarget).getType());
    }
    return null;
  }

  @Nullable
  public static PyType asPyType(PsiType type) {
    if (type instanceof PsiClassType) {
      PsiClassType classType = (PsiClassType)type;
      PsiClass psiClass = classType.resolve();
      if (psiClass != null) {
        return new PyJavaClassType(psiClass, false);
      }
    }
    return null;
  }

  public Ref<PyType> getParameterType(@Nonnull PyNamedParameter param,
                                      @Nonnull PyFunction func,
                                      @Nonnull TypeEvalContext context) {
    if (!(param.getParent() instanceof PyParameterList)) {
      return null;
    }
    List<PyNamedParameter> params = ParamHelper.collectNamedParameters((PyParameterList)param.getParent());
    int index = params.indexOf(param);
    if (index < 0) {
      return null;
    }
    List<PyType> superMethodParameterTypes = new ArrayList<>();
    PySuperMethodsSearch.search(func, context).forEach(psiElement -> {
      if (psiElement instanceof PsiMethod) {
        PsiMethod method = (PsiMethod)psiElement;
        PsiParameter[] psiParameters = method.getParameterList().getParameters();
        int javaIndex = method.hasModifierProperty(PsiModifier.STATIC) ? index : index - 1; // adjust for 'self' parameter
        if (javaIndex < psiParameters.length) {
          PsiType paramType = psiParameters[javaIndex].getType();
          if (paramType instanceof PsiClassType) {
            PsiClass psiClass = ((PsiClassType)paramType).resolve();
            if (psiClass != null) {
              superMethodParameterTypes.add(new PyJavaClassType(psiClass, false));
            }
          }
        }
      }
      return true;
    });
    if (superMethodParameterTypes.size() > 0) {
      PyType type = superMethodParameterTypes.get(0);
      if (type != null) {
        return Ref.create(type);
      }
    }
    return null;
  }
}
