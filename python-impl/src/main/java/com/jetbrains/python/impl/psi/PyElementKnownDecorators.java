// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * The known decorators of a decoratable element, one {@link PyKnownDecorators} per decorator in declaration order.
 * <p>
 * The two axes stay apart: which decorator, and which known decorator each of them can resolve to. A predicate
 * holds for the element when it holds for any of its decorators, while {@link #allKnown} asks about all of them.
 *
 * @see PyKnownDecoratorUtil#knownDecoratorsOf
 */
public final class PyElementKnownDecorators {
  public static final PyElementKnownDecorators NONE = new PyElementKnownDecorators(List.of());

  private final List<PyKnownDecorators> decorators;

  private PyElementKnownDecorators(List<PyKnownDecorators> decorators) {
    this.decorators = decorators;
  }

  /**
   * Whether every decorator of the element resolves to a known decorator. True for an element without decorators.
   */
  public boolean allKnown() {
    return decorators.stream().allMatch(PyKnownDecorators::isKnown);
  }

  public boolean contains(PyKnownDecorator known) {
    return decorators.stream().anyMatch(it -> it.contains(known));
  }

  public boolean containsAny(PyKnownDecorator... known) {
    return decorators.stream().anyMatch(it -> it.containsAny(known));
  }

  public boolean hasTrait(Predicate<PyKnownDecorator> trait) {
    return decorators.stream().anyMatch(it -> it.hasTrait(trait));
  }

  /**
   * Returns the known decorator with [trait] that is nearest the decorated element, or null when none carries it.
   * The decorator nearest the element is applied first and the others wrap it, so it decides.
   */
  public @Nullable PyKnownDecorator findLastWithTrait(Predicate<PyKnownDecorator> trait) {
    for (int i = decorators.size() - 1; i >= 0; i--) {
      PyKnownDecorator found = decorators.get(i).findWithTrait(trait);
      if (found != null) {
        return found;
      }
    }
    return null;
  }

  public List<PyKnownDecorator> asList() {
    List<PyKnownDecorator> result = new ArrayList<>();
    for (PyKnownDecorators it : decorators) {
      result.addAll(it.asList());
    }
    return result;
  }

  public static PyElementKnownDecorators of(List<PyKnownDecorators> decorators) {
    return decorators.isEmpty() ? NONE : new PyElementKnownDecorators(List.copyOf(decorators));
  }
}
