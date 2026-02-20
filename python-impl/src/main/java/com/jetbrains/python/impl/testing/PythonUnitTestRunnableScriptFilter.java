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
package com.jetbrains.python.impl.testing;

import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyIfStatement;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.impl.run.RunnableScriptFilter;
import consulo.annotation.component.ExtensionImpl;
import consulo.execution.action.Location;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.module.Module;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author yole
 */
@ExtensionImpl
public class PythonUnitTestRunnableScriptFilter implements RunnableScriptFilter {
  public boolean isRunnableScript(PsiFile script, @Nonnull Module module, Location location, @Nullable TypeEvalContext context) {
    return script instanceof PyFile && PythonUnitTestUtil.getTestCaseClassesFromFile(script, context)
                                                         .size() > 0 && !isIfNameMain(location) && TestRunnerService.getInstance(module)
                                                                                                                    .getProjectConfiguration()
                                                                                                                    .
                                                                                                                      equals(
                                                                                                                        PythonTestConfigurationsModel.PYTHONS_UNITTEST_NAME);
  }

  public static boolean isIfNameMain(Location location) {
    PsiElement element = location.getPsiElement();
    while (true) {
      PyIfStatement ifStatement = PsiTreeUtil.getParentOfType(element, PyIfStatement.class);
      if (ifStatement == null) {
        break;
      }
      element = ifStatement;
    }
    if (element instanceof PyIfStatement) {
      PyIfStatement ifStatement = (PyIfStatement)element;
      return PyUtil.isIfNameEqualsMain(ifStatement);
    }
    return false;
  }
}
