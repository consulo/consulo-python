// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.actions;

import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.resolve.PyResolveImportUtil;
import com.jetbrains.python.psi.resolve.PyQualifiedNameResolveContext;
import consulo.module.ModuleManager;
import consulo.module.Module;
import consulo.language.psi.util.QualifiedName;
import java.util.List;
import consulo.language.psi.scope.GlobalSearchScope;
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

import org.jspecify.annotations.Nullable;
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
      return ((PyFunction)element).getQualifiedName();
    }
    return null;
  }

  @Nullable
  @Override
  public PsiElement qualifiedNameToElement(String fqn, Project project) {
    GlobalSearchScope entireProjectScope = GlobalSearchScope.allScope(project);
    List<PyClass> classes = PyClassNameIndex.findByQualifiedName(fqn, project, entireProjectScope);
    if (!classes.isEmpty()) {
      return classes.get(0);
    }
    List<PyFunction> functions = PyFunctionNameIndex.findByQualifiedName(fqn, project, entireProjectScope);
    if (!functions.isEmpty()) {
      return functions.get(0);
    }
    return qualifiedNameToModule(fqn, project);
  }

  /**
   * Resolves a module/package qualified name (e.g. {@code collections}) to its file, turning a package
   * directory into its {@code __init__} file so navigation lands on the module rather than the directory.
   */
  private static @Nullable PsiElement qualifiedNameToModule(String fqn, Project project) {
    QualifiedName qualifiedName = QualifiedName.fromDottedString(fqn);
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      PyQualifiedNameResolveContext context = PyResolveImportUtil.fromModule(module);
      for (PsiElement resolved : PyResolveImportUtil.resolveQualifiedName(qualifiedName, context)) {
        if (PyUtil.turnDirIntoInit(resolved) instanceof PyFile file) {
          return file;
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
