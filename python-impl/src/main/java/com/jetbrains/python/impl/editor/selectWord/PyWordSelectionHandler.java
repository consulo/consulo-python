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

package com.jetbrains.python.impl.editor.selectWord;

import com.jetbrains.python.PyTokenTypes;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.ast.ASTNode;
import consulo.language.editor.action.AbstractWordSelectioner;
import consulo.language.psi.PsiElement;

/**
 * @author yole
 */
@ExtensionImpl
public class PyWordSelectionHandler extends AbstractWordSelectioner {
  public boolean canSelect(PsiElement e) {
    ASTNode astNode = e.getNode();
    return astNode != null && astNode.getElementType() == PyTokenTypes.IDENTIFIER;
  }
}
