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
package com.jetbrains.python.impl.codeInsight.intentions;

import com.jetbrains.python.impl.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.debugger.PySignature;
import com.jetbrains.python.impl.debugger.PySignatureCacheManager;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import consulo.codeEditor.Editor;
import consulo.document.Document;
import consulo.document.util.TextRange;
import consulo.fileEditor.FileEditorManager;
import consulo.language.editor.CodeInsightUtilCore;
import consulo.language.editor.template.Template;
import consulo.language.editor.template.TemplateBuilder;
import consulo.language.editor.template.TemplateBuilderFactory;
import consulo.language.editor.template.TemplateManager;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiReference;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.navigation.OpenFileDescriptor;
import consulo.navigation.OpenFileDescriptorFactory;
import consulo.project.Project;
import consulo.util.lang.ObjectUtil;
import consulo.util.lang.StringUtil;

import jakarta.annotation.Nonnull;

/**
 * User: ktisha
 * <p>
 * Helps to specify type  in annotations in python3
 */
public class SpecifyTypeInPy3AnnotationsIntention extends TypeIntention {
  private String myText = PyBundle.message("INTN.specify.type.in.annotation");

  @Nonnull
  public String getText() {
    return myText;
  }

  @Nonnull
  public String getFamilyName() {
    return PyBundle.message("INTN.specify.type.in.annotation");
  }

