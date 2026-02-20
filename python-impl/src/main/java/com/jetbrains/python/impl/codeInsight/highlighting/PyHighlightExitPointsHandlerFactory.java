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

package com.jetbrains.python.impl.codeInsight.highlighting;

import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyReturnStatement;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.language.editor.TargetElementUtil;
import consulo.language.editor.highlight.usage.HighlightUsagesHandlerBase;
import consulo.language.editor.highlight.usage.HighlightUsagesHandlerFactory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;

/**
 * @author oleg
 */
@ExtensionImpl
public class PyHighlightExitPointsHandlerFactory implements HighlightUsagesHandlerFactory {
  @Override
  public HighlightUsagesHandlerBase createHighlightUsagesHandler(Editor editor, PsiFile file) {
    int offset = TargetElementUtil.adjustOffset(file, editor.getDocument(), editor.getCaretModel().getOffset());
    PsiElement target = file.findElementAt(offset);
    if (target != null) {
      PyReturnStatement returnStatement = PsiTreeUtil.getParentOfType(target, PyReturnStatement.class);
      if (returnStatement != null) {
        PyExpression returnExpr = returnStatement.getExpression();
        if (returnExpr == null || !PsiTreeUtil.isAncestor(returnExpr, target, false)) {
          return new PyHighlightExitPointsHandler(editor, file, target);
        }
      }
    }
    return null;
  }
}
