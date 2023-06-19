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
package com.jetbrains.python.impl.psi.search;

import com.google.common.collect.ImmutableSet;
import com.jetbrains.python.impl.psi.stubs.PySuperClassIndex;
import com.jetbrains.python.psi.PyClass;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.ReadAction;
import consulo.application.util.function.Processor;
import consulo.language.psi.stub.StubIndex;
import consulo.project.Project;
import consulo.project.content.scope.ProjectScopes;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * @author yole
 */
@ExtensionImpl
public class DefaultPyClassInheritorsSearchExecutor implements PyClassInheritorsSearchExecutor {

  /**
   * These base classes are to general to look for inheritors list.
   */
  protected static final ImmutableSet<String> IGNORED_BASES = ImmutableSet.of("object", "BaseException", "Exception");

  public boolean execute(@Nonnull final PyClassInheritorsSearch.SearchParameters queryParameters,
                         @Nonnull final Processor<? super PyClass> consumer) {
    Set<PyClass> processed = new HashSet<>();

    return ReadAction.compute(() -> processDirectInheritors(queryParameters.getSuperClass(),
                                                            consumer,
                                                            queryParameters.isCheckDeepInheritance(),
                                                            processed));
  }

  private static boolean processDirectInheritors(final PyClass superClass,
                                                 final Processor<? super PyClass> consumer,
                                                 final boolean checkDeep,
                                                 final Set<PyClass> processed) {
    final String superClassName = superClass.getName();
    if (superClassName == null || IGNORED_BASES.contains(superClassName)) {
      return true;  // we don't want to look for inheritors of overly general classes
    }
    if (processed.contains(superClass)) {
      return true;
    }
    processed.add(superClass);
    Project project = superClass.getProject();
    final Collection<PyClass> candidates =
      StubIndex.getElements(PySuperClassIndex.KEY, superClassName, project, ProjectScopes.getAllScope(project), PyClass.class);
    for (PyClass candidate : candidates) {
      final PyClass[] classes = candidate.getSuperClasses(null);
      for (PyClass superClassCandidate : classes) {
        if (superClassCandidate.isEquivalentTo(superClass)) {
          if (!consumer.process(candidate)) {
            return false;
          }
          if (checkDeep && !processDirectInheritors(candidate, consumer, checkDeep, processed)) {
            return false;
          }
          break;
        }
      }
    }
    return true;
  }
}
