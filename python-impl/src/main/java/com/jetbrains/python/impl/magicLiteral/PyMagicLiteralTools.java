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
package com.jetbrains.python.impl.magicLiteral;

import com.jetbrains.python.psi.StringLiteralExpression;
import consulo.application.Application;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiModificationTracker;
import consulo.language.psi.util.PsiCacheKey;
import consulo.util.lang.ref.Ref;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.function.Function;

/**
 * Tools that help user to work with magic literals.
 *
 * @author Ilya.Kazakevich
 */
public final class PyMagicLiteralTools {
  /**
   * Cache: ref (value may be null or extension point) by by string literal
   */
  private final static PsiCacheKey<Ref<PyMagicLiteralExtensionPoint>, StringLiteralExpression>
    MAGIC_LITERAL_POINT = PsiCacheKey.create(PyMagicLiteralTools.class.getName(),
                                             new MagicLiteralChecker(), PsiModificationTracker.MODIFICATION_COUNT);

  private PyMagicLiteralTools() {
  }


  /**
   * Checks if literal is magic (there is some extension point that supports it)
   *
   * @param element element to check
   * @return true if magic
   */
  public static boolean isMagicLiteral(@Nonnull final PsiElement element) {
    return (element instanceof StringLiteralExpression) && (getPoint((StringLiteralExpression)element) != null);
  }

  /**
   * Gets extension point by literal.
   *
   * @param element literal
   * @return extension point (if any) or null if literal is unknown to all installed magic literal extesnion points
   */
  @Nullable
  public static PyMagicLiteralExtensionPoint getPoint(@Nonnull final StringLiteralExpression element) {
    return MAGIC_LITERAL_POINT.getValue(element).get();
  }

  /**
   * Obtains ref (value may be null or extension point) by by string literal
   */
  private static class MagicLiteralChecker implements Function<StringLiteralExpression, Ref<PyMagicLiteralExtensionPoint>> {
    @Override
    public Ref<PyMagicLiteralExtensionPoint> apply(StringLiteralExpression element) {
      for (final PyMagicLiteralExtensionPoint magicLiteralExtensionPoint : Application.get().getExtensionList(PyMagicLiteralExtensionPoint.class)) {
        if (magicLiteralExtensionPoint.isMagicLiteral(element)) {
          return Ref.create(magicLiteralExtensionPoint);
        }
      }
      return new Ref<>();
    }
  }
}
