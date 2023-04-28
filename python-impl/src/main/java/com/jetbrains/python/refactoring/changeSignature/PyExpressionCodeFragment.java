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

package com.jetbrains.python.refactoring.changeSignature;

import javax.annotation.Nonnull;

import consulo.language.impl.file.SingleRootFileViewProvider;
import consulo.language.impl.internal.psi.PsiManagerEx;
import consulo.project.Project;
import consulo.language.psi.PsiCodeFragment;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiManager;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.file.light.LightVirtualFile;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.psi.impl.PyFileImpl;
import org.jetbrains.annotations.NonNls;

/**
 * User : ktisha
 */

public class PyExpressionCodeFragment extends PyFileImpl implements PsiCodeFragment {

  private GlobalSearchScope myResolveScope;

  public PyExpressionCodeFragment(@Nonnull final Project project,
                                  @NonNls final String name,
                                  @Nonnull final CharSequence text) {
    super(((PsiManagerEx)PsiManager.getInstance(project)).getFileManager().createFileViewProvider(
      new LightVirtualFile(name, PythonFileType.INSTANCE, text), true));
    ((SingleRootFileViewProvider)getViewProvider()).forceCachedPsi(this);
  }

  @Override
  public void forceResolveScope(GlobalSearchScope scope) {
    myResolveScope = scope;
  }

  @Override
  public GlobalSearchScope getForcedResolveScope() {
    return myResolveScope;
  }

  @Override
  public boolean isValid() {
    return true;
  }

  @Override
  public void accept(@Nonnull PsiElementVisitor visitor) {
  }

  public boolean isAcceptedFor(@Nonnull Class visitorClass) {
    return false;
  }
}
