// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.quickfix;

import com.jetbrains.python.impl.PythonTemplateRunner;
import com.jetbrains.python.impl.PythonUiService;
import com.jetbrains.python.impl.psi.impl.ParamHelper;
import com.jetbrains.python.impl.psi.impl.PyFunctionBuilder;
import com.jetbrains.python.impl.psi.types.PyModuleType;
import com.jetbrains.python.inspections.PyInspectionExtension;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFromImportStatement;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyKeywordArgument;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.application.WriteAction;
import consulo.language.editor.CodeInsightUtilCore;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.template.TemplateBuilder;
import consulo.language.editor.template.TemplateBuilderFactory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;

import java.util.List;

import static com.jetbrains.python.impl.psi.PyUtil.sure;

/**
 * Adds a missing top-level function to a module.
 *
 * @see AddMethodQuickFix AddMethodQuickFix
 */
public class AddFunctionQuickFix implements LocalQuickFix {

    private final String myIdentifier;
    private final String myModuleName;

    public AddFunctionQuickFix(String identifier, String moduleName) {
        myIdentifier = identifier;
        myModuleName = moduleName;
    }

    @Override
    public LocalizeValue getName() {
        return PyLocalize.qfixCreateFunctionInModule(myIdentifier, myModuleName);
    }

    @Override
    public boolean startInWriteAction() {
        return false;
    }

    @Override
    public void applyFix(Project project, ProblemDescriptor descriptor) {
        try {
            PsiElement problemElement = descriptor.getPsiElement();
            if (!(problemElement instanceof PyQualifiedExpression)) {
                return;
            }
            PyExpression qualifier = ((PyQualifiedExpression)problemElement).getQualifier();
            if (qualifier == null) {
                PyFromImportStatement fromImport = PsiTreeUtil.getParentOfType(problemElement, PyFromImportStatement.class);
                if (fromImport == null) {
                    return;
                }
                qualifier = fromImport.getImportSource();
            }
            if (qualifier == null) {
                return;
            }
            PyType type = TypeEvalContext.userInitiated(problemElement.getProject(), problemElement.getContainingFile()).getType(qualifier);
            if (!(type instanceof PyModuleType)) {
                return;
            }
            PyFile file = ((PyModuleType)type).getModule();
            sure(file);
            sure(FileModificationService.getInstance().preparePsiElementForWrite(file));
            // try to at least match parameter count
            // TODO: get parameter style from code style
            PyFunctionBuilder builder = new PyFunctionBuilder(myIdentifier, problemElement);
            PsiElement problemParent = problemElement.getParent();
            if (problemParent instanceof PyCallExpression) {
                PyArgumentList arglist = ((PyCallExpression)problemParent).getArgumentList();
                if (arglist == null) {
                    return;
                }
                PyExpression[] args = arglist.getArguments();
                for (PyExpression arg : args) {
                    if (arg instanceof PyKeywordArgument) { // foo(bar) -> def foo(bar_1)
                        builder.parameter(((PyKeywordArgument)arg).getKeyword());
                    }
                    else if (arg instanceof PyReferenceExpression refex) {
                        builder.parameter(refex.getReferencedName());
                    }
                    else { // use a boring name
                        builder.parameter("param");
                    }
                }
            }
            else if (problemParent != null) {
                for (PyInspectionExtension extension : PyInspectionExtension.EP_NAME.getExtensionList()) {
                    List<String> params = extension.getFunctionParametersFromUsage(problemElement);
                    if (params != null) {
                        for (String param : params) {
                            builder.parameter(param);
                        }
                        break;
                    }
                }
            }
            // else: no arglist, use empty args

            WriteAction.run(() -> {
                PyFunction function = builder.buildFunction();

                // add to the bottom
                function = (PyFunction)file.add(function);
                showTemplateBuilder(function, file);
            });
        }
        catch (IncorrectOperationException ignored) {
            // we failed. tell about this
            PythonUiService.getInstance().showBalloonError(project, PyLocalize.qfixFailedToAddFunction().get());
        }
    }

    private static void showTemplateBuilder(PyFunction method, PsiFile file) {
        method = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(method);

        TemplateBuilder builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(method);
        ParamHelper.walkDownParamArray(
            method.getParameterList().getParameters(),
            new ParamHelper.ParamVisitor() {
                @Override
                public void visitNamedParameter(PyNamedParameter param, boolean first, boolean last) {
                    builder.replaceElement(param, param.getName());
                }
            }
        );

        // TODO: detect expected return type from call site context: PY-1863
        builder.replaceElement(method.getStatementList(), "return None");
        PythonTemplateRunner.runTemplate(file, builder);
    }
}
