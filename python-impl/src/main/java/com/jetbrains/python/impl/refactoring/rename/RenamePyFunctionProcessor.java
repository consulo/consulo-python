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

package com.jetbrains.python.impl.refactoring.rename;

import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.language.editor.refactoring.rename.PsiElementRenameHandler;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.MessageBoxBuilder;
import consulo.ui.MessageButtonRole;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.application.util.function.Processor;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.codeInsight.PyCodeInsightSettings;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.impl.psi.search.PyOverridingMethodsSearch;
import com.jetbrains.python.impl.psi.search.PySuperMethodsSearch;
import com.jetbrains.python.toolbox.Maybe;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * @author yole
 */
@ExtensionImpl(id = "pyFunc", order = "before pyVar")
public class RenamePyFunctionProcessor extends RenamePyElementProcessor {
  @Override
  public boolean canProcessElement(PsiElement element) {
    return element instanceof PyFunction;
  }

  @Override
  public boolean forcesShowPreview() {
    return true;
  }

  @Override
  public boolean isToSearchInComments(PsiElement element) {
    return PyCodeInsightSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_FUNCTION;
  }

  @Override
  public void setToSearchInComments(PsiElement element, boolean enabled) {
    PyCodeInsightSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_FUNCTION = enabled;
  }

  @Override
  public boolean isToSearchForTextOccurrences(PsiElement element) {
    return PyCodeInsightSettings.getInstance().RENAME_SEARCH_NON_CODE_FOR_FUNCTION;
  }

  @Override
  public void setToSearchForTextOccurrences(PsiElement element, boolean enabled) {
    PyCodeInsightSettings.getInstance().RENAME_SEARCH_NON_CODE_FOR_FUNCTION = enabled;
  }

  @Override
  @RequiredUIAccess
  public @Nullable PsiElement substituteElementToRename(PsiElement element, Editor editor) {
    return substitute(element).getNow(null);
  }

  @Override
  @RequiredUIAccess
  public void substituteElementToRename(PsiElement element, Editor editor, Consumer<PsiElement> renameCallback) {
    substitute(element).thenAccept(substituted -> {
      if (substituted != null && PsiElementRenameHandler.canRename(substituted.getProject(), editor, substituted)) {
        renameCallback.accept(substituted);
      }
    });
  }

  @RequiredUIAccess
  private static CompletableFuture<@Nullable PsiElement> substitute(PsiElement element) {
    PyFunction function = (PyFunction) element;
    PyClass containingClass = function.getContainingClass();
    if (containingClass == null) {
      return CompletableFuture.completedFuture(function);
    }
    if (PyNames.INIT.equals(function.getName())) {
      return CompletableFuture.completedFuture(containingClass);
    }
    PyFunction deepestSuperMethod = PySuperMethodsSearch.findDeepestSuperMethod(function);
    if (!deepestSuperMethod.equals(function)) {
      String message = "Method " + function.getName() + " of class " + containingClass.getQualifiedName() + "\noverrides method of class "
                       + deepestSuperMethod.getContainingClass().getQualifiedName() + ".\nDo you want to rename the base method?";
      return ask(element.getProject(), message, deepestSuperMethod, function);
    }
    Property property = containingClass.findPropertyByCallable(function);
    if (property != null) {
      PyTargetExpression site = property.getDefinitionSite();
      if (site != null) {
        String message = String.format("Do you want to rename the property '%s' instead of its accessor function '%s'?",
                                       property.getName(), function.getName());
        return ask(element.getProject(), message, site, function);
      }
    }
    return CompletableFuture.completedFuture(function);
  }

  @RequiredUIAccess
  private static CompletableFuture<@Nullable PsiElement> ask(Project project, String message, PsiElement yes, PsiElement no) {
    if (project.getApplication().isUnitTestMode()) {
      return CompletableFuture.completedFuture(yes);
    }
    return MessageBoxBuilder.<@Nullable PsiElement>create()
      .asQuestion()
      .title(LocalizeValue.of("Rename"))
      .text(LocalizeValue.of(message))
      .button(MessageButtonRole.YES, yes)
      .asDefaultButton()
      .button(MessageButtonRole.NO, no)
      .button(MessageButtonRole.CANCEL, (PsiElement) null)
      .asExitButton()
      .showAsync(project);
  }

  @Override
  public void prepareRenaming(PsiElement element, final String newName, final Map<PsiElement, String> allRenames) {
    PyFunction function = (PyFunction) element;
    PyOverridingMethodsSearch.search(function, true).forEach(new Processor<PyFunction>() {
      @Override
      public boolean process(PyFunction pyFunction) {
        allRenames.put(pyFunction, newName);
        return true;
      }
    });
    PyClass containingClass = function.getContainingClass();
    if (containingClass != null) {
      Property property = containingClass.findPropertyByCallable(function);
      if (property != null) {
        addRename(allRenames, newName, property.getGetter());
        addRename(allRenames, newName, property.getSetter());
        addRename(allRenames, newName, property.getDeleter());
      }
    }
  }

  private static void addRename(Map<PsiElement, String> renames, String newName, Maybe<PyCallable> accessor) {
    PyCallable callable = accessor.valueOrNull();
    if (callable instanceof PyFunction) {
      renames.put(callable, newName);
    }
  }
}
