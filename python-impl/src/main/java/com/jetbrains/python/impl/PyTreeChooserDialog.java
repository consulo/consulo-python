// Copyright 2000-2017 JetBrains s.r.o.
// Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package com.jetbrains.python.impl;

import consulo.project.content.scope.ProjectAwareSearchScope;
import consulo.ide.impl.idea.ide.util.AbstractTreeClassChooserDialog;
import consulo.language.psi.PsiNamedElement;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import org.jspecify.annotations.Nullable;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.Collection;
import java.util.function.Predicate;

/**
 * @author traff
 * @author ilya.kazakevich
 */
public abstract class PyTreeChooserDialog<T extends PsiNamedElement> extends AbstractTreeClassChooserDialog<T> {
    @RequiredUIAccess
    public PyTreeChooserDialog(String title,
                               Class<T> clazz,
                               Project project,
                               GlobalSearchScope scope,
                               @Nullable Predicate<T> classFilter,
                               @Nullable T initialValue) {
        super(title, project, scope, clazz, classFilter, initialValue);
    }

    @Override
    protected @Nullable T getSelectedFromTreeUserObject(DefaultMutableTreeNode node) {
        return null;
    }

    @Override
    protected Collection<T> getClassesByName(String name, boolean checkBoxState, String pattern, ProjectAwareSearchScope searchScope) {
        return findElements(name, searchScope).stream().filter(getFilter()).toList();
    }

    public abstract Collection<T> findElements(String name, ProjectAwareSearchScope searchScope);
}
