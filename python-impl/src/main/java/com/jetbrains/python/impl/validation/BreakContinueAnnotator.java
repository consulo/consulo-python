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

package com.jetbrains.python.impl.validation;

import jakarta.annotation.Nullable;

import consulo.language.psi.util.PsiTreeUtil;
import com.jetbrains.python.impl.PyBundle;
import com.jetbrains.python.psi.PyBreakStatement;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyContinueStatement;
import com.jetbrains.python.psi.PyFinallyPart;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyLoopStatement;
import com.jetbrains.python.psi.PyStatement;

/**
 * Annotates misplaced 'break' and 'continue'.
 */
public class BreakContinueAnnotator extends PyAnnotator {
  @Override
  public void visitPyBreakStatement(final PyBreakStatement node) {
    if (getContainingLoop(node) == null) {
      getHolder().createErrorAnnotation(node, PyBundle.message("ANN.break.outside.loop"));
    }
  }

  @Nullable
  private static PyLoopStatement getContainingLoop(PyStatement node) {
    return PsiTreeUtil.getParentOfType(node, PyLoopStatement.class, false, PyFunction.class, PyClass.class);
  }

  @Override
  public void visitPyContinueStatement(final PyContinueStatement node) {
    if (getContainingLoop(node) == null) {
      getHolder().createErrorAnnotation(node, PyBundle.message("ANN.continue.outside.loop"));
    }
    else if (PsiTreeUtil.getParentOfType(node,  PyFinallyPart.class, false, PyLoopStatement.class) != null) {
      getHolder().createErrorAnnotation(node, PyBundle.message("ANN.cant.continue.in.finally"));
    }
  }
}