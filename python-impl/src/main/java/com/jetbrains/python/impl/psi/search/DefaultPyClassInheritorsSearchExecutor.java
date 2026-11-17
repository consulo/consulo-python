// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.search;

import com.google.common.collect.ImmutableSet;
import com.jetbrains.python.impl.psi.stubs.PySuperClassIndex;
import com.jetbrains.python.psi.PyClass;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.ReadAction;
import consulo.language.psi.stub.StubIndex;
import consulo.project.Project;
import consulo.project.content.scope.ProjectScopes;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

/**
 * JB PyClassInheritorsSearchExecutor (Consulo: {@link PyClassInheritorsSearchExecutor} is the extension point).
 */
@ExtensionImpl
public final class DefaultPyClassInheritorsSearchExecutor implements PyClassInheritorsSearchExecutor {

    /**
     * These base classes are to general to look for inheritors list.
     */
    private static final ImmutableSet<String> IGNORED_BASES = ImmutableSet.of("object", "BaseException", "Exception");

    @Override
    public boolean execute(PyClassInheritorsSearch.SearchParameters queryParameters, Predicate<? super PyClass> consumer) {
        Set<PyClass> processed = new HashSet<>();
        return processDirectInheritors(queryParameters.getSuperClass(), consumer, queryParameters.isCheckDeepInheritance(), processed);
    }

    private static boolean processDirectInheritors(
        PyClass superClass, Predicate<? super PyClass> consumer, boolean checkDeep, Set<PyClass> processed
    ) {
        return ReadAction.compute(() -> {
            String superClassName = superClass.getName();
            if (superClassName == null || IGNORED_BASES.contains(superClassName)) {
                return true;  // we don't want to look for inheritors of overly general classes
            }
            if (processed.contains(superClass)) {
                return true;
            }
            processed.add(superClass);
            Project project = superClass.getProject();
            Collection<PyClass> candidates = StubIndex.getElements(PySuperClassIndex.KEY, superClassName, project,
                ProjectScopes.getAllScope(project), PyClass.class);
            for (PyClass candidate : candidates) {
                PyClass[] classes = candidate.getSuperClasses(null);
                for (PyClass superClassCandidate : classes) {
                    if (superClassCandidate.isEquivalentTo(superClass)) {
                        if (!consumer.test(candidate)) {
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
        });
    }
}
