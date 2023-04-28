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

package com.jetbrains.python.codeInsight.intentions;

import com.jetbrains.python.PyBundle;
import com.jetbrains.python.codeInsight.codeFragment.PyCodeFragmentUtil;
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyFunctionBuilder;
import com.jetbrains.python.refactoring.introduce.IntroduceValidator;
import consulo.codeEditor.Editor;
import consulo.ide.impl.idea.codeInsight.controlflow.ControlFlow;
import consulo.language.editor.CodeInsightUtilCore;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.intention.BaseIntentionAction;
import consulo.language.editor.template.*;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.List;

/**
 * User: catherine
 * Intention to convert lambda to function
 */
public class PyConvertLambdaToFunctionIntention extends BaseIntentionAction {

  @Nonnull
  public String getFamilyName() {
    return PyBundle.message("INTN.convert.lambda.to.function");
  }

  @Nonnull
  public String getText() {
    return PyBundle.message("INTN.convert.lambda.to.function");
  }

  public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
    if (!(file instanceof PyFile)) {
      return false;
    }

    PyLambdaExpression lambdaExpression = PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), PyLambdaExpression.class);
    if (lambdaExpression != null) {
      if (lambdaExpression.getBody() != null) {
        final ControlFlow flow = ControlFlowCache.getControlFlow(lambdaExpression);
        final List<consulo.ide.impl.idea.codeInsight.controlflow.Instruction> graph = Arrays.asList(flow.getInstructions());
        final List<PsiElement> elements = PyCodeFragmentUtil.getInputElements(graph, graph);
        if (elements.size() > 0) return false;
        return true;
      }
    }
    return false;
  }

  public void invoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException
  {
    PyLambdaExpression lambdaExpression = PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), PyLambdaExpression.class);
    if (lambdaExpression != null) {
      String name = "function";
      while (IntroduceValidator.isDefinedInScope(name, lambdaExpression)) {
        name += "1";
      }

      PsiElement parent = lambdaExpression.getParent();
      if (parent instanceof PyAssignmentStatement) {
        name = ((PyAssignmentStatement)parent).getLeftHandSideExpression().getText();
      }

      if (name.isEmpty()) return;
      PyExpression body = lambdaExpression.getBody();
      PyFunctionBuilder functionBuilder = new PyFunctionBuilder(name);
      for (PyParameter param : lambdaExpression.getParameterList().getParameters()) {
        functionBuilder.parameter(param.getText());
      }
      functionBuilder.statement("return " + body.getText());
      PyFunction function = functionBuilder.buildFunction(project, LanguageLevel.getDefault());

      final PyStatement statement = PsiTreeUtil.getParentOfType(lambdaExpression,
                                                                 PyStatement.class);
      if (statement != null) {
        final PsiElement statementParent = statement.getParent();
        if (statementParent != null)
          function = (PyFunction)statementParent.addBefore(function, statement);
      }

      function = CodeInsightUtilCore
        .forcePsiPostprocessAndRestoreElement(function);

      if (parent instanceof PyAssignmentStatement) {
        parent.delete();
      }
      else {
        PsiFile parentScope = lambdaExpression.getContainingFile();
        final TemplateBuilder builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(parentScope);
        PsiElement functionName = function.getNameIdentifier();
        functionName = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(functionName);
        lambdaExpression = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(lambdaExpression);

        ReferenceNameExpression refExpr = new ReferenceNameExpression(name);

        builder.replaceElement(lambdaExpression, name, refExpr, true);
        builder.replaceElement(functionName, name, name, false);

        int textOffSet = functionName.getTextOffset();
        editor.getCaretModel().moveToOffset(parentScope.getTextRange().getStartOffset());

        Template template = builder.buildInlineTemplate();
        TemplateManager.getInstance(project).startTemplate(editor, template);
        editor.getCaretModel().moveToOffset(textOffSet);
      }
    }
  }
  private class ReferenceNameExpression extends Expression {
    ReferenceNameExpression(String oldReferenceName) {
      myOldReferenceName = oldReferenceName;
    }

    private final String myOldReferenceName;

    public Result calculateResult(ExpressionContext context) {
      return new TextResult(myOldReferenceName);
    }

    public Result calculateQuickResult(ExpressionContext context) {
      return null;
    }

    @Override
    public LookupElement[] calculateLookupItems(ExpressionContext context) {
      return null;
    }
  }
}