  @Override
  public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
    if (!LanguageLevel.forElement(file).isPy3K()) {
      return false;
    }
    return super.isAvailable(project, editor, file);
  }

  @Override
  public void doInvoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final PsiElement elementAt = PyUtil.findNonWhitespaceAtOffset(file, editor.getCaretModel().getOffset());
    final PyExpression problemElement = getProblemElement(elementAt);
    final PsiReference reference = problemElement == null ? null : problemElement.getReference();

    final PsiElement resolved = reference != null ? reference.resolve() : null;
    final PyNamedParameter parameter = getParameter(problemElement, resolved);

    if (parameter != null) {
      annotateParameter(project, editor, parameter, true);
    }
    else {
      PyCallable callable = getCallable(elementAt);
      if (callable instanceof PyFunction) {
        annotateReturnType(project, (PyFunction)callable, true);
      }
    }
  }

  static PyNamedParameter annotateParameter(Project project, Editor editor, @Nonnull PyNamedParameter parameter, boolean createTemplate) {
    final PyExpression defaultParamValue = parameter.getDefaultValue();

    final String paramName = StringUtil.notNullize(parameter.getName());
    final PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);

    final String defaultParamText = defaultParamValue == null ? null : defaultParamValue.getText();

    String paramType = parameterType(parameter);


    final PyNamedParameter namedParameter =
      elementGenerator.createParameter(paramName, defaultParamText, paramType, LanguageLevel.forElement(parameter));
    assert namedParameter != null;
    parameter = (PyNamedParameter)parameter.replace(namedParameter);
    parameter = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(parameter);
    editor.getCaretModel().moveToOffset(parameter.getTextOffset());
    final PyAnnotation annotation = parameter.getAnnotation();
    if (annotation != null && createTemplate) {
      final PyExpression annotationValue = annotation.getValue();

      final TemplateBuilder builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(parameter);
      assert annotationValue != null : "Generated parameter must have annotation";
      final int replacementStart = annotation.getStartOffsetInParent() + annotationValue.getStartOffsetInParent();
      builder.replaceRange(TextRange.create(replacementStart, replacementStart + annotationValue.getTextLength()), paramType);
      final Template template = builder.buildInlineTemplate();
      TemplateManager.getInstance(project).startTemplate(editor, template);
    }

    return parameter;
  }

  static String parameterType(PyParameter parameter) {
    String paramType = PyNames.OBJECT;

    PyFunction function = PsiTreeUtil.getParentOfType(parameter, PyFunction.class);
    if (function != null) {
      final PySignature signature = PySignatureCacheManager.getInstance(parameter.getProject()).findSignature(function);
      String parameterName = parameter.getName();
      if (signature != null && parameterName != null) {
        paramType = ObjectUtil.chooseNotNull(signature.getArgTypeQualifiedName(parameterName), paramType);
      }
    }
    return paramType;
  }


  static String returnType(@Nonnull PyFunction function) {
    String returnType = PyNames.OBJECT;
    final PySignature signature = PySignatureCacheManager.getInstance(function.getProject()).findSignature(function);
    if (signature != null) {
      returnType = ObjectUtil.chooseNotNull(signature.getReturnTypeQualifiedName(), returnType);
    }
    return returnType;
  }

  public static PyExpression annotateReturnType(Project project, PyFunction function, boolean createTemplate) {
    String returnType = returnType(function);

    final String annotationText = "-> " + returnType;

    final PsiDocumentManager manager = PsiDocumentManager.getInstance(project);
    Document documentWithCallable = manager.getDocument(function.getContainingFile());
    if (documentWithCallable != null) {
      try {
        manager.doPostponedOperationsAndUnblockDocument(documentWithCallable);
        final PyAnnotation oldAnnotation = function.getAnnotation();
        if (oldAnnotation != null) {
          final TextRange oldRange = oldAnnotation.getTextRange();
          documentWithCallable.replaceString(oldRange.getStartOffset(), oldRange.getEndOffset(), annotationText);
        }
        else {
          final PsiElement prevElem = PyPsiUtils.getPrevNonCommentSibling(function.getStatementList(), true);
          assert prevElem != null;
          final TextRange range = prevElem.getTextRange();
          if (prevElem.getNode().getElementType() == PyTokenTypes.COLON) {
            documentWithCallable.insertString(range.getStartOffset(), " " + annotationText);
          }
          else {
            documentWithCallable.insertString(range.getEndOffset(), " " + annotationText + ":");
          }
        }
      }
      finally {
        manager.commitDocument(documentWithCallable);
      }
    }


    function = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(function);
    final PyAnnotation annotation = function.getAnnotation();
    assert annotation != null;
    final PyExpression annotationValue = annotation.getValue();
    assert annotationValue != null : "Generated function must have annotation";

    if (createTemplate) {
      final int offset = annotationValue.getTextOffset();

      final TemplateBuilder builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(annotationValue);
      builder.replaceRange(TextRange.create(0, returnType.length()), returnType);
      final Template template = builder.buildInlineTemplate();
      final OpenFileDescriptor descriptor =
        OpenFileDescriptorFactory.getInstance(project).builder(function.getContainingFile().getVirtualFile()).offset(offset).build();
      final Editor targetEditor = FileEditorManager.getInstance(project).openTextEditor(descriptor, true);
      if (targetEditor != null) {
        targetEditor.getCaretModel().moveToOffset(offset);
        TemplateManager.getInstance(project).startTemplate(targetEditor, template);
      }
    }
    return annotationValue;
  }

  @Override
  protected boolean isParamTypeDefined(PyParameter parameter) {
    return isDefinedInAnnotation(parameter);
  }

  private static boolean isDefinedInAnnotation(PyParameter parameter) {
    if (LanguageLevel.forElement(parameter).isOlderThan(LanguageLevel.PYTHON30)) {
      return false;
    }
    if (parameter instanceof PyNamedParameter && (((PyNamedParameter)parameter).getAnnotation() != null)) {
      return true;
    }
    return false;
  }

  @Override
  protected boolean isReturnTypeDefined(@Nonnull PyFunction function) {
    return function.getAnnotation() != null;
  }

  @Override
  protected void updateText(boolean isReturn) {
    myText = isReturn ? PyBundle.message("INTN.specify.return.type.in.annotation") : PyBundle.message("INTN.specify.type.in.annotation");
  }
}