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
import consulo.localize.LocalizeValue;
import consulo.navigation.OpenFileDescriptor;
import consulo.navigation.OpenFileDescriptorFactory;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.lang.ObjectUtil;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;

/**
 * Helps to specify type  in annotations in python3.
 *
 * @author ktisha
 */
public class SpecifyTypeInPy3AnnotationsIntention extends TypeIntention {
    @Nonnull
    private LocalizeValue myText = PyLocalize.intnSpecifyTypeInAnnotation();

    @Nonnull
    @Override
    public LocalizeValue getText() {
        return myText;
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
        PsiElement elementAt = PyUtil.findNonWhitespaceAtOffset(file, editor.getCaretModel().getOffset());
        PyExpression problemElement = getProblemElement(elementAt);
        PsiReference reference = problemElement == null ? null : problemElement.getReference();

        PsiElement resolved = reference != null ? reference.resolve() : null;
        PyNamedParameter parameter = getParameter(problemElement, resolved);

        if (parameter != null) {
            annotateParameter(project, editor, parameter, true);
        }
        else {
            PyCallable callable = getCallable(elementAt);
            if (callable instanceof PyFunction) {
                annotateReturnType(project, (PyFunction) callable, true);
            }
        }
    }

    static PyNamedParameter annotateParameter(Project project, Editor editor, @Nonnull PyNamedParameter parameter, boolean createTemplate) {
        PyExpression defaultParamValue = parameter.getDefaultValue();

        String paramName = StringUtil.notNullize(parameter.getName());
        PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);

        String defaultParamText = defaultParamValue == null ? null : defaultParamValue.getText();

        String paramType = parameterType(parameter);


        PyNamedParameter namedParameter =
            elementGenerator.createParameter(paramName, defaultParamText, paramType, LanguageLevel.forElement(parameter));
        assert namedParameter != null;
        parameter = (PyNamedParameter) parameter.replace(namedParameter);
        parameter = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(parameter);
        editor.getCaretModel().moveToOffset(parameter.getTextOffset());
        PyAnnotation annotation = parameter.getAnnotation();
        if (annotation != null && createTemplate) {
            PyExpression annotationValue = annotation.getValue();

            TemplateBuilder builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(parameter);
            assert annotationValue != null : "Generated parameter must have annotation";
            int replacementStart = annotation.getStartOffsetInParent() + annotationValue.getStartOffsetInParent();
            builder.replaceRange(TextRange.create(replacementStart, replacementStart + annotationValue.getTextLength()), paramType);
            Template template = builder.buildInlineTemplate();
            TemplateManager.getInstance(project).startTemplate(editor, template);
        }

        return parameter;
    }

    static String parameterType(PyParameter parameter) {
        String paramType = PyNames.OBJECT;

        PyFunction function = PsiTreeUtil.getParentOfType(parameter, PyFunction.class);
        if (function != null) {
            PySignature signature = PySignatureCacheManager.getInstance(parameter.getProject()).findSignature(function);
            String parameterName = parameter.getName();
            if (signature != null && parameterName != null) {
                paramType = ObjectUtil.chooseNotNull(signature.getArgTypeQualifiedName(parameterName), paramType);
            }
        }
        return paramType;
    }


    static String returnType(@Nonnull PyFunction function) {
        String returnType = PyNames.OBJECT;
        PySignature signature = PySignatureCacheManager.getInstance(function.getProject()).findSignature(function);
        if (signature != null) {
            returnType = ObjectUtil.chooseNotNull(signature.getReturnTypeQualifiedName(), returnType);
        }
        return returnType;
    }

    public static PyExpression annotateReturnType(Project project, PyFunction function, boolean createTemplate) {
        String returnType = returnType(function);

        String annotationText = "-> " + returnType;

        PsiDocumentManager manager = PsiDocumentManager.getInstance(project);
        Document documentWithCallable = manager.getDocument(function.getContainingFile());
        if (documentWithCallable != null) {
            try {
                manager.doPostponedOperationsAndUnblockDocument(documentWithCallable);
                PyAnnotation oldAnnotation = function.getAnnotation();
                if (oldAnnotation != null) {
                    TextRange oldRange = oldAnnotation.getTextRange();
                    documentWithCallable.replaceString(oldRange.getStartOffset(), oldRange.getEndOffset(), annotationText);
                }
                else {
                    PsiElement prevElem = PyPsiUtils.getPrevNonCommentSibling(function.getStatementList(), true);
                    assert prevElem != null;
                    TextRange range = prevElem.getTextRange();
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
        PyAnnotation annotation = function.getAnnotation();
        assert annotation != null;
        PyExpression annotationValue = annotation.getValue();
        assert annotationValue != null : "Generated function must have annotation";

        if (createTemplate) {
            int offset = annotationValue.getTextOffset();

            TemplateBuilder builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(annotationValue);
            builder.replaceRange(TextRange.create(0, returnType.length()), returnType);
            Template template = builder.buildInlineTemplate();
            OpenFileDescriptor descriptor =
                OpenFileDescriptorFactory.getInstance(project)
                    .builder(function.getContainingFile().getVirtualFile())
                    .offset(offset)
                    .build();
            Editor targetEditor = FileEditorManager.getInstance(project).openTextEditor(descriptor, true);
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
        if (parameter instanceof PyNamedParameter && (((PyNamedParameter) parameter).getAnnotation() != null)) {
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
        myText = isReturn ? PyLocalize.intnSpecifyReturnTypeInAnnotation() : PyLocalize.intnSpecifyTypeInAnnotation();
    }
}