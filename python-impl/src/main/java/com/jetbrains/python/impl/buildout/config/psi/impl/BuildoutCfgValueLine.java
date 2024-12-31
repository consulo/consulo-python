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
import com.jetbrains.python.impl.buildout.config.psi.BuildoutPsiUtil;
import com.jetbrains.python.impl.buildout.config.ref.BuildoutPartReference;
import consulo.ide.impl.idea.openapi.util.text.StringUtil;
import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiReference;
import consulo.util.lang.Pair;

import jakarta.annotation.Nonnull;
import java.util.List;

/**
 * @author traff
 */
public class BuildoutCfgValueLine extends BuildoutCfgPsiElement {
  public BuildoutCfgValueLine(@Nonnull final ASTNode node) {
    super(node);
  }

  @Override
  public String toString() {
    return "BuildoutCfgValue:" + getNode().getElementType().toString();
  }

  @Nonnull
  @Override
  public PsiReference[] getReferences() {
    if (BuildoutPsiUtil.isInBuildoutSection(this) && BuildoutPsiUtil.isAssignedTo(this, "parts")) {
      List<BuildoutPartReference> refs = Lists.newArrayList();
      List<Pair<String, Integer>> names = StringUtil.getWordsWithOffset(getText());
      for (Pair<String, Integer> name : names) {
        refs.add(new BuildoutPartReference(this, name.getFirst(), name.getSecond()));
      }
      return refs.toArray(new BuildoutPartReference[refs.size()]);
    }

    return PsiReference.EMPTY_ARRAY;
  }
}
