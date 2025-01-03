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

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.toolbox.Maybe;

/**
 * Describes a property, result of either a call to property() or application of @property and friends.
 * This is <i>not</i> a node of PSI tree.
 * <br/>
 * User: dcheryasov
 * Date: May 31, 2010 5:18:10 PM
 */
public interface Property {
  String getName();

  /**
   * @return the setter: a method or null if defined, or something else callable if undefined.
   */
  @Nonnull
  Maybe<PyCallable> getSetter();

  /**
   * @return the getter: a method or null if defined, or something else callable if undefined.
   */
  @Nonnull
  Maybe<PyCallable> getGetter();

  /**
   * @return the deleter: a method or null if defined, or something else callable if undefined.
   */
  @Nonnull
  Maybe<PyCallable> getDeleter();

  /**
   * @return doc string as known to property() call. If null, see getter's doc.
   */
  @Nullable
  String getDoc();

  /**
   * @return the target to which the result of property() call is assigned. For things defined via @property, it is null.
   */
  @Nullable
  PyTargetExpression getDefinitionSite();

  /**
   * @param direction how the property is accessed
   * @return getter, setter, or deleter.
   */
  @Nonnull
  Maybe<PyCallable> getByDirection(@Nonnull AccessDirection direction);

  /**
   * Get the return type of the property getter.
   */
  @Nullable
  PyType getType(@Nonnull TypeEvalContext context);
}
