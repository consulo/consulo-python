/*
 * Copyright 2006 Dmitry Jemerov (yole)
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

package ru.yole.pythonid.structureView;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import java.util.ArrayList;
import java.util.List;
import javax.swing.Icon;
import org.jetbrains.annotations.Nullable;
import ru.yole.pythonid.psi.PyElement;
import ru.yole.pythonid.psi.PyElementEx;
import ru.yole.pythonid.psi.PyElementVisitor;
import ru.yole.pythonid.psi.PyFunction;
import ru.yole.pythonid.psi.PyParameter;
import ru.yole.pythonid.psi.PyParameterList;

public class PyStructureViewElement
  implements StructureViewTreeElement<PyElement>
{
  private PyElement _element;

  public PyStructureViewElement(PyElement element)
  {
    this._element = element;
  }

  public PyElement getValue() {
    return this._element;
  }

  public void navigate(boolean requestFocus) {
    ((NavigationItem)this._element).navigate(requestFocus);
  }

  public boolean canNavigate() {
    return ((NavigationItem)this._element).canNavigate();
  }

  public boolean canNavigateToSource() {
    return ((NavigationItem)this._element).canNavigateToSource();
  }

  public StructureViewTreeElement[] getChildren() {
    final List childrenElements = new ArrayList();
    this._element.acceptChildren(new PyElementVisitor() {
      public void visitElement(PsiElement element) {
        if (((element instanceof PsiNamedElement)) && (((PsiNamedElement)element).getName() != null)) {
          childrenElements.add((PyElementEx)element);
        }
        else
          element.acceptChildren(this);
      }

      public void visitPyParameter(PyParameter node)
      {
      }
    });
    StructureViewTreeElement[] children = new StructureViewTreeElement[childrenElements.size()];
    for (int i = 0; i < children.length; i++) {
      children[i] = new PyStructureViewElement((PyElement)childrenElements.get(i));
    }

    return children;
  }

  public ItemPresentation getPresentation() {
    return new ItemPresentation() {
      public String getPresentableText() {
        if ((PyStructureViewElement.this._element instanceof PyFunction)) {
          PsiElement[] children = PyStructureViewElement.this._element.getChildren();
          if ((children.length > 0) && ((children[0] instanceof PyParameterList))) {
            PyParameterList argList = (PyParameterList)children[0];
            StringBuilder result = new StringBuilder(((PsiNamedElement)PyStructureViewElement.this._element).getName());
            result.append("(");
            boolean first = true;
            for (PsiElement e : argList.getChildren()) {
              if ((e instanceof PyParameter)) {
                if (first) {
                  first = false;
                }
                else {
                  result.append(",");
                }
                PyParameter p = (PyParameter)e;
                if (p.isPositionalContainer()) {
                  result.append("*");
                }
                else if (p.isKeywordContainer()) {
                  result.append("**");
                }
                result.append(p.getName());
              }
            }
            result.append(")");
            return result.toString();
          }
        }
        return ((PsiNamedElement)PyStructureViewElement.this._element).getName();
      }
      @Nullable
      public TextAttributesKey getTextAttributesKey() {
        return null;
      }
      @Nullable
      public String getLocationString() {
        return null;
      }

      public Icon getIcon(boolean open) {
        return PyStructureViewElement.this._element.getIcon(4);
      }
    };
  }
}