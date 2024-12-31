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

package com.jetbrains.python.impl.buildout.config.psi.impl;

import com.google.common.collect.Lists;
import consulo.language.ast.ASTNode;
import consulo.language.psi.util.PsiTreeUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * @author traff
 */
public class BuildoutCfgOption extends BuildoutCfgPsiElement {
  public BuildoutCfgOption(@Nonnull final ASTNode node) {
    super(node);
  }

  @Nullable
  public String getKey() {
    BuildoutCfgKey key = PsiTreeUtil.findChildOfType(this, BuildoutCfgKey.class);
    String result = key != null ? key.getText() : null;

    return result != null ? result.trim() : null;
  }

  public List<String> getValues() {
    List<String> result = Lists.newArrayList();
    Collection<BuildoutCfgValueLine> lines = PsiTreeUtil.collectElementsOfType(this, BuildoutCfgValueLine.class);
    for (BuildoutCfgValueLine line : lines) {
      String text = line.getText();
      if (text != null) {
        result.add(text.trim());
      }
    }
    return result;
  }

  @Override
  public String toString() {
    return "BuildoutCfgOption:" + getNode().getElementType().toString();
  }

}
