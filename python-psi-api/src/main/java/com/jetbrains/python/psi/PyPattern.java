// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi;

import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jspecify.annotations.Nullable;

// TODO consulo: JB extends com.jetbrains.python.ast.PyAstPattern (python-ast layer not ported),
//  its members are placed here
public interface PyPattern extends PyTypedElement {
  /**
   * An irrefutable pattern is a pattern that always succeed (matches).
   * <p>
   * Primarily, these are capture patterns and wildcard patterns, as well as group and OR-patterns containing one of those.
   * <p>
   * <h3>Examples of irrefutable patterns:</h3>
   * <ul>
   *   <li>{@code name}</li>
   *   <li>{@code _} (a wildcard)</li>
   *   <li>{@code 42 | name}</li>
   *   <li>{@code (42 | name) as alias}</li>
   *   <li>{@code ((name))}</li>
   *   <li>{@code *args}</li>
   *   <li>{@code **kwargs}</li>
   * </ul>
   * <p>
   * <h3>Examples of refutable patterns:</h3>
   * <ul>
   *   <li>{@code foo.bar}</li>
   *   <li>{@code 'foo'}</li>
   *   <li>{@code [_]}</li>
   *   <li>{@code [*args]}</li>
   *   <li>{@code str(foo)}</li>
   * </ul>
   */
  boolean isIrrefutable();

  /**
   * Returns the type that would be captured by this pattern when matching.
   * <p>
   * Unlike other PyTypedElements where getType returns their own type, pattern's getType
   * returns the type that would result from a successful match. For example:
   *
   * <pre>{@code
   * class Plant: pass
   * class Animal: pass
   * class Dog(Animal): pass
   *
   * x: Dog | Plant
   * match x:
   * case Animal():
   * # getType returns Dog here, even though the pattern is Animal()
   * }</pre>
   *
   * @see PyCapturePatternImpl.getCaptureType
   */
  @Override
  @Nullable
  PyType getType(TypeEvalContext context, TypeEvalContext.Key key);

  /**
   * Decides if the set of values described by a pattern is suitable
   * to be subtracted (excluded) from a subject type on the negative edge,
   * or if this pattern is too specific.
   */
  default boolean canExcludePatternType(TypeEvalContext context) {
    return true;
  }
}
