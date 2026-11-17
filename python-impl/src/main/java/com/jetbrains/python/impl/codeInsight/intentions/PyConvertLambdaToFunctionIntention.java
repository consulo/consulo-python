// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.intentions;

import com.jetbrains.python.impl.codeInsight.codeFragment.PyCodeFragmentUtil;
import com.jetbrains.python.impl.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.impl.psi.impl.PyFunctionBuilder;
import com.jetbrains.python.impl.refactoring.introduce.IntroduceValidator;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyLambdaExpression;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.PyStatement;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.language.controlFlow.ControlFlow;
import consulo.language.controlFlow.Instruction;
import consulo.language.editor.CodeInsightUtilCore;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.editor.template.ConstantNode;
import consulo.language.editor.template.Expression;
import consulo.language.editor.template.TemplateBuilder;
import consulo.language.editor.template.TemplateBuilderFactory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;

import java.util.Arrays;
import java.util.List;

/**
 * User: catherine
 * Intention to convert lambda to function
 */
@ExtensionImpl
@IntentionMetaData(ignoreId = "python.PyConvertLambdaToFunctionIntention", categories = "Python", fileExtensions = "py")
public final class PyConvertLambdaToFunctionIntention extends PyBaseIntentionAction {


    @Override
    public LocalizeValue getText() {
        return PyLocalize.intnConvertLambdaToFunction();
    }

    @Override
    public boolean isAvailable(Project project, Editor editor, PsiFile psiFile) {
        if (!(psiFile instanceof PyFile)) {
            return false;
        }

        PyLambdaExpression lambdaExpression =
                PsiTreeUtil.getParentOfType(psiFile.findElementAt(editor.getCaretModel().getOffset()), PyLambdaExpression.class);
        if (lambdaExpression != null) {
            if (lambdaExpression.getBody() != null) {
                ControlFlow flow = ControlFlowCache.getControlFlow(lambdaExpression);
                List<Instruction> graph = Arrays.asList(flow.getInstructions());
                List<PsiElement> elements = PyCodeFragmentUtil.getInputElements(graph, graph);
                if (!elements.isEmpty()) {
                    return false;
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public void doInvoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        PyLambdaExpression lambdaExpression =
                PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), PyLambdaExpression.class);
        if (lambdaExpression != null) {
            String name = "function";
            while (IntroduceValidator.isDefinedInScope(name, lambdaExpression)) {
                name += "1";
            }

            PsiElement parent = lambdaExpression.getParent();
            if (parent instanceof PyAssignmentStatement) {
                name = ((PyAssignmentStatement)parent).getLeftHandSideExpression().getText();
            }

            if (name.isEmpty()) {

                return;

            }
            PyExpression body = lambdaExpression.getBody();
            PyFunctionBuilder functionBuilder = new PyFunctionBuilder(name, lambdaExpression);
            for (PyParameter param : lambdaExpression.getParameterList().getParameters()) {
                functionBuilder.parameter(param.getText());
            }
            functionBuilder.statement("return " + body.getText());
            PyFunction function = functionBuilder.buildFunction();

            PyStatement statement = PsiTreeUtil.getParentOfType(lambdaExpression,
                    PyStatement.class);
            if (statement != null) {
                PsiElement statementParent = statement.getParent();
                if (statementParent != null) {
                    function = (PyFunction)statementParent.addBefore(function, statement);
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

                Expression refExpr = new ConstantNode(name);

                builder.replaceElement(lambdaExpression, name, refExpr, true);
                builder.replaceElement(functionName, name, name, false);

                editor.getCaretModel().moveToOffset(parentScope.getTextRange().getStartOffset());

                builder.run(editor, true);
            }
        }
    }
}

