/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.impl.actions;

import com.jetbrains.python.impl.psi.stubs.PyClassNameIndex;
import com.jetbrains.python.impl.psi.stubs.PyFunctionNameIndex;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.codeEditor.util.EditorModificationUtil;
import consulo.language.editor.QualifiedNameProvider;
import consulo.language.psi.PsiElement;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.StringUtil;

import jakarta.annotation.Nullable;
import java.util.Collection;


/**
 * User: anna
 * Date: 3/29/11
 */
@ExtensionImpl
public class PyQualifiedNameProvider implements QualifiedNameProvider {
  @Override
  public PsiElement adjustElementToCopy(PsiElement element) {
    return element instanceof PyClass || element instanceof PyFunction ? element : null;
  }

  @Nullable
  @Override
  public String getQualifiedName(PsiElement element) {
    if (element instanceof PyClass) {
      return ((PyClass)element).getQualifiedName();
    }
    if (element instanceof PyFunction) {
      PyClass containingClass = ((PyFunction)element).getContainingClass();
      if (containingClass != null) {
        return containingClass.getQualifiedName() + "#" + ((PyFunction)element).getName();
      }
      else {
        return ((PyFunction)element).getQualifiedName();
      }
    }
    return null;
  }

  @Nullable
  @Override
  public PsiElement qualifiedNameToElement(String fqn, Project project) {
    PyClass aClass = PyClassNameIndex.findClass(fqn, project);
    if (aClass != null) {
      return aClass;
    }
    Collection<PyFunction> functions = PyFunctionNameIndex.find(fqn, project);
    if (!functions.isEmpty()) {
      return ContainerUtil.getFirstItem(functions);
    }
    int sharpIdx = fqn.indexOf("#");
    if (sharpIdx > -1) {
      String className = StringUtil.getPackageName(fqn, '#');
      aClass = PyClassNameIndex.findClass(className, project);
      if (aClass != null) {
        String memberName = StringUtil.getShortName(fqn, '#');
        PyClass nestedClass = aClass.findNestedClass(memberName, false);
        if (nestedClass != null) {
          return nestedClass;
        }
        PyFunction methodByName = aClass.findMethodByName(memberName, false, null);
        if (methodByName != null) {
          return methodByName;
        }
      }
    }
    return null;
  }

  @Override
  public void insertQualifiedName(String fqn, PsiElement element, Editor editor, Project project) {
    EditorModificationUtil.insertStringAtCaret(editor, fqn);
  }
}
