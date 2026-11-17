// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.hierarchy.treestructures;

import com.jetbrains.python.impl.hierarchy.PyHierarchyNodeDescriptor;
import com.jetbrains.python.impl.psi.search.PyClassInheritorsSearch;
import com.jetbrains.python.psi.PyClass;
import consulo.application.util.query.Query;
import consulo.language.editor.hierarchy.HierarchyNodeDescriptor;
import consulo.language.editor.hierarchy.HierarchyTreeStructure;
import consulo.language.psi.PsiElement;
import consulo.project.Project;
import consulo.util.collection.ArrayUtil;

import java.util.ArrayList;
import java.util.List;

public class PySubTypesHierarchyTreeStructure extends HierarchyTreeStructure {
    protected PySubTypesHierarchyTreeStructure(Project project, HierarchyNodeDescriptor baseDescriptor) {
        super(project, baseDescriptor);
    }

    public PySubTypesHierarchyTreeStructure(PyClass cl) {
        super(cl.getProject(), new PyHierarchyNodeDescriptor(null, cl, true));
    }

    @Override
    protected Object[] buildChildren(HierarchyNodeDescriptor descriptor) {
        List<PyHierarchyNodeDescriptor> res = new ArrayList<>();
        PsiElement element = descriptor.getPsiElement();
        if (element instanceof PyClass cls) {
            Query<PyClass> subClasses = PyClassInheritorsSearch.search(cls, false);
            for (PyClass subClass : subClasses /* Consulo: Query is Iterable */) {
                res.add(new PyHierarchyNodeDescriptor(descriptor, subClass, false));
            }

        }

        return ArrayUtil.toObjectArray(res);
    }
}
