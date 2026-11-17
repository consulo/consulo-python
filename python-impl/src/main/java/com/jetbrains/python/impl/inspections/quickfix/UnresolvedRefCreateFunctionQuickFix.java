// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.quickfix;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.impl.PythonTemplateRunner;
import com.jetbrains.python.impl.psi.impl.ParamHelper;
import com.jetbrains.python.impl.psi.impl.PyFunctionBuilder;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyCallSiteExpression;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyKeywordArgument;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyPrefixExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyStatement;
import consulo.annotation.access.RequiredWriteAction;
import consulo.language.editor.CodeInsightUtilCore;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.template.TemplateBuilder;
import consulo.language.editor.template.TemplateBuilderFactory;
import consulo.language.inject.InjectedLanguageManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiLanguageInjectionHost;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;

import static com.jetbrains.python.impl.psi.PyUtil.as;

/**
 * User: catherine
 * <p>
 * QuickFix to create function to unresolved unqualified reference
 */
public class UnresolvedRefCreateFunctionQuickFix implements LocalQuickFix {
    private final String myFunctionName;
    private final boolean myAsync;

    public UnresolvedRefCreateFunctionQuickFix(PyReferenceExpression reference) {
        myFunctionName = reference.getReferencedName();
        PyCallSiteExpression callSiteExpression = as(reference.getParent(), PyCallSiteExpression.class);
        PyPrefixExpression prefixExpression = callSiteExpression != null ? as(callSiteExpression.getParent(), PyPrefixExpression.class) : null;
        myAsync = prefixExpression != null && prefixExpression.getOperator() == PyTokenTypes.AWAIT_KEYWORD;
    }

    @Override
    public LocalizeValue getName() {
        return PyLocalize.qfixNameUnresolvedReferenceCreateFunction(myFunctionName);
    }

    @Override
    @RequiredWriteAction
    public void applyFix(Project project, ProblemDescriptor descriptor) {
        PyReferenceExpression referenceExpr = as(descriptor.getPsiElement(), PyReferenceExpression.class);
        PyCallExpression callExpr = PsiTreeUtil.getParentOfType(referenceExpr, PyCallExpression.class);

        if (referenceExpr == null || callExpr == null) {
            return;
        }

        PyFunctionBuilder functionBuilder = new PyFunctionBuilder(referenceExpr.getText(), callExpr);

        // if function is actually an argument of a call, don't use other arguments of the call to create parameter list of new function
        PyArgumentList argumentList = callExpr.getArgumentList();
        if (argumentList != null && !PsiTreeUtil.isAncestor(argumentList, referenceExpr, false)) {
            for (PyExpression param : argumentList.getArguments()) {
                if (param instanceof PyKeywordArgument) {
                    functionBuilder.parameter(((PyKeywordArgument)param).getKeyword());
                }
                else if (param instanceof PyReferenceExpression refex) {
                    functionBuilder.parameter(refex.getReferencedName());
                }
                else {
                    functionBuilder.parameter("param");
                }
            }
        }
        else {
            functionBuilder.parameter("args");
        }

        if (myAsync) {
            functionBuilder.makeAsync();
        }

        PyFunction function = functionBuilder.buildFunction();

        InjectedLanguageManager instance = InjectedLanguageManager.getInstance(project);
        PsiLanguageInjectionHost host = instance.getInjectionHost(callExpr);
        PsiElement insertAnchor = host != null ? host : callExpr;

        PyFunction parentFunction = PsiTreeUtil.getTopmostParentOfType(insertAnchor, PyFunction.class);
        if (parentFunction != null) {
            PyClass parentClass = PsiTreeUtil.getTopmostParentOfType(parentFunction, PyClass.class);
            if (parentClass != null) {
                PsiElement parent = parentClass.getParent();
                function = (PyFunction)parent.addBefore(function, parentClass);
            }
            else {
                PsiElement parent = parentFunction.getParent();
                function = (PyFunction)parent.addBefore(function, parentFunction);
            }
        }
        else {
            PyStatement statement = PsiTreeUtil.getTopmostParentOfType(insertAnchor, PyStatement.class);
            if (statement != null) {
                PsiElement parent = statement.getParent();
                if (parent != null) {
                    function = (PyFunction)parent.addBefore(function, statement);
                }
            }
        }

        function = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(function);
        TemplateBuilder builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(function);
        ParamHelper.walkDownParamArray(
            function.getParameterList().getParameters(),
            new ParamHelper.ParamVisitor() {
                @Override
                public void visitNamedParameter(PyNamedParameter param, boolean first, boolean last) {
                    builder.replaceElement(param, param.getName());
                }
            }
        );
        builder.replaceElement(function.getStatementList(), PyNames.PASS);

        PythonTemplateRunner.runTemplateInSelectedEditor(project, insertAnchor, builder);
    }
}
