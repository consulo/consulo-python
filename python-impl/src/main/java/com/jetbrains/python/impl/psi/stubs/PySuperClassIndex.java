// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.stubs;

import consulo.annotation.component.ExtensionImpl;
import com.jetbrains.python.psi.PyClass;
import consulo.language.psi.stub.StringStubIndexExtension;
import consulo.language.psi.stub.StubIndexKey;

@ExtensionImpl
public final class PySuperClassIndex extends StringStubIndexExtension<PyClass> {
  public static final StubIndexKey<String, PyClass> KEY = StubIndexKey.createIndexKey("Py.class.super");

  @Override
  public StubIndexKey<String, PyClass> getKey() {
    return KEY;
  }
}
