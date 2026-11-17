// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.hierarchy.call;

import com.jetbrains.python.psi.PyElement;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.editor.hierarchy.HierarchyScope;
import consulo.language.psi.PsiElement;
import consulo.project.Project;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Map;

/**
 * @author novokrest
 */
public class PyCalleeFunctionTreeStructure extends PyCallHierarchyTreeStructureBase {
    public PyCalleeFunctionTreeStructure(Project project, PsiElement element, @Nullable HierarchyScope currentScopeType) {
        super(project, element, currentScopeType);
    }

    @Override
    @RequiredReadAction
    protected Map<PsiElement, Collection<PsiElement>> getChildren(PyElement element) {
        // TODO: Add callees from the dynamic call data manager
        return PyStaticCallHierarchyUtil.getCallees(element);
    }
}
