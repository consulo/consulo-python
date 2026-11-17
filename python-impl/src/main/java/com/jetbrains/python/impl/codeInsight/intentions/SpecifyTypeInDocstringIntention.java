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

import com.jetbrains.python.psi.StructuredDocString;
import com.jetbrains.python.documentation.docstrings.DocStringFormat;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.virtualFileSystem.VirtualFile;
import consulo.language.editor.util.PsiEditorUtil;
import consulo.language.editor.template.TemplateBuilderFactory;
import consulo.language.editor.template.TemplateBuilder;
import com.google.common.base.Preconditions;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.debugger.PySignature;
import com.jetbrains.python.impl.debugger.PySignatureCacheManager;
import com.jetbrains.python.impl.documentation.docstrings.DocStringUtil;
import com.jetbrains.python.impl.documentation.docstrings.PyDocstringGenerator;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.toolbox.Substring;
import consulo.codeEditor.Editor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiReference;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.lang.ObjectUtil;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

/**
 * Helps to specify type.
 *
 * @author ktisha
 */
@ExtensionImpl
@IntentionMetaData(ignoreId = "python.SpecifyTypeInDocstringIntention", categories = "Python", fileExtensions = "py")
public class SpecifyTypeInDocstringIntention extends TypeIntention {
    private LocalizeValue myText = PyLocalize.intnSpecifyType();

    @Override
    public LocalizeValue getText() {
        return myText;
    }

    @Override
    public void doInvoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        PsiElement elementAt = PyUtil.findNonWhitespaceAtOffset(file, editor.getCaretModel().getOffset());
        PyExpression problemElement = getProblemElement(elementAt);
        PsiReference reference = problemElement == null ? null : problemElement.getReference();

        PsiElement resolved = reference != null ? reference.resolve() : null;
        PyNamedParameter parameter = getParameter(problemElement, resolved);

        PyCallable callable;
        if (parameter != null) {
            callable = PsiTreeUtil.getParentOfType(parameter, PyFunction.class);
        }
        else {
            callable = getCallable(elementAt);
        }
        if (callable instanceof PyFunction) {
            generateDocstring(parameter, (PyFunction) callable);
        }
    }

    private static void generateDocstring(@Nullable PyNamedParameter param, PyFunction pyFunction) {
        if (!PyGenerateDocstringIntention.ensureNotPlainDocstringFormat(pyFunction)) {
            return;
        }

        PyDocstringGenerator docstringGenerator = PyDocstringGenerator.forDocStringOwner(pyFunction);
        String type = PyNames.OBJECT;
        if (param != null) {
            String paramName = StringUtil.notNullize(param.getName());
            PySignature signature = PySignatureCacheManager.getInstance(pyFunction.getProject()).findSignature(pyFunction);
            if (signature != null) {
                type = ObjectUtil.chooseNotNull(signature.getArgTypeQualifiedName(paramName), type);
            }
            docstringGenerator.withParamTypedByName(param, type);
        }
        else {
            PySignature signature = PySignatureCacheManager.getInstance(pyFunction.getProject()).findSignature(pyFunction);
            if (signature != null) {
                type = ObjectUtil.chooseNotNull(signature.getReturnTypeQualifiedName(), type);
            }
            docstringGenerator.withReturnValue(type);
        }

        docstringGenerator.addFirstEmptyLine().buildAndInsert();
        startTemplate(docstringGenerator);
    }

    public static void startTemplate(PyDocstringGenerator generator) {
        Preconditions.checkNotNull(generator.getDocStringOwner(), "For this action docstring owner must be supplied");
        final PyStringLiteralExpression docStringExpression = (PyStringLiteralExpression) generator.getDocStringExpression();
        assert docStringExpression != null;

        final TemplateBuilder builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(docStringExpression);

        if (generator.getAddedParams().size() > 1) {
            throw new IllegalArgumentException("TemplateBuilder can be created only for one parameter");
        }

        final PyDocstringGenerator.DocstringParam paramToEdit = generator.getParamToEdit();
        final DocStringFormat format = generator.getDocStringFormat();
        if (format == DocStringFormat.PLAIN) {
            return;
        }
        final StructuredDocString parsed = DocStringUtil.parseDocString(format, docStringExpression);
        final Substring substring;
        if (paramToEdit.isReturnValue()) {
            substring = parsed.getReturnTypeSubstring();
        }
        else {
            final String paramName = paramToEdit.getName();
            substring = parsed.getParamTypeSubstring(paramName);
        }
        if (substring == null) {
            return;
        }
        builder.replaceRange(substring.getTextRange(), PyDocstringGenerator.getDefaultType(generator.getParamToEdit()));

        final VirtualFile virtualFile = generator.getDocStringOwner().getContainingFile().getVirtualFile();
        if (virtualFile == null) {
            return;
        }
        final Editor targetEditor = PsiEditorUtil.findEditor(generator.getDocStringOwner());
        if (targetEditor != null) {
            PyUtil.updateDocumentUnblockedAndCommitted(generator.getDocStringOwner(), document -> {
                builder.run(targetEditor, true);
            });
        }
    }

    @Override
    protected void updateText(boolean isReturn) {
        myText = isReturn ? PyLocalize.intnSpecifyReturnType() : PyLocalize.intnSpecifyType();
    }

    @Override
    protected boolean isParamTypeDefined(PyParameter parameter) {
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
}
