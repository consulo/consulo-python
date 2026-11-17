// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.stubs;

import consulo.annotation.component.ExtensionImpl;
import com.jetbrains.python.impl.psi.search.PySearchUtilBase;
import com.jetbrains.python.psi.PyClass;
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
public final class PyClassNameIndex extends StringStubIndexExtension<PyClass> {
  public static final StubIndexKey<String, PyClass> KEY = StubIndexKey.createIndexKey("Py.class.shortName");

  @Override
  public StubIndexKey<String, PyClass> getKey() {
    return KEY;
  }

  public static Collection<PyClass> find(String name, Project project, GlobalSearchScope scope) {
    return StubIndex.getElements(KEY, name, project, scope, PyClass.class);
  }

  public static Collection<PyClass> find(String name, Project project, boolean includeNonProjectItems) {
    final GlobalSearchScope scope = includeNonProjectItems
                                    ? PySearchUtilBase.excludeSdkTestsScope(project)
                                    : GlobalSearchScope.projectScope(project);
    return find(name, project, scope);
  }

  public static List<PyClass> findByQualifiedName(QualifiedName qName,
                                                           Project project,
                                                           GlobalSearchScope scope) {
    String shortName = qName.getLastComponent();
    if (shortName == null) return Collections.emptyList();
    String qNameString = qName.toString();
    return ContainerUtil.filter(find(shortName, project, scope), cls -> qNameString.equals(cls.getQualifiedName()));
  }

  public static List<PyClass> findByQualifiedName(String qName,
                                                           Project project,
                                                           GlobalSearchScope scope) {
    return findByQualifiedName(QualifiedName.fromDottedString(qName), project, scope);
  }

  public static Collection<String> allKeys(Project project) {
    return StubIndex.getInstance().getAllKeys(KEY, project);
  }
}
