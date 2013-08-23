/*
 * Copyright 2006 Dmitry Jemerov (yole)
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

package ru.yole.pythonid;

import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.PsiReferenceProvider;
import ru.yole.pythonid.psi.PyElement;

public abstract interface PythonReferenceProviderRegistry
{
  public abstract PsiReference[] getPythonReferences(PyElement paramPyElement);

  public abstract void registerReferenceProvider(PsiReferenceProvider paramPsiReferenceProvider);

  public abstract void unregisterReferenceProvider(PsiReferenceProvider paramPsiReferenceProvider);
}