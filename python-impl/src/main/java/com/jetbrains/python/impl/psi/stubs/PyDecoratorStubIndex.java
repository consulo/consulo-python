// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.stubs;

import consulo.annotation.component.ExtensionImpl;
import com.jetbrains.python.psi.PyDecorator;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.stub.StringStubIndexExtension;
import consulo.language.psi.stub.StubIndex;
import consulo.language.psi.stub.StubIndexKey;
import consulo.project.Project;
import java.util.Collection;

/**
 * Python Decorator stub index.
 * Decorators are indexed by name
 * @author Ilya.Kazakevich
 */
@ExtensionImpl
public final class PyDecoratorStubIndex extends StringStubIndexExtension<PyDecorator> {
  /**
   * Key to search for python decorators
   */
  public static final StubIndexKey<String, PyDecorator> KEY = StubIndexKey.createIndexKey("Python.Decorator");

  public static Collection<PyDecorator> find(String name, Project project) {
    return find(name, project, GlobalSearchScope.allScope(project));
  }

  public static Collection<PyDecorator> find(String name, Project project, GlobalSearchScope scope) {
    return StubIndex.getElements(KEY, name, project, scope, PyDecorator.class);
  }

  @Override
  public StubIndexKey<String, PyDecorator> getKey() {
    return KEY;
  }
}
