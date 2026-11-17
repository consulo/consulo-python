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

package com.jetbrains.python.impl.projectView;

import com.jetbrains.python.psi.impl.PyPsiUtils;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.ReadAction;
import consulo.util.lang.ref.SimpleReference;
import consulo.project.ui.view.tree.PsiFileNode;
import consulo.project.ui.view.tree.SelectableTreeStructureProvider;
import consulo.project.ui.view.tree.ViewSettings;
import consulo.project.ui.view.tree.AbstractTreeNode;
import consulo.application.dumb.DumbAware;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.PyDocStringOwner;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author yole
 */
@ExtensionImpl
public class PyTreeStructureProvider implements SelectableTreeStructureProvider, DumbAware {
  @Override
  public Collection<AbstractTreeNode> modify(AbstractTreeNode parent, Collection<AbstractTreeNode> children, ViewSettings settings) {
    // TODO consulo: PythonInterpreter / PythonInterpreterRegistry / EvoPyProject API missing - JB adds PySkeletonsNode,
    //  PyRemoteLibrariesNode, PyTypeShedNode, PyBundledStubsNode under the interpreter library node, hides skeleton
    //  directories (hideSkeletons) and draws interpreter libraries via PyInterpreterLibraryNode
    if (settings != null && settings.isShowMembers()) {
      List<AbstractTreeNode> newChildren = new ArrayList<AbstractTreeNode>();
      for (AbstractTreeNode child : children) {
        if (child instanceof PsiFileNode && ((PsiFileNode)child).getValue() instanceof PyFile) {
          newChildren.add(new PyFileNode(parent.getProject(), ((PsiFileNode)child).getValue(), settings));
        }
        else {
          newChildren.add(child);
        }
      }
      return newChildren;
    }
    return children;
  }

  @Override
  public PsiElement getTopLevelElement(PsiElement element) {
    PyPsiUtils.assertValid(element);
    SimpleReference<PsiFile> containingFileRef = SimpleReference.create();
    ReadAction.run(() -> containingFileRef.set(element.getContainingFile()));
    PsiFile containingFile = containingFileRef.get();
    if (!(containingFile instanceof PyFile)) {
      return null;
    }
    List<PsiElement> parents = new ArrayList<PsiElement>();
    PyDocStringOwner container = PsiTreeUtil.getParentOfType(element, PyDocStringOwner.class);
    while (container != null) {
      if (container instanceof PyFile) {
        break;
      }
      parents.add(0, container);
      container = PsiTreeUtil.getParentOfType(container, PyDocStringOwner.class);
    }
    for (PsiElement parent : parents) {
      if (parent instanceof PyFunction) {
        return parent;     // we don't display any nodes under functions
      }
    }
    if (!parents.isEmpty()) {
      return parents.get(parents.size()-1);
    }
    return element.getContainingFile();    
  }
}
