// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.stubs;

import consulo.annotation.component.ExtensionImpl;
import com.jetbrains.python.psi.PyFunction;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.stub.StringStubIndexExtension;
import consulo.language.psi.stub.StubIndex;
import consulo.language.psi.stub.StubIndexKey;
import consulo.language.psi.util.QualifiedName;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@ExtensionImpl
public final class PyFunctionNameIndex extends StringStubIndexExtension<PyFunction> {
  public static final StubIndexKey<String, PyFunction> KEY = StubIndexKey.createIndexKey("Py.function.shortName");

  @Override
  public StubIndexKey<String, PyFunction> getKey() {
    return KEY;
  }

  public static Collection<PyFunction> find(String name, Project project, GlobalSearchScope scope) {
    return StubIndex.getElements(KEY, name, project, scope, PyFunction.class);
  }

  public static Collection<PyFunction> find(String name, Project project) {
    return StubIndex.getElements(KEY, name, project, GlobalSearchScope.allScope(project), PyFunction.class);
  }

  public static Collection<String> allKeys(Project project) {
    return StubIndex.getInstance().getAllKeys(KEY, project);
  }

  public static List<PyFunction> findByQualifiedName(QualifiedName qName,
                                                              Project project,
                                                              GlobalSearchScope scope) {
    String shortName = qName.getLastComponent();
    if (shortName == null) return Collections.emptyList();
    String qNameString = qName.toString();
    return ContainerUtil.filter(find(qName.getLastComponent(), project, scope), func -> qNameString.equals(func.getQualifiedName()));
  }

  public static List<PyFunction> findByQualifiedName(String qName,
                                                              Project project,
                                                              GlobalSearchScope scope) {
    return findByQualifiedName(QualifiedName.fromDottedString(qName), project, scope);
  }
}