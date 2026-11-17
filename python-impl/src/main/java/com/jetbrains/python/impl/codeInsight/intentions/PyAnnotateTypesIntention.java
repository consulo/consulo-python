// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.intentions;

import com.jetbrains.python.impl.PythonUiService;
import com.jetbrains.python.impl.documentation.doctest.PyDoctestFile;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyAnnotation;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyIndentUtil;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.PyStatementList;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.WriteAction;
import consulo.codeEditor.Editor;
import consulo.document.Document;
import consulo.document.util.TextRange;
import consulo.language.editor.CodeInsightUtilCore;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.editor.template.TemplateBuilder;
import consulo.language.editor.template.TemplateBuilderFactory;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.Pair;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@ExtensionImpl
@IntentionMetaData(ignoreId = "python.PyAnnotateTypesIntention", categories = "Python", fileExtensions = "py")
public final class PyAnnotateTypesIntention extends PyBaseIntentionAction {
    public PyAnnotateTypesIntention() {
        setText(PyLocalize.intnNameAddTypeHintsForFunction());
    }

    @Override
    public boolean isAvailable(Project project, Editor editor, PsiFile psiFile) {
        if (!(psiFile instanceof PyFile) || psiFile instanceof PyDoctestFile) {
            return false;
        }
        PyFunction function = findSuitableFunction(editor, psiFile);
        if (function == null) {
            return false;
        }
        if (function.getTypeComment() != null) {
            return false;
        }

        PyAnnotation annotation = function.getAnnotation();
        if (annotation != null) {
            boolean allParametersAnnotated = ContainerUtil.and(function.getParameterList().getParameters(),
                    it -> it instanceof PyNamedParameter &&
                    ((PyNamedParameter)it).getAnnotation() != null);
            if (allParametersAnnotated) {
                return false;
            }
        }

        setText(PyLocalize.intnAddTypeHintsForFunction(function.getName()));
        return true;
    }

    @Override
    public void doInvoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        PyFunction function = findSuitableFunction(editor, file);
        if (function != null) {
            annotateTypes(editor, function);
        }
    }

    @Override
    public boolean startInWriteAction() {
        return false;
    }

    private static @Nullable PyFunction findSuitableFunction(Editor editor, PsiFile file) {
        return TypeIntention.findOnlySuitableFunction(editor, file, input -> true);
    }

    public static void annotateTypes(Editor editor, PyFunction function) {
        if (!FileModificationService.getInstance().preparePsiElementForWrite(function)) {
            return;
        }
        WriteAction.run(() -> {
            if (isPy3k(function.getContainingFile())) {
                generatePy3kTypeAnnotations(function.getProject(), editor, function);
            }
            else {
                generateTypeCommentAnnotations(function.getProject(), function);
            }
        });
    }

    private static void generateTypeCommentAnnotations(Project project, PyFunction function) {

        StringBuilder replacementTextBuilder = new StringBuilder("# type: (");

        PyParameter[] params = function.getParameterList().getParameters();

        List<Pair<Integer, String>> templates = new ArrayList<>();

        for (int i = 0; i < params.length; i++) {
            if (!params[i].isSelf()) {
                String type = SpecifyTypeInPy3AnnotationsIntention.parameterType(params[i]);

                templates.add(Pair.create(replacementTextBuilder.length(), type));

                replacementTextBuilder.append(type);

                if (i < params.length - 1) {
                    replacementTextBuilder.append(", ");
                }
            }
        }

        replacementTextBuilder.append(") -> ");

        String returnType = SpecifyTypeInPy3AnnotationsIntention.returnType(function).getAnnotationText();
        templates.add(Pair.create(replacementTextBuilder.length(), returnType));

        replacementTextBuilder.append(returnType);

        PyStatementList statements = function.getStatementList();
        String indentation = PyIndentUtil.getElementIndent(statements);
        replacementTextBuilder.insert(0, indentation);
        replacementTextBuilder.insert(0, "\n");


        PsiDocumentManager manager = PsiDocumentManager.getInstance(project);
        Document document = manager.getDocument(function.getContainingFile());

        if (document != null) {
            PsiElement beforeStatements = statements.getPrevSibling();
            int offset = beforeStatements.getTextRange().getStartOffset();
            if (":".equals(beforeStatements.getText())) {
                offset += 1;
            }
            try {
                document.insertString(offset, replacementTextBuilder.toString());
            }
            finally {
                manager.commitDocument(document);
            }


            PsiElement element = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(function);

            while (element != null && !element.getText().contains(replacementTextBuilder.toString())) {
                element = element.getParent();
            }

            if (element != null) {
                TemplateBuilder builder =
                        TemplateBuilderFactory.getInstance().createTemplateBuilder(element);

                for (Pair<Integer, String> template : templates) {
                    builder.replaceRange(TextRange.from(
                            offset - element.getTextRange().getStartOffset() + replacementTextBuilder.toString().indexOf('#') + template.first,
                            template.second.length()), template.second);
                }

                startTemplate(project, element, builder);
            }
        }
    }

    private static void startTemplate(Project project, PsiElement callable, TemplateBuilder builder) {
        int offset = callable.getTextRange().getStartOffset();

        Editor targetEditor = PythonUiService.getInstance().openTextEditor(project,
                callable.getContainingFile().getVirtualFile(),
                offset);
        if (targetEditor != null) {
            builder.run(targetEditor, true);
        }
        else {
            // TODO consulo: TemplateBuilder.runNonInteractively missing - JB: builder.runNonInteractively(true);
        }
    }

    private static boolean isPy3k(PsiFile file) {
        return !LanguageLevel.forElement(file).isPython2();
    }

    private static void generatePy3kTypeAnnotations(Project project, Editor editor, PyFunction function) {
        TemplateBuilder builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(function);

        PyExpression returnType = SpecifyTypeInPy3AnnotationsIntention.annotateReturnType(project, function, false);

        if (returnType != null) {
            builder.replaceElement(returnType, returnType.getText());
        }

        PyParameter[] params = function.getParameterList().getParameters();

        for (int i = params.length - 1; i >= 0; i--) {
            if (params[i] instanceof PyNamedParameter && !params[i].isSelf()) {
                params[i] = SpecifyTypeInPy3AnnotationsIntention.annotateParameter(project, editor, (PyNamedParameter)params[i], false);
            }
        }


        for (int i = params.length - 1; i >= 0; i--) {
            if (params[i] instanceof PyNamedParameter) {
                if (!params[i].isSelf()) {
                    params[i] = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(params[i]);
                    PyAnnotation annotation = ((PyNamedParameter)params[i]).getAnnotation();
                    if (annotation != null) {
                        PyExpression annotationValue = annotation.getValue();
                        if (annotationValue != null) {
                            builder.replaceElement(annotationValue, annotationValue.getText());
                        }
                    }
                }
            }
        }
        startTemplate(project, function, builder);
    }
}

