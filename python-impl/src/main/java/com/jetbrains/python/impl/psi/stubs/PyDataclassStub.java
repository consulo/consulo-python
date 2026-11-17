// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.stubs;

import com.jetbrains.python.impl.psi.impl.stubs.PyDataclassMetadata;
import com.jetbrains.python.psi.impl.stubs.PyCustomClassStub;
import consulo.language.psi.util.QualifiedName;
import org.jspecify.annotations.Nullable;

/**
 * Represents dataclass-related properties directly available in a class definition, i.e. not considering its ancestor classes,
 * decorator parameter defaults or any other "external" configuration sources.
 * <p>
 * Note that omitted properties should have {@code null} value, not the default. These are substituted with the corresponding defaults
 * later during analysis after checking other possible sources.
 * <p>
 * To get a complete "merged" set of properties use {@code PyDataclassesKt#parseDataclassParameters(PyClass, TypeEvalContext)}.
 */
public interface PyDataclassStub extends PyCustomClassStub {

  /**
   * @return library used to determine dataclass.
   */
  String getType();

  @Nullable QualifiedName decoratorName();

  /**
   * @return value of `init` parameter or
   * its default value if it is not specified or could not be evaluated.
   */
  @Nullable Boolean initValue();

  /**
   * @return value of `repr` parameter or
   * its default value if it is not specified or could not be evaluated.
   */
  @Nullable Boolean reprValue();

  /**
   * @return value of `eq` (std) or `cmp` (attrs) parameter or
   * its default value if it is not specified or could not be evaluated.
   */
  @Nullable Boolean eqValue();

  /**
   * @return value of `order` (std) or `cmp` (attrs) parameter or
   * its default value if it is not specified or could not be evaluated.
   */
  @Nullable Boolean orderValue();

  /**
   * @return value of `unsafe_hash` (std) or `hash` (attrs) parameter or
   * its default value if it is not specified or could not be evaluated.
   */
  @Nullable Boolean unsafeHashValue();

  /**
   * @return value of `frozen` parameter or
   * its default value if it is not specified or could not be evaluated.
   */
  @Nullable Boolean frozenValue();

  /**
   * @return value of `matchArgs` parameter or
   * its default value if it is not specified or could not be evaluated.
   */
  @Nullable Boolean matchArgsValue();

  /**
   * @return value of `kw_only` parameter or
   * its default value if it is not specified or could not be evaluated.
   */
  @Nullable Boolean kwOnly();

  /**
   * @return value of `slots` parameter or
   * its default value if it is not specified or could not be evaluated.
   */
  @Nullable Boolean slotsValue();

  /**
   * Opaque per-class payload written by the framework that built this stub, or `null` when it persisted nothing.
   * For built-in dataclass/attrs/transform classes this is always `null`. Decode it with {@link PyDataclassMetadata#decode},
   * after checking {@link #getType()} names your framework.
   */
  @Nullable PyDataclassMetadata getMetadata();
}
