/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.impl.psi.impl;

import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.impl.psi.resolve.QualifiedNameResolverImpl;
import com.jetbrains.python.impl.psi.stubs.PyClassNameIndex;
import com.jetbrains.python.impl.psi.types.PyClassTypeImpl;
import com.jetbrains.python.impl.psi.types.PyTupleType;
import com.jetbrains.python.impl.psi.types.PyTypeParser;
import com.jetbrains.python.impl.psi.types.PyUnionType;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyPsiFacade;
import com.jetbrains.python.psi.resolve.QualifiedNameResolver;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import consulo.annotation.component.ServiceImpl;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.QualifiedName;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Collection;
import java.util.List;

/**
 * @author yole
 */
@ServiceImpl
@Singleton
public class PyPsiFacadeImpl extends PyPsiFacade {
  private final Project myProject;

  @Inject
  public PyPsiFacadeImpl(Project project) {
    myProject = project;
  }

  @Override
  public QualifiedNameResolver qualifiedNameResolver(String qNameString) {
    return new QualifiedNameResolverImpl(qNameString);
  }

  @Override
  public QualifiedNameResolver qualifiedNameResolver(QualifiedName qualifiedName) {
    return new QualifiedNameResolverImpl(qualifiedName);
  }

  @Nullable
  @Override
  public PyClass findClass(String qName) {
    return PyClassNameIndex.findClass(qName, myProject);
  }

  @Nonnull
  @Override
  public PyClassType createClassType(@Nonnull PyClass pyClass, boolean isDefinition) {
    return new PyClassTypeImpl(pyClass, isDefinition);
  }

  @Nullable
  @Override
  public PyType createUnionType(@Nonnull Collection<PyType> members) {
    return PyUnionType.union(members);
  }

  @Nullable
  @Override
  public PyType createTupleType(@Nonnull List<PyType> members, @Nonnull PsiElement anchor) {
    return PyTupleType.create(anchor, members);
  }

  @Nullable
  @Override
  public PyType parseTypeAnnotation(@Nonnull String annotation, @Nonnull PsiElement anchor) {
    return PyTypeParser.getTypeByName(anchor, annotation);
  }

  @Nullable
  @Override
  public final PyClass createClassByQName(@Nonnull String qName, @Nonnull PsiElement anchor) {
    PyClassType classType = PyUtil.as(parseTypeAnnotation(qName, anchor), PyClassType.class);
    return (classType != null ? classType.getPyClass() : null);
  }

  @Nullable
  @Override
  public String findShortestImportableName(@Nonnull VirtualFile targetFile, @Nonnull PsiElement anchor) {
    return QualifiedNameFinder.findShortestImportableName(anchor, targetFile);
  }
}
