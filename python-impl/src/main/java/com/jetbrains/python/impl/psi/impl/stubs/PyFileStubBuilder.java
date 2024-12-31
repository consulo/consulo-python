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

package com.jetbrains.python.impl.psi.impl.stubs;

import jakarta.annotation.Nonnull;

import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.stub.DefaultStubBuilder;
import consulo.language.psi.stub.StubElement;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyIfStatement;
import com.jetbrains.python.impl.psi.PyUtil;

/**
 * @author yole
 */
public class PyFileStubBuilder extends DefaultStubBuilder {
  @Override
  protected StubElement createStubForFile(@Nonnull PsiFile file) {
    if (file instanceof PyFile) {
      return new PyFileStubImpl((PyFile)file);
    }

    return super.createStubForFile(file);
  }

  @Override
  protected boolean skipChildProcessingWhenBuildingStubs(@Nonnull PsiElement parent, @Nonnull PsiElement element) {
    return parent instanceof PyIfStatement && PyUtil.isIfNameEqualsMain((PyIfStatement)parent);
  }

  @Override
  public boolean skipChildProcessingWhenBuildingStubs(@Nonnull ASTNode parent, @Nonnull ASTNode node) {
    PsiElement psi = parent.getPsi();
    return psi instanceof PyIfStatement && PyUtil.isIfNameEqualsMain((PyIfStatement)psi);
  }
}
