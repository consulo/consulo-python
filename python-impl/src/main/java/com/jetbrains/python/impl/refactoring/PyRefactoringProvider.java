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

package com.jetbrains.python.impl.refactoring;

import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.impl.refactoring.changeSignature.PyChangeSignatureHandler;
import com.jetbrains.python.impl.refactoring.classes.extractSuperclass.PyExtractSuperclassHandler;
import com.jetbrains.python.impl.refactoring.classes.pullUp.PyPullUpHandler;
import com.jetbrains.python.impl.refactoring.classes.pushDown.PyPushDownHandler;
import com.jetbrains.python.impl.refactoring.extractmethod.PyExtractMethodHandler;
import com.jetbrains.python.impl.refactoring.introduce.constant.PyIntroduceConstantHandler;
import com.jetbrains.python.impl.refactoring.introduce.field.PyIntroduceFieldHandler;
import com.jetbrains.python.impl.refactoring.introduce.parameter.PyIntroduceParameterHandler;
import com.jetbrains.python.impl.refactoring.introduce.variable.PyIntroduceVariableHandler;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.editor.refactoring.RefactoringSupportProvider;
import consulo.language.editor.refactoring.action.RefactoringActionHandler;
import consulo.language.editor.refactoring.changeSignature.ChangeSignatureHandler;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author Alexey.Ivanov
 */
@ExtensionImpl
public class PyRefactoringProvider extends RefactoringSupportProvider {
  @Override
  public RefactoringActionHandler getIntroduceVariableHandler() {
    return new PyIntroduceVariableHandler();
  }

  @Override
  public RefactoringActionHandler getIntroduceConstantHandler() {
    return new PyIntroduceConstantHandler();
  }

  @Override
  public RefactoringActionHandler getIntroduceFieldHandler() {
    return new PyIntroduceFieldHandler();
  }

  @Override
  public RefactoringActionHandler getPullUpHandler() {
    return new PyPullUpHandler();
  }

  @Override
  public RefactoringActionHandler getPushDownHandler() {
    return new PyPushDownHandler();
  }

  @Override
  public RefactoringActionHandler getExtractSuperClassHandler() {
    return new PyExtractSuperclassHandler();
  }

  @Override
  public RefactoringActionHandler getExtractMethodHandler() {
    return new PyExtractMethodHandler();
  }

  @Override
  public boolean isInplaceRenameAvailable(@Nonnull PsiElement element, PsiElement context) {
    if (context != null && context.getContainingFile() != element.getContainingFile()) return false;
    PyFunction containingFunction = PsiTreeUtil.getParentOfType(element, PyFunction.class);
    if (containingFunction != null) {
      if (element instanceof PyTargetExpression || element instanceof PyFunction || element instanceof PyClass) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  @Override
  public ChangeSignatureHandler getChangeSignatureHandler() {
    return new PyChangeSignatureHandler();
  }

  @Nullable
  @Override
  public RefactoringActionHandler getIntroduceParameterHandler() {
    return new PyIntroduceParameterHandler();
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return PythonLanguage.INSTANCE;
  }
}
