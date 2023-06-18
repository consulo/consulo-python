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

package com.jetbrains.python.impl.buildout.config.psi;

import com.jetbrains.python.impl.buildout.config.BuildoutCfgElementTypes;
import com.jetbrains.python.impl.buildout.config.BuildoutCfgTokenTypes;
import com.jetbrains.python.impl.buildout.config.psi.impl.*;
import consulo.language.ast.ASTNode;
import consulo.language.ast.IElementType;
import consulo.language.psi.PsiElement;

/**
 * @author traff
 */
public class BuildoutCfgASTFactory implements BuildoutCfgElementTypes, BuildoutCfgTokenTypes {

  public PsiElement create(ASTNode node) {
    IElementType type = node.getElementType();
    if (type == SECTION) {
      return new BuildoutCfgSection(node);
    }
    if (type == SECTION_HEADER) {
      return new BuildoutCfgSectionHeader(node);
    }
    if (type == OPTION) {
      return new BuildoutCfgOption(node);
    }
    if (type == KEY) {
      return new BuildoutCfgKey(node);
    }
    if (type == VALUE) {
      return new BuildoutCfgValue(node);
    }
    if (type == VALUE_LINE) {
      return new BuildoutCfgValueLine(node);
    }
    if (type == SECTION_NAME) {
      return new BuildoutCfgSectionHeaderName(node);
    }
    
    return new BuildoutCfgPsiElement(node);
  }
}
