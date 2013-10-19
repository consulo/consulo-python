package com.jetbrains.python.psi.impl;

import java.util.ArrayList;
import java.util.List;

import org.consulo.psi.PsiPackage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.ide.IconDescriptorUpdaters;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaPackage;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;

/**
 * @author yole
 */
public class PyJavaPackageType implements PyType {
  private final PsiJavaPackage myPackage;
  @Nullable private final Module myModule;

  public PyJavaPackageType(PsiJavaPackage aPackage, @Nullable Module module) {
    myPackage = aPackage;
    myModule = module;
  }

  @Override
  public List<? extends RatedResolveResult> resolveMember(@NotNull String name,
                                                          @Nullable PyExpression location,
                                                          @NotNull AccessDirection direction,
                                                          @NotNull PyResolveContext resolveContext) {
    Project project = myPackage.getProject();
    JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    String childName = myPackage.getQualifiedName() + "." + name;
    GlobalSearchScope scope = getScope(project);
    ResolveResultList result = new ResolveResultList();
    final PsiClass[] classes = facade.findClasses(childName, scope);
    for (PsiClass aClass : classes) {
      result.poke(aClass, RatedResolveResult.RATE_NORMAL);
    }
    final PsiPackage psiPackage = facade.findPackage(childName);
    if (psiPackage != null) {
      result.poke(psiPackage, RatedResolveResult.RATE_NORMAL);
    }
    return result;
  }

  private GlobalSearchScope getScope(Project project) {
    return myModule != null ? myModule.getModuleWithDependenciesAndLibrariesScope(false) : ProjectScope.getAllScope(project);
  }

  @Override
  public Object[] getCompletionVariants(String completionPrefix, PsiElement location, ProcessingContext context) {
    List<Object> variants = new ArrayList<Object>();
    final GlobalSearchScope scope = getScope(location.getProject());
    final PsiClass[] classes = myPackage.getClasses(scope);
    for (PsiClass psiClass : classes) {
      variants.add(LookupElementBuilder.create(psiClass).withIcon(IconDescriptorUpdaters.getIcon(psiClass, 0)));
    }
    final PsiPackage[] subPackages = myPackage.getSubPackages(scope);
    for (PsiPackage subPackage : subPackages) {
      variants.add(LookupElementBuilder.create(subPackage).withIcon(IconDescriptorUpdaters.getIcon(subPackage, 0)));
    }
    return ArrayUtil.toObjectArray(variants);
  }

  @Override
  public String getName() {
    return myPackage.getQualifiedName();
  }

  @Override
  public boolean isBuiltin(TypeEvalContext context) {
    return false;
  }

  @Override
  public void assertValid(String message) {
  }
}
