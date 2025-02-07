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

package com.jetbrains.python.psi.resolve;

import jakarta.annotation.Nonnull;

import consulo.module.Module;
import consulo.language.util.ModuleUtilCore;
import consulo.project.Project;
import consulo.content.bundle.Sdk;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import com.jetbrains.python.PyNames;

import jakarta.annotation.Nullable;

/**
 * @author yole
 */
public class QualifiedNameResolveContext {
  @Nullable private Module myModule;
  private PsiFile myFootholdFile;
  @Nonnull
  PsiManager myPsiManager;
  private Sdk mySdk;

  public void copyFrom(QualifiedNameResolveContext context) {
    myModule = context.getModule();
    myPsiManager = context.getPsiManager();
    mySdk = context.getSdk();
    myFootholdFile = context.getFootholdFile();
  }

  public void setFromElement(PsiElement foothold) {
    if (foothold instanceof PsiDirectory) {
      myFootholdFile = ((PsiDirectory)foothold).findFile(PyNames.INIT_DOT_PY);
    }
    else {
      myFootholdFile = foothold.getContainingFile().getOriginalFile();
    }
    myPsiManager = foothold.getManager();
    myModule = ModuleUtilCore.findModuleForPsiElement(foothold);
  }

  public void setFromModule(Module module) {
    myModule = module;
    myPsiManager = PsiManager.getInstance(module.getProject());
  }

  public void setFromSdk(Project project, Sdk sdk) {
    myPsiManager = PsiManager.getInstance(project);
    mySdk = sdk;
  }

  public void setSdk(Sdk sdk) {
    mySdk = sdk;
  }

  @Nullable
  public Module getModule() {
    return myModule;
  }

  public boolean isValid() {
    if (myFootholdFile != null) {
      return myFootholdFile.isValid();
    }
    return true;
  }

  @Nullable
  public PsiFile getFootholdFile() {
    return myFootholdFile;
  }

  @Nonnull
  public PsiManager getPsiManager() {
    return myPsiManager;
  }

  @Nonnull
  public Project getProject() {
    return myPsiManager.getProject();
  }

  public Sdk getSdk() {
    return mySdk;
  }
}
