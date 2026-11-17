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
package com.jetbrains.python;

import consulo.annotation.component.ExtensionImpl;
import consulo.language.CodeDocumentationAwareCommenter;
import consulo.language.IndentedCommenter;
import consulo.language.Language;
import consulo.language.ast.IElementType;
import consulo.language.psi.PsiComment;
import org.jspecify.annotations.Nullable;


@ExtensionImpl
public final class PythonCommenter implements CodeDocumentationAwareCommenter, IndentedCommenter {
  @Override
  public String getLineCommentPrefix() {
    return "# ";
  }

  @Override
  public @Nullable String getBlockCommentPrefix() {
    return null;
  }

  @Override
  public @Nullable String getBlockCommentSuffix() {
    return null;
  }

  @Override
  public @Nullable String getCommentedBlockCommentPrefix() {
    return null;
  }

  @Override
  public @Nullable String getCommentedBlockCommentSuffix() {
    return null;
  }

  @Override
  public IElementType getLineCommentTokenType() {
    return PyTokenTypes.END_OF_LINE_COMMENT;
  }

  @Override
  public @Nullable IElementType getBlockCommentTokenType() {
    return null;
  }

  @Override
  public IElementType getDocumentationCommentTokenType() {
    return PyTokenTypes.DOCSTRING;
  }

  @Override
  public @Nullable String getDocumentationCommentPrefix() {
    return null;
  }

  @Override
  public @Nullable String getDocumentationCommentLinePrefix() {
    return null;
  }

  @Override
  public @Nullable String getDocumentationCommentSuffix() {
    return null;
  }

  @Override
  public boolean isDocumentationComment(PsiComment element) {
    return false;
  }

  @Override
  public @Nullable Boolean forceIndentedLineComment() {
    return true;
  }

  @Override
  public Language getLanguage() {
    return PythonLanguage.INSTANCE;
  }
}
