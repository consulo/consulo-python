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

package com.jetbrains.python.impl.debugger;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import consulo.document.Document;
import consulo.document.FileDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.application.util.function.Processor;
import consulo.execution.debug.XDebugSession;
import consulo.execution.debug.XDebuggerUtil;
import consulo.execution.debug.XSourcePosition;
import consulo.execution.debug.step.XSmartStepIntoHandler;
import consulo.execution.debug.step.XSmartStepIntoVariant;
import com.jetbrains.python.impl.PyBundle;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyExpression;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.util.List;
import java.util.Set;

/**
 * @author traff
 */
public class PySmartStepIntoHandler extends XSmartStepIntoHandler<PySmartStepIntoHandler.PySmartStepIntoVariant> {
  private final XDebugSession mySession;
  private PyDebugProcess myProcess;

  public PySmartStepIntoHandler(final PyDebugProcess process) {
    mySession = process.getSession();
    myProcess = process;
  }

  @Nonnull
  public List<PySmartStepIntoVariant> computeSmartStepVariants(@Nonnull XSourcePosition position) {
    final Document document = FileDocumentManager.getInstance().getDocument(position.getFile());
    final List<PySmartStepIntoVariant> variants = Lists.newArrayList();
    final Set<PyCallExpression> visitedCalls = Sets.newHashSet();

    final int line = position.getLine();
    XDebuggerUtil.getInstance().iterateLine(mySession.getProject(), document, line, new Processor<PsiElement>() {
      public boolean process(PsiElement psiElement) {
        addVariants(document, line, psiElement, variants, visitedCalls);
        return true;
      }
    });

    return variants;
  }

  @Override
  public void startStepInto(PySmartStepIntoVariant smartStepIntoVariant) {
    myProcess.startSmartStepInto(smartStepIntoVariant.getFunctionName());
  }

  public String getPopupTitle(@Nonnull XSourcePosition position) {
    return PyBundle.message("debug.popup.title.step.into.function");
  }

  private static void addVariants(Document document, int line, @Nullable PsiElement element,
                                  List<PySmartStepIntoVariant> variants,
                                  Set<PyCallExpression> visited) {
    if (element == null) return;

    final PyCallExpression expression = PsiTreeUtil.getParentOfType(element, PyCallExpression.class);
    if (expression != null &&
        expression.getTextRange().getEndOffset() <= document.getLineEndOffset(line) &&
        visited.add(expression)) {
      addVariants(document, line, expression.getParent(), variants, visited);
      PyExpression ref = expression.getCallee();

      variants.add(new PySmartStepIntoVariant(ref));
    }
  }

  public static class PySmartStepIntoVariant extends XSmartStepIntoVariant {
    //private final String myFunctionName;

    private final PyElement myElement;

    public PySmartStepIntoVariant(PyElement element) {
      myElement = element;
    }

    @Override
    public String getText() {
      return myElement.getText() + "()";
    }

    public String getFunctionName() {
      String name = myElement.getName();
      return name != null ? name : getText();
    }
  }
}
