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
package com.jetbrains.python.impl.psi.resolve;

import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import com.jetbrains.python.impl.psi.impl.references.PyReferenceImpl;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.resolve.PyReferenceResolveProvider;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;

import jakarta.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * User : ktisha
 */
@ExtensionImpl
public class PythonBuiltinReferenceResolveProvider implements PyReferenceResolveProvider {
  @Nonnull
  @Override
  public List<RatedResolveResult> resolveName(@Nonnull PyQualifiedExpression element) {
    List<RatedResolveResult> result = new ArrayList<>();
    PsiElement realContext = PyPsiUtils.getRealContext(element);
    String referencedName = element.getReferencedName();
    PyBuiltinCache builtinCache = PyBuiltinCache.getInstance(realContext);
    // ...as a builtin symbol
    PyFile bfile = builtinCache.getBuiltinsFile();
    if (bfile != null && !PyUtil.isClassPrivateName(referencedName)) {
      PsiElement resultElement = bfile.getElementNamed(referencedName);
      if (resultElement == null && "__builtins__".equals(referencedName)) {
        resultElement = bfile; // resolve __builtins__ reference
      }
      if (resultElement != null) {
        TypeEvalContext typeEvalContext = TypeEvalContext.codeInsightFallback(element.getProject());
        result.add(new ImportedResolveResult(resultElement, PyReferenceImpl.getRate(resultElement, typeEvalContext), null));
      }
    }
    return result;
  }
}
