/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package com.jetbrains.python.psi;

import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;
import consulo.language.ast.IElementType;
import com.jetbrains.python.PythonFileType;
import org.jetbrains.annotations.NonNls;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.lang.reflect.Constructor;

public class PyElementType extends IElementType {
  protected Class<? extends PsiElement> myPsiElementClass;
  private static final Class[] PARAMETER_TYPES = new Class[]{ASTNode.class};
  private Constructor<? extends PsiElement> myConstructor;

  private String mySpecialMethodName;

  public PyElementType(@Nonnull @NonNls String debugName) {
    super(debugName, PythonFileType.INSTANCE.getLanguage());
  }

  public PyElementType(@Nonnull @NonNls String debugName, @Nonnull Class<? extends PsiElement> psiElementClass) {
    this(debugName);
    myPsiElementClass = psiElementClass;
  }

  public PyElementType(@Nonnull @NonNls String debugName, @Nonnull @NonNls String specialMethodName) {
    this(debugName);
    mySpecialMethodName = specialMethodName;
  }

  @Nonnull
  public PsiElement createElement(@Nonnull ASTNode node) {
    if (myPsiElementClass == null) {
      throw new IllegalStateException("Cannot create an element for " + node.getElementType() + " without element class");
    }
    try {
      if (myConstructor == null) {
        myConstructor = myPsiElementClass.getConstructor(PARAMETER_TYPES);
      }
      return myConstructor.newInstance(node);
    }
    catch (Exception e) {
      throw new IllegalStateException("No necessary constructor for " + node.getElementType(), e);
    }
  }

  /**
   * @return name of special method for operation marked by this token; e.g. "__add__" for "+".
   */
  @Nullable
  public String getSpecialMethodName() {
    return mySpecialMethodName;
  }

  @Override
  public String toString() {
    return "Py:" + super.toString();
  }
}
