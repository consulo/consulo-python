/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.psi;

import com.jetbrains.python.psi.resolve.QualifiedNameResolver;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.ide.ServiceManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.QualifiedName;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;

import org.jspecify.annotations.Nullable;
import java.util.Collection;
import java.util.List;

/**
 * @author yole
 */
@ServiceAPI(ComponentScope.PROJECT)
public abstract class PyPsiFacade {
  public static PyPsiFacade getInstance(Project project) {
    return ServiceManager.getService(project, PyPsiFacade.class);
  }

  public abstract QualifiedNameResolver qualifiedNameResolver(String qNameString);

  public abstract QualifiedNameResolver qualifiedNameResolver(QualifiedName qualifiedName);

  /**
   * @deprecated use {@link #createClassByQName(String, PsiElement)} or skeleton may be found
   */
  @Deprecated
  @Nullable
  public abstract PyClass findClass(String qName);

  public abstract PyClassType createClassType(PyClass pyClass, boolean isDefinition);

  @Nullable
  public abstract PyType createUnionType(Collection<PyType> members);

  @Nullable
  public abstract PyType createTupleType(List<PyType> members, PsiElement anchor);

  @Nullable
  public abstract PyType parseTypeAnnotation(String annotation, PsiElement anchor);

  @Nullable
  public abstract PyClass createClassByQName(String qName, PsiElement anchor);

  @Nullable
  public abstract String findShortestImportableName(VirtualFile targetFile, PsiElement anchor);
}
