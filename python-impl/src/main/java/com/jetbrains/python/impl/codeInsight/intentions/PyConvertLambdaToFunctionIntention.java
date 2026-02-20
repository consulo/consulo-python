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
package com.jetbrains.python.impl.codeInsight.intentions;

import com.jetbrains.python.impl.codeInsight.codeFragment.PyCodeFragmentUtil;
import com.jetbrains.python.impl.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.impl.psi.impl.PyFunctionBuilder;
import com.jetbrains.python.impl.refactoring.introduce.IntroduceValidator;
import com.jetbrains.python.psi.*;
import consulo.codeEditor.Editor;
import consulo.language.controlFlow.ControlFlow;
import consulo.language.controlFlow.Instruction;
import consulo.language.editor.CodeInsightUtilCore;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.intention.BaseIntentionAction;
import consulo.language.editor.template.*;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;

import consulo.python.impl.localize.PyLocalize;
import jakarta.annotation.Nonnull;

import java.util.Arrays;
import java.util.List;

/**
 * Intention to convert lambda to function
 *
 * @author catherine
 */
public class PyConvertLambdaToFunctionIntention extends BaseIntentionAction {
    @Nonnull
    @Override
    public LocalizeValue getText() {
        return PyLocalize.intnConvertLambdaToFunction();
    }

    public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
        if (!(file instanceof PyFile)) {
            return false;
        }

        PyLambdaExpression lambdaExpression =
            PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), PyLambdaExpression.class);
        if (lambdaExpression != null) {
            if (lambdaExpression.getBody() != null) {
                ControlFlow flow = ControlFlowCache.getControlFlow(lambdaExpression);
                List<Instruction> graph = Arrays.asList(flow.getInstructions());
                List<PsiElement> elements = PyCodeFragmentUtil.getInputElements(graph, graph);
                if (elements.size() > 0) {
                    return false;
                }
                return true;
            }
        }
        return false;
    }

    public void invoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        PyLambdaExpression lambdaExpression =
            PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), PyLambdaExpression.class);
        if (lambdaExpression != null) {
            String name = "function";
            while (IntroduceValidator.isDefinedInScope(name, lambdaExpression)) {
                name += "1";
            }

            PsiElement parent = lambdaExpression.getParent();
            if (parent instanceof PyAssignmentStatement) {
                name = ((PyAssignmentStatement) parent).getLeftHandSideExpression().getText();
            }

            if (name.isEmpty()) {
                return;
            }
            PyExpression body = lambdaExpression.getBody();
            PyFunctionBuilder functionBuilder = new PyFunctionBuilder(name);
            for (PyParameter param : lambdaExpression.getParameterList().getParameters()) {
                functionBuilder.parameter(param.getText());
            }
            functionBuilder.statement("return " + body.getText());
            PyFunction function = functionBuilder.buildFunction(project, LanguageLevel.getDefault());

            PyStatement statement = PsiTreeUtil.getParentOfType(
                lambdaExpression,
                PyStatement.class
            );
            if (statement != null) {
                PsiElement statementParent = statement.getParent();
                if (statementParent != null) {
                    function = (PyFunction) statementParent.addBefore(function, statement);
                }
            }

            function = CodeInsightUtilCore
                .forcePsiPostprocessAndRestoreElement(function);

            if (parent instanceof PyAssignmentStatement) {
                parent.delete();
            }
            else {
                PsiFile parentScope = lambdaExpression.getContainingFile();
                TemplateBuilder builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(parentScope);
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
