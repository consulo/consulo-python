/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package com.jetbrains.python.structureView;

import consulo.application.AllIcons;
import consulo.fileEditor.structureView.tree.ActionPresentation;
import consulo.fileEditor.structureView.tree.ActionPresentationData;
import consulo.fileEditor.structureView.tree.Filter;
import consulo.fileEditor.structureView.tree.TreeElement;
import consulo.ide.IdeBundle;

import javax.annotation.Nonnull;

/**
 * @author vlan
 */
public class PyFieldsFilter implements Filter {
  private static final String ID = "SHOW_FIELDS";

  @Override
  public boolean isReverted() {
    return true;
  }

  @Override
  public boolean isVisible(TreeElement treeNode) {
    if (treeNode instanceof PyStructureViewElement) {
      final PyStructureViewElement sve = (PyStructureViewElement)treeNode;
      return !sve.isField();
    }
    return true;
  }

  @Nonnull
  @Override
  public String getName() {
    return ID;
  }

  @Override
  public String toString() {
    return getName();
  }

  @Nonnull
  @Override
  public ActionPresentation getPresentation() {
    return new ActionPresentationData(IdeBundle.message("action.structureview.show.fields"), null, AllIcons.Nodes.Field);
  }
}
