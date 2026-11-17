// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi;

import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

/**
 * The known decorators that one decorator can resolve to.
 * <p>
 * Several candidates mean an ambiguous resolve, not several decorators: without the AST the short name of a
 * decorator is all its file gives, and every registered decorator with that short name becomes a candidate.
 * A predicate therefore holds for the decorator when it holds for any one of its candidates.
 *
 * @see PyKnownDecoratorUtil#knownDecoratorsOf
 */
public final class PyKnownDecorators {
  public static final PyKnownDecorators NONE = new PyKnownDecorators(List.of());

  private final List<PyKnownDecorator> candidates;

  private PyKnownDecorators(List<PyKnownDecorator> candidates) {
    this.candidates = candidates;
  }

  /**
   * Whether the decorator resolves to a known decorator at all.
   */
  public boolean isKnown() {
    return !candidates.isEmpty();
  }

  public boolean contains(PyKnownDecorator known) {
    return candidates.contains(known);
  }

  public boolean containsAny(PyKnownDecorator... known) {
    return Arrays.stream(known).anyMatch(candidates::contains);
  }

  public boolean hasTrait(Predicate<PyKnownDecorator> trait) {
    return candidates.stream().anyMatch(trait);
  }

  /**
   * Only {@link PyElementKnownDecorators#findLastWithTrait} needs a candidate rather than an answer about one.
   */
  public @Nullable PyKnownDecorator findWithTrait(Predicate<PyKnownDecorator> trait) {
    return candidates.stream().filter(trait).findFirst().orElse(null);
  }

  public List<PyKnownDecorator> asList() {
    return candidates;
  }

  public static PyKnownDecorators of(List<PyKnownDecorator> candidates) {
    return candidates.isEmpty() ? NONE : new PyKnownDecorators(List.copyOf(candidates));
  }
}
