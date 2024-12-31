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
package com.jetbrains.python.impl.inspections.quickfix;

import com.jetbrains.python.impl.PyBundle;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.inspections.PyInspectionExtension;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.impl.psi.impl.ParamHelper;
import com.jetbrains.python.impl.psi.impl.PyFunctionBuilder;
import com.jetbrains.python.impl.psi.types.PyModuleType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.codeEditor.Editor;
import consulo.component.extension.Extensions;
import consulo.fileEditor.FileEditorManager;
import consulo.language.editor.CodeInsightUtilCore;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.template.TemplateBuilder;
import consulo.language.editor.template.TemplateBuilderFactory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.navigation.OpenFileDescriptorFactory;
import consulo.project.Project;
import consulo.ui.NotificationType;
import consulo.virtualFileSystem.VirtualFile;

import jakarta.annotation.Nonnull;
import java.util.List;

import static com.jetbrains.python.impl.psi.PyUtil.sure;

/**
 * Adds a missing top-level function to a module.
 * <br/>
 * User: dcheryasov
 * Date: Sep 15, 2010 4:34:23 PM
 *
 * @see AddMethodQuickFix AddMethodQuickFix
 */
public class AddFunctionQuickFix implements LocalQuickFix {

  private final String myIdentifier;
  private final String myModuleName;

  public AddFunctionQuickFix(@Nonnull String identifier, String moduleName) {
    myIdentifier = identifier;
    myModuleName = moduleName;
  }

  @Nonnull
  public String getName() {
    return PyBundle.message("QFIX.NAME.add.function.$0.to.module.$1", myIdentifier, myModuleName);
  }

  @Nonnull
  public String getFamilyName() {
    return "Create function in module";
  }

  public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
    try {
      final PsiElement problemElement = descriptor.getPsiElement();
      if (!(problemElement instanceof PyQualifiedExpression)) {
        return;
      }
      final PyExpression qualifier = ((PyQualifiedExpression)problemElement).getQualifier();
      if (qualifier == null) {
        return;
      }
      final PyType type = TypeEvalContext.userInitiated(problemElement.getProject(), problemElement.getContainingFile()).getType(qualifier);
      if (!(type instanceof PyModuleType)) {
        return;
      }
      final PyFile file = ((PyModuleType)type).getModule();
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
        final PyExpression[] args = arglist.getArguments();
        for (PyExpression arg : args) {
          if (arg instanceof PyKeywordArgument) { // foo(bar) -> def foo(bar_1)
            builder.parameter(((PyKeywordArgument)arg).getKeyword());
          }
          else if (arg instanceof PyReferenceExpression) {
            PyReferenceExpression refex = (PyReferenceExpression)arg;
            builder.parameter(refex.getReferencedName());
          }
          else { // use a boring name
            builder.parameter("param");
          }
        }
      }
      else if (problemParent != null) {
        for (PyInspectionExtension extension : Extensions.getExtensions(PyInspectionExtension.EP_NAME)) {
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
      PyFunction function = builder.buildFunction(project, LanguageLevel.forElement(file));

      // add to the bottom
      function = (PyFunction)file.add(function);
      showTemplateBuilder(function, file);
    }
    catch (IncorrectOperationException ignored) {
      // we failed. tell about this
      PyUtil.showBalloon(project, PyBundle.message("QFIX.failed.to.add.function"), NotificationType.ERROR);
    }
  }

  private static void showTemplateBuilder(PyFunction method, @Nonnull final PsiFile file) {
    method = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(method);

    final TemplateBuilder builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(method);
    ParamHelper.walkDownParamArray(method.getParameterList().getParameters(), new ParamHelper.ParamVisitor() {
      public void visitNamedParameter(PyNamedParameter param, boolean first, boolean last) {
        builder.replaceElement(param, param.getName());
      }
    });

    // TODO: detect expected return type from call site context: PY-1863
    builder.replaceElement(method.getStatementList(), "return None");
    final VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) {
      return;
    }
    final Editor editor =
      FileEditorManager.getInstance(file.getProject())
                       .openTextEditor(OpenFileDescriptorFactory.getInstance(file.getProject()).builder(virtualFile).build(), true);
    if (editor == null) {
      return;
    }
    builder.run(editor, false);
  }
}
