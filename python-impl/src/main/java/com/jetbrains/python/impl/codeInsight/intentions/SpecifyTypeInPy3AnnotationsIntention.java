// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.intentions;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.debugger.PySignature;
import com.jetbrains.python.impl.PythonUiService;
import com.jetbrains.python.impl.codeInsight.intentions.PyTypeHintGenerationUtil.AnnotationInfo;
import com.jetbrains.python.impl.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.impl.debugger.PySignatureCacheManager;
import com.jetbrains.python.impl.documentation.PythonDocumentationProvider;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.ParamHelper;
import com.jetbrains.python.impl.psi.types.PyLiteralType;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyAnnotation;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.WriteAction;
import consulo.codeEditor.Editor;
import consulo.document.util.TextRange;
import consulo.language.editor.CodeInsightUtilCore;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.editor.template.TemplateBuilder;
import consulo.language.editor.template.TemplateBuilderFactory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.lang.ObjectUtil;
import consulo.util.lang.ref.Ref;
import org.jspecify.annotations.Nullable;

/**
 * User: ktisha
 * <p>
 * Helps to specify type  in annotations in python3
 */
@ExtensionImpl
@IntentionMetaData(ignoreId = "python.SpecifyTypeInPy3AnnotationsIntention", categories = "Python", fileExtensions = "py")
public final class SpecifyTypeInPy3AnnotationsIntention extends TypeIntention {
    public SpecifyTypeInPy3AnnotationsIntention() {
        setText(PyLocalize.intnNameSpecifyTypeInAnnotation());
    }

    @Override
    public boolean isAvailable(Project project, Editor editor, PsiFile psiFile) {
        if (LanguageLevel.forElement(psiFile).isPython2()) {
            return false;
        }
        return super.isAvailable(project, editor, psiFile);
    }

    @Override
    public void doInvoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        PyNamedParameter parameter = findOnlySuitableParameter(editor, file);
        if (parameter != null) {
            annotateParameter(project, editor, parameter);
            return;
        }

        PyFunction function = findOnlySuitableFunction(editor, file);
        if (function != null) {
            annotateReturnType(project, function);
        }
    }

    private static void annotateParameter(Project project, Editor editor, PyNamedParameter parameter) {
        if (!FileModificationService.getInstance().preparePsiElementForWrite(parameter)) {
            return;
        }
        WriteAction.run(() -> annotateParameter(project, editor, parameter, true));
    }

    private static void annotateReturnType(Project project, PyFunction function) {
        if (!FileModificationService.getInstance().preparePsiElementForWrite(function)) {
            return;
        }
        WriteAction.run(() -> annotateReturnType(project, function, true));
    }

    @Override
    public boolean startInWriteAction() {
        return false;
    }

    public static PyNamedParameter annotateParameter(Project project,
                                                     Editor editor,
                                                     PyNamedParameter parameter,
                                                     boolean createTemplate) {
        PyExpression defaultParamValue = parameter.getDefaultValue();

        String paramName = ParamHelper.getNameInSignature(parameter);
        PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);

        String defaultParamText = defaultParamValue == null ? null : defaultParamValue.getText();

        String paramType = parameterType(parameter);


        PyNamedParameter namedParameter = elementGenerator.createParameter(paramName, defaultParamText, paramType,
                LanguageLevel.forElement(parameter));
        assert namedParameter != null;
        PyNamedParameter replaced = (PyNamedParameter)parameter.replace(namedParameter);
        replaced = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(replaced);
        editor.getCaretModel().moveToOffset(replaced.getTextOffset());
        PyAnnotation annotation = replaced.getAnnotation();
        if (annotation != null && createTemplate) {
            PyExpression annotationValue = annotation.getValue();

            TemplateBuilder builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(replaced);
            assert annotationValue != null : "Generated parameter must have annotation";
            int replacementStart = annotation.getStartOffsetInParent() + annotationValue.getStartOffsetInParent();
            builder.replaceRange(TextRange.from(replacementStart, annotationValue.getTextLength()), paramType);
            builder.run(editor, true);
        }

        return replaced;
    }

    static String parameterType(PyParameter parameter) {
        String paramType = PyNames.OBJECT;

        if (parameter instanceof PyNamedParameter) {
            PyAnnotation annotation = ((PyNamedParameter)parameter).getAnnotation();
            if (annotation != null && annotation.getValue() != null) {
                return annotation.getValue().getText();
            }
        }

        PyFunction function = PsiTreeUtil.getParentOfType(parameter, PyFunction.class);
        if (function != null) {
            PySignature signature = PySignatureCacheManager.getInstance(parameter.getProject()).findSignature(
                    function);
            String parameterName = parameter.getName();
            if (signature != null && parameterName != null) {
                paramType = ObjectUtil.chooseNotNull(signature.getArgTypeQualifiedName(parameterName), paramType);
            }
        }

        return paramType;
    }


    static AnnotationInfo returnType(PyFunction function) {
        if (function.getAnnotation() != null && function.getAnnotation().getValue() != null) {
            return new AnnotationInfo(function.getAnnotation().getValue().getText());
        }

        PySignature signature = PySignatureCacheManager.getInstance(function.getProject()).findSignature(function);
        if (signature != null) {
            String qualifiedName = signature.getReturnTypeQualifiedName();
            if (qualifiedName != null) {
                return new AnnotationInfo(qualifiedName);
            }
        }

        TypeEvalContext context = TypeEvalContext.userInitiated(function.getProject(), function.getContainingFile());
        PyType inferredType = context.getReturnType(function);
        if (function.isAsync()) {
            // TODO consulo: Ref.deref missing
            Ref<PyType> unwrappedRef = PyTypingTypeProvider.unwrapCoroutineReturnType(inferredType);
            inferredType = unwrappedRef != null ? unwrappedRef.get() : null;
        }
        // Annotate with the wide type (e.g. `str`), not a one-return literal like `Literal["42"]`.
        inferredType = PyLiteralType.upcastLiteralToClass(inferredType);
        return new AnnotationInfo(
                PythonDocumentationProvider.getTypeHint(inferredType, context),
                PythonDocumentationProvider.getFullyQualifiedTypeHint(inferredType, context)
        );
    }

    public static @Nullable PyExpression annotateReturnType(Project project, PyFunction function, boolean createTemplate) {
        AnnotationInfo returnTypeAnnotation = returnType(function);

        String returnTypeText = returnTypeAnnotation.getAnnotationText();
        String annotationText = "-> " + returnTypeText;

        PyTypeHintGenerationUtil.addImportsForTypeAnnotations(returnTypeAnnotation.getFullyQualifiedTypeHints(), function);

        PyFunction annotatedFunction = PyUtil.updateDocumentUnblockedAndCommitted(function, document -> {
            PyAnnotation oldAnnotation = function.getAnnotation();
            if (oldAnnotation != null) {
                TextRange oldRange = oldAnnotation.getTextRange();
                document.replaceString(oldRange.getStartOffset(), oldRange.getEndOffset(), annotationText);
            }
            else {
                PsiElement prevElem = PyPsiUtils.getPrevNonCommentSibling(function.getStatementList(), true);
                assert prevElem != null;
                TextRange range = prevElem.getTextRange();
                if (prevElem.getNode().getElementType() == PyTokenTypes.COLON) {
                    document.insertString(range.getStartOffset(), " " + annotationText);
                }
                else {
                    document.insertString(range.getEndOffset(), " " + annotationText + ":");
                }
            }
            return CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(function);
        });

        if (annotatedFunction == null) {
            return null;
        }

        PyAnnotation annotation = annotatedFunction.getAnnotation();
        assert annotation != null;
        PyExpression annotationValue = annotation.getValue();
        assert annotationValue != null : "Generated function must have annotation";

        if (createTemplate) {
            int offset = annotationValue.getTextOffset();

            TemplateBuilder builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(annotationValue);
            builder.replaceRange(TextRange.create(0, returnTypeText.length()), returnTypeText);
            Editor targetEditor =
                    PythonUiService.getInstance().openTextEditor(project, annotatedFunction.getContainingFile().getVirtualFile(), offset);
            if (targetEditor != null) {
                builder.run(targetEditor, true);
            }
        }
        return annotationValue;
    }

    @Override
    protected boolean isParamTypeDefined(PyNamedParameter parameter) {
        if (parameter.getAnnotation() != null) {
            return true;
        }
        if (parameter.getTypeComment() != null) {
            return true;
        }
        PyFunction function = PsiTreeUtil.getParentOfType(parameter, PyFunction.class);
        return function != null && function.getTypeComment() != null;
    }

    @Override
    protected boolean isReturnTypeDefined(PyFunction function) {
        return function.getAnnotation() != null || function.getTypeComment() != null;
    }

    @Override
    protected void updateText(boolean isReturn) {
        setText(isReturn ? PyLocalize.intnSpecifyReturnTypeInAnnotation() : PyLocalize.intnSpecifyTypeInAnnotation());
    }
}

