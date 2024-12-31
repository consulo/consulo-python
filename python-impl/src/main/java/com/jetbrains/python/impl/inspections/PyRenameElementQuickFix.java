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

package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyTargetExpression;
import consulo.application.ApplicationManager;
import consulo.codeEditor.Editor;
import consulo.content.scope.SearchScope;
import consulo.fileEditor.FileEditorManager;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.refactoring.rename.PsiElementRenameHandler;
import consulo.language.editor.refactoring.rename.RenameProcessor;
import consulo.language.editor.refactoring.rename.RenamePsiElementProcessor;
import consulo.language.editor.refactoring.rename.inplace.VariableInplaceRenamer;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiNameIdentifierOwner;
import consulo.language.psi.scope.LocalSearchScope;
import consulo.language.psi.search.PsiSearchHelper;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.navigation.OpenFileDescriptorFactory;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * User: ktisha
 */
public class PyRenameElementQuickFix implements LocalQuickFix {
  @Nonnull
  @Override
  public String getName() {
    return "Rename element";
  }

  @Nonnull
  @Override
  public String getFamilyName() {
    return "Rename element";
  }

  @Override
  public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
    final PsiElement element = descriptor.getPsiElement();
    final PsiNameIdentifierOwner nameOwner = element instanceof PsiNameIdentifierOwner ?
      (PsiNameIdentifierOwner)element :
      PsiTreeUtil.getParentOfType(element, PsiNameIdentifierOwner.class, true);
    if (nameOwner != null) {
      final VirtualFile virtualFile = nameOwner.getContainingFile().getVirtualFile();
      if (virtualFile != null) {
        final Editor editor = FileEditorManager.getInstance(project)
                                               .openTextEditor(OpenFileDescriptorFactory.getInstance(project).builder(virtualFile).build(),
                                                               true);
        if (ApplicationManager.getApplication().isUnitTestMode()) {
          renameInUnitTestMode(project, nameOwner, editor);
        }
        else {
          if (checkLocalScope(element) != null && (nameOwner instanceof PyNamedParameter || nameOwner instanceof PyTargetExpression)) {
            new VariableInplaceRenamer(nameOwner, editor).performInplaceRename();
          }
          else {
            PsiElementRenameHandler.invoke(nameOwner, project, ScopeUtil.getScopeOwner(nameOwner), editor);
          }
        }
      }
    }
  }

  @Nullable
  protected PsiElement checkLocalScope(PsiElement element) {
    final SearchScope searchScope = PsiSearchHelper.SERVICE.getInstance(element.getProject()).getUseScope(element);
    if (searchScope instanceof LocalSearchScope) {
      final PsiElement[] elements = ((LocalSearchScope)searchScope).getScope();
      return PsiTreeUtil.findCommonParent(elements);
    }

    return null;
  }

  private static void renameInUnitTestMode(@Nonnull Project project, @Nonnull PsiNameIdentifierOwner nameOwner,
                                           @Nullable Editor editor) {
    final PsiElement substitution = RenamePsiElementProcessor.forElement(nameOwner).substituteElementToRename(nameOwner, editor);
    if (substitution != null) {
      new RenameProcessor(project, substitution, "a", false, false).run();
    }
  }
}
