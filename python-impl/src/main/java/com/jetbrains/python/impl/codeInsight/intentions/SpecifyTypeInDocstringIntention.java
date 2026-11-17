// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.intentions;

import com.google.common.base.Preconditions;
import com.jetbrains.python.documentation.docstrings.DocStringFormat;
import com.jetbrains.python.impl.codeInsight.intentions.PyTypeHintGenerationUtil.AnnotationInfo;
import com.jetbrains.python.impl.documentation.docstrings.DocStringUtil;
import com.jetbrains.python.impl.documentation.docstrings.PyDocstringGenerator;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.StructuredDocString;
import com.jetbrains.python.toolbox.Substring;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.WriteAction;
import consulo.codeEditor.Editor;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.editor.template.TemplateBuilder;
import consulo.language.editor.template.TemplateBuilderFactory;
import consulo.language.editor.util.PsiEditorUtil;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

/**
 * User: ktisha
 * <p/>
 * Helps to specify type
 */
@ExtensionImpl
@IntentionMetaData(ignoreId = "python.SpecifyTypeInDocstringIntention", categories = "Python", fileExtensions = "py")
public final class SpecifyTypeInDocstringIntention extends TypeIntention {
    public SpecifyTypeInDocstringIntention() {
        setText(PyLocalize.intnNameSpecifyTypeInDocstring());
    }

    @Override
    public void doInvoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        PyNamedParameter parameter = findOnlySuitableParameter(editor, file);
        if (parameter != null) {
            PyFunction parentFunction = PsiTreeUtil.getParentOfType(parameter, PyFunction.class);
            if (parentFunction != null) {
                generateDocstring(parameter, parentFunction);
            }
            return;
        }

        PyFunction function = findOnlySuitableFunction(editor, file);
        if (function != null) {
            generateDocstring(null, function);
        }
    }

    @Override
    public boolean startInWriteAction() {
        return false;
    }

    private static void generateDocstring(@Nullable PyNamedParameter param, PyFunction pyFunction) {
        if (!FileModificationService.getInstance().preparePsiElementForWrite(pyFunction)) {
            return;
        }
        if (!PyGenerateDocstringIntention.ensureNotPlainDocstringFormat(pyFunction)) {

            return;

        }
        PyDocstringGenerator docstringGenerator = PyDocstringGenerator.forDocStringOwner(pyFunction);

        if (param != null) {
            String type = SpecifyTypeInPy3AnnotationsIntention.parameterType(param);
            docstringGenerator.withParamTypedByName(param, type);
        }
        else {
            AnnotationInfo info = SpecifyTypeInPy3AnnotationsIntention.returnType(pyFunction);
            docstringGenerator.withReturnValue(info.getAnnotationText());
        }

        WriteAction.run(() -> {
            docstringGenerator.addFirstEmptyLine().buildAndInsert();
            startTemplate(docstringGenerator);
        });
    }

    @Override
    protected void updateText(boolean isReturn) {
        setText(isReturn ? PyLocalize.intnSpecifyReturnTypeInDocstring() : PyLocalize.intnSpecifyTypeInDocstring());
    }

    @Override
    protected boolean isParamTypeDefined(PyNamedParameter parameter) {
        PyFunction pyFunction = PsiTreeUtil.getParentOfType(parameter, PyFunction.class);
        if (pyFunction != null) {
            StructuredDocString structuredDocString = pyFunction.getStructuredDocString();
            if (structuredDocString == null) {
                return false;
            }
            Substring typeSub = structuredDocString.getParamTypeSubstring(StringUtil.notNullize(parameter.getName()));
            return typeSub != null && !typeSub.isEmpty();
        }
        return false;
    }

    @Override
    protected boolean isReturnTypeDefined(PyFunction function) {
        StructuredDocString structuredDocString = function.getStructuredDocString();
        return structuredDocString != null && structuredDocString.getReturnType() != null;
    }

    public static void startTemplate(PyDocstringGenerator generator) {
        Preconditions.checkNotNull(generator.getDocStringOwner(), "For this action docstring owner must be supplied");
        PyStringLiteralExpression docStringExpression = (PyStringLiteralExpression)generator.getDocStringExpression();
        assert docStringExpression != null;

        TemplateBuilder builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(docStringExpression);

        if (generator.getAddedParams().size() > 1) {
            throw new IllegalArgumentException("TemplateBuilder can be created only for one parameter");
        }

        PyDocstringGenerator.DocstringParam paramToEdit = generator.getParamToEdit();
        DocStringFormat format = generator.getDocStringFormat();
        if (format == DocStringFormat.PLAIN) {
            return;
        }
        StructuredDocString parsed = DocStringUtil.parseDocString(format, docStringExpression);
        Substring substring;
        if (paramToEdit.isReturnValue()) {
            substring = parsed.getReturnTypeSubstring();
        }
        else {
            String paramName = paramToEdit.getName();
            substring = parsed.getParamTypeSubstring(paramName);
        }
        if (substring == null) {
            return;
        }
        builder.replaceRange(substring.getTextRange(), PyDocstringGenerator.getDefaultType(generator.getParamToEdit()));

        VirtualFile virtualFile = generator.getDocStringOwner().getContainingFile().getVirtualFile();
        if (virtualFile == null) {
            return;
        }
        Editor targetEditor = PsiEditorUtil.findEditor(generator.getDocStringOwner());
        if (targetEditor != null) {
            PyUtil.updateDocumentUnblockedAndCommitted(generator.getDocStringOwner(), document -> {
                builder.run(targetEditor, true);
                return null;
            });
        }
    }
}

