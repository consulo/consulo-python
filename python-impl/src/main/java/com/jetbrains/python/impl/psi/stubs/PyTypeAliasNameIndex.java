// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.stubs;

import consulo.annotation.component.ExtensionImpl;
import com.jetbrains.python.psi.PyTypeAliasStatement;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.stub.StringStubIndexExtension;
import consulo.language.psi.stub.StubIndex;
import consulo.language.psi.stub.StubIndexKey;
import consulo.project.Project;
import java.util.Collection;

@ExtensionImpl
public final class PyTypeAliasNameIndex extends StringStubIndexExtension<PyTypeAliasStatement> {
  public static final StubIndexKey<String, PyTypeAliasStatement> KEY = StubIndexKey.createIndexKey("Py.TypeAliasName");

  @Override
  public StubIndexKey<String, PyTypeAliasStatement> getKey() {
    return KEY;
  }

  public static Collection<PyTypeAliasStatement> find(String name, Project project, GlobalSearchScope scope) {
    return StubIndex.getElements(KEY, name, project, scope, PyTypeAliasStatement.class);
  }
}
