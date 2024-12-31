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

package com.jetbrains.python.impl;

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonLanguage;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.IndentedCommenter;
import consulo.language.CodeDocumentationAwareCommenter;
import consulo.language.Language;
import consulo.language.psi.PsiComment;
import consulo.language.ast.IElementType;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author yole
 */
@ExtensionImpl
public class PythonCommenter implements CodeDocumentationAwareCommenter, IndentedCommenter {
  public String getLineCommentPrefix() {
    return "#";
  }

  public String getBlockCommentPrefix() {
    return null;
  }

  public String getBlockCommentSuffix() {
    return null;
  }

  public String getCommentedBlockCommentPrefix() {
    return null;
  }

  public String getCommentedBlockCommentSuffix() {
    return null;
  }

  @Override
  public IElementType getLineCommentTokenType() {
    return PyTokenTypes.END_OF_LINE_COMMENT;
  }

  @Override
  public IElementType getBlockCommentTokenType() {
    return null;
  }

  @Override
  public IElementType getDocumentationCommentTokenType() {
    return null;
  }

  @Override
  public String getDocumentationCommentPrefix() {
    return null;
  }

  @Override
  public String getDocumentationCommentLinePrefix() {
    return null;
  }

  @Override
  public String getDocumentationCommentSuffix() {
    return null;
  }

  @Override
  public boolean isDocumentationComment(PsiComment element) {
    return false;
  }

  @Nullable
  @Override
  public Boolean forceIndentedLineComment() {
    return true;
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return PythonLanguage.INSTANCE;
  }
}
