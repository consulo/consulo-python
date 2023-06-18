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

package com.jetbrains.python.impl.codeInsight.liveTemplates;

import com.jetbrains.python.psi.PyClass;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.template.Expression;
import consulo.language.editor.template.ExpressionContext;
import consulo.language.editor.template.Result;
import consulo.language.editor.template.TextResult;
import consulo.language.editor.template.context.TemplateContextType;
import consulo.language.editor.template.macro.Macro;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author yole
 */
@ExtensionImpl
public class PyClassNameMacro extends Macro {
  @Override
  public String getName() {
    return "pyClassName";
  }

  @Override
  public String getPresentableName() {
    return "pyClassName()";
  }

  @Nullable
  @Override
  public Result calculateResult(@Nonnull Expression[] params, ExpressionContext context) {
    PsiElement place = context.getPsiElementAtStartOffset();
    PyClass pyClass = PsiTreeUtil.getParentOfType(place, PyClass.class);
    if (pyClass == null) {
      return null;
    }
    String name = pyClass.getName();
    return name == null ? null : new TextResult(name);
  }

  @Override
  public boolean isAcceptableInContext(TemplateContextType context) {
    return context instanceof PythonTemplateContextType;
  }
}
