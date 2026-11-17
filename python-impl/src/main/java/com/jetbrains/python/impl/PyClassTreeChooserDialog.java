// Copyright 2000-2017 JetBrains s.r.o.
// Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package com.jetbrains.python.impl;

import com.jetbrains.python.impl.psi.stubs.PyClassNameIndex;
import com.jetbrains.python.psi.PyClass;
import consulo.project.content.scope.ProjectAwareSearchScope;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.function.Predicate;

/**
 * @author traff
 * @author ilya.kazakevich
 */
public class PyClassTreeChooserDialog extends PyTreeChooserDialog<PyClass> {
    @RequiredUIAccess
    public PyClassTreeChooserDialog(String title,
                                    Project project,
                                    GlobalSearchScope scope,
                                    @Nullable Predicate<PyClass> classFilter,
                                    @Nullable PyClass initialClass) {
        super(title, PyClass.class, project, scope, classFilter, initialClass);
    }

    @Override
    public Collection<PyClass> findElements(String name, ProjectAwareSearchScope searchScope) {
        return PyClassNameIndex.find(name, getProject(), ((GlobalSearchScope) searchScope).isSearchInLibraries());
    }
}
