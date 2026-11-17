// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;


@ExtensionAPI(ComponentScope.APPLICATION)
public interface PyKnownDecoratorProvider {
  ExtensionPointName<PyKnownDecoratorProvider> EP_NAME = ExtensionPointName.create(PyKnownDecoratorProvider.class);

  default Collection<PyKnownDecorator> getKnownDecorators() {
    return Collections.emptyList();
  }

  /**
   * @deprecated Use {@link #getKnownDecorators()} instead to provide a new {@link PyKnownDecorator} using {@link PyKnownDecorator.Builder}
   */
  @Deprecated(forRemoval = true)
  default @Nullable String toKnownDecorator(String decoratorName) {
    return null;
  }
}
