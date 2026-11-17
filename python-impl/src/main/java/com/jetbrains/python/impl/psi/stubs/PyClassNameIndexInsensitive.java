// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.stubs;

import consulo.annotation.component.ExtensionImpl;
import com.jetbrains.python.psi.PyClass;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.stub.StringStubIndexExtension;
import consulo.language.psi.stub.StubIndex;
import consulo.language.psi.stub.StubIndexKey;
import consulo.project.Project;
import consulo.util.lang.StringUtil;
import java.util.Collection;

@ExtensionImpl
public final class PyClassNameIndexInsensitive extends StringStubIndexExtension<PyClass> {
  public static final StubIndexKey<String, PyClass> KEY = StubIndexKey.createIndexKey("Py.class.shortNameInsensitive");

  @Override
  public StubIndexKey<String, PyClass> getKey() {
    return KEY;
  }

  public static Collection<PyClass> find(String name, Project project) {
    return StubIndex.getElements(KEY, StringUtil.toLowerCase(name), project, GlobalSearchScope.projectScope(project), PyClass.class);
  }
}
