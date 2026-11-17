// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.hierarchy.call;

import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.editor.hierarchy.HierarchyKind;
import consulo.language.editor.hierarchy.HierarchyModel;
import consulo.language.editor.hierarchy.HierarchyRequest;
import consulo.language.editor.hierarchy.HierarchyTreeStructure;
import consulo.language.editor.hierarchy.HierarchyViewType;
import consulo.language.editor.hierarchy.StandardHierarchyKinds;
import consulo.language.editor.hierarchy.StandardHierarchyViewTypes;
import consulo.language.psi.PsiElement;
import consulo.logging.Logger;
import consulo.project.Project;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Consulo counterpart of JB PyCallHierarchyBrowser: the platform hierarchy view is driven by a {@link HierarchyModel}.
 *
 * @author novokrest
 */
public class PyCallHierarchyModel implements HierarchyModel<PsiElement> {
    private static final Logger LOG = Logger.getInstance(PyCallHierarchyModel.class);

    private final Project myProject;
    private final PsiElement myTarget;

    public PyCallHierarchyModel(Project project, PsiElement target) {
        myProject = project;
        myTarget = target;
    }

    @Override
    public HierarchyKind getKind() {
        return StandardHierarchyKinds.CALL;
    }

    @Override
    public PsiElement getTarget() {
        return myTarget;
    }

    @Override
    public List<HierarchyViewType> getViewTypes() {
        return List.of(StandardHierarchyViewTypes.CALLER, StandardHierarchyViewTypes.CALLEE);
    }

    @RequiredReadAction
    @Override
    public HierarchyViewType getDefaultViewType() {
        // JB PyCallHierarchyProvider#browserActivated switches to the caller view
        return StandardHierarchyViewTypes.CALLER;
    }

    @Override
    public boolean isScopeSupported() {
        return true;
    }

    @RequiredReadAction
    @Override
    public @Nullable HierarchyTreeStructure createTreeStructure(HierarchyRequest<PsiElement> request) {
        PsiElement psiElement = request.getTarget();
        HierarchyViewType viewType = request.getViewType();
        if (StandardHierarchyViewTypes.CALLER.equals(viewType)) {
            return new PyCallerFunctionTreeStructure(myProject, psiElement, request.getScope());
        }
        else if (StandardHierarchyViewTypes.CALLEE.equals(viewType)) {
            return new PyCalleeFunctionTreeStructure(myProject, psiElement, request.getScope());
        }
        else {
            LOG.error("unexpected type: " + viewType.getId());
            return null;
        }
    }

    @RequiredReadAction
    @Override
    public boolean isApplicableElement(PsiElement element) {
        return element instanceof PyFunction || element instanceof PyClass || element instanceof PyFile;
    }
}
