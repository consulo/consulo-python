/*
 * Copyright 2013-2026 consulo.io
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
package com.jetbrains.python.impl.hierarchy;

import com.jetbrains.python.impl.hierarchy.treestructures.PySubTypesHierarchyTreeStructure;
import com.jetbrains.python.impl.hierarchy.treestructures.PySuperTypesHierarchyTreeStructure;
import com.jetbrains.python.impl.hierarchy.treestructures.PyTypeHierarchyTreeStructure;
import com.jetbrains.python.psi.PyClass;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.editor.hierarchy.HierarchyKind;
import consulo.language.editor.hierarchy.HierarchyModel;
import consulo.language.editor.hierarchy.HierarchyRequest;
import consulo.language.editor.hierarchy.HierarchyTreeStructure;
import consulo.language.editor.hierarchy.HierarchyViewType;
import consulo.language.editor.hierarchy.StandardHierarchyKinds;
import consulo.language.editor.hierarchy.StandardHierarchyViewTypes;
import consulo.language.editor.localize.LanguageEditorLocalize;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * @author Alexey.Ivanov
 * @since 2009-07-31
 */
public class PyTypeHierarchyModel implements HierarchyModel<PyClass> {
    private static final Logger LOG = Logger.getInstance(PyTypeHierarchyModel.class);

    private static final String POPUP_ACTION_GROUP_ID = "PyTypeHierarchyPopupMenu";

    private final PyClass myTarget;

    public PyTypeHierarchyModel(PyClass target) {
        myTarget = target;
    }

    @Override
    public HierarchyKind getKind() {
        return StandardHierarchyKinds.TYPE;
    }

    @Override
    public PyClass getTarget() {
        return myTarget;
    }

    @Override
    public List<HierarchyViewType> getViewTypes() {
        return List.of(
            StandardHierarchyViewTypes.CLASS,
            StandardHierarchyViewTypes.SUPERTYPES,
            StandardHierarchyViewTypes.SUBTYPES
        );
    }

    @RequiredReadAction
    @Override
    public HierarchyViewType getDefaultViewType() {
        return StandardHierarchyViewTypes.CLASS;
    }

    @Override
    public String getPopupActionGroupId() {
        return POPUP_ACTION_GROUP_ID;
    }

    @RequiredReadAction
    @Override
    public @Nullable HierarchyTreeStructure createTreeStructure(HierarchyRequest<PyClass> request) {
        PyClass target = request.getTarget();
        HierarchyViewType viewType = request.getViewType();

        if (StandardHierarchyViewTypes.SUPERTYPES.equals(viewType)) {
            return new PySuperTypesHierarchyTreeStructure(target);
        }
        if (StandardHierarchyViewTypes.SUBTYPES.equals(viewType)) {
            return new PySubTypesHierarchyTreeStructure(target);
        }
        if (StandardHierarchyViewTypes.CLASS.equals(viewType)) {
            return new PyTypeHierarchyTreeStructure(target);
        }

        LOG.error("unexpected view type: " + viewType.getId());
        return null;
    }

    @RequiredReadAction
    @Override
    public boolean isApplicableElement(PsiElement element) {
        return element instanceof PyClass;
    }

    @RequiredReadAction
    @Override
    public boolean canBeBase(PsiElement element) {
        return element instanceof PyClass;
    }

    @RequiredReadAction
    @Override
    public LocalizeValue getBaseOnThisText(PsiElement element) {
        return LanguageEditorLocalize.actionBaseOnThisClass();
    }
}
