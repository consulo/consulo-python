package com.jetbrains.python.psi.impl;

import org.jetbrains.annotations.Nullable;
import com.intellij.openapi.module.Module;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaPackage;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.psi.resolve.QualifiedNameResolveContext;

/**
 * @author yole
 */
public class PyJavaImportResolver implements PyImportResolver {
  @Nullable
  public PsiElement resolveImportReference(QualifiedName name, QualifiedNameResolveContext context) {
    String fqn = name.toString();
    final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(context.getProject());
    if (psiFacade == null) return null;
    final PsiJavaPackage aPackage = psiFacade.findPackage(fqn);
    if (aPackage != null) {
      return aPackage;
    }

    Module module = context.getModule();
    if (module != null) {
      final PsiClass aClass = psiFacade.findClass(fqn, module.getModuleWithDependenciesAndLibrariesScope(false));
      if (aClass != null) return aClass;
    }
    return null;
  }
}
