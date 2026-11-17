// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.hierarchy.treestructures;

import com.jetbrains.python.impl.hierarchy.PyHierarchyNodeDescriptor;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.language.editor.hierarchy.HierarchyNodeDescriptor;
import consulo.language.editor.hierarchy.HierarchyTreeStructure;
import consulo.language.psi.PsiElement;

import java.util.ArrayList;
import java.util.List;

public class PySuperTypesHierarchyTreeStructure extends HierarchyTreeStructure {
    public PySuperTypesHierarchyTreeStructure(PyClass cl) {
        super(cl.getProject(), new PyHierarchyNodeDescriptor(null, cl, true));
    }

    @Override
    protected Object[] buildChildren(HierarchyNodeDescriptor descriptor) {
        List<PyHierarchyNodeDescriptor> res = new ArrayList<>();
        if (descriptor instanceof PyHierarchyNodeDescriptor pyDescriptor) {
            PsiElement element = pyDescriptor.getPsiElement();
            if (element instanceof PyClass cls) {
                TypeEvalContext context = TypeEvalContext.codeAnalysis(cls.getProject(), cls.getContainingFile());
                for (PyClass superClass : cls.getSuperClasses(context)) {
                    res.add(new PyHierarchyNodeDescriptor(descriptor, superClass, false));
                }
            }
        }
        return res.toArray();
    }
}
