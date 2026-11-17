// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.quickfix;

import com.jetbrains.python.impl.codeInsight.intentions.PyTypeHintGenerationUtil;
import com.jetbrains.python.impl.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.impl.documentation.PythonDocumentationProvider;
import com.jetbrains.python.impl.psi.types.PyLiteralType;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyAnnotation;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiComment;
import consulo.language.psi.SmartPointerManager;
import consulo.language.psi.SmartPsiElementPointer;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.lang.ref.Ref;
import org.jspecify.annotations.Nullable;

import java.util.List;

// TODO consulo: PsiUpdateModCommandAction missing, ported as a LocalQuickFix over a smart pointer to the function
public class PyMakeFunctionReturnTypeQuickFix implements LocalQuickFix {
    private final SmartPsiElementPointer<PyFunction> myFunction;
    private final String myReturnTypeName;
    private final String myReturnTypeFqName;

    public PyMakeFunctionReturnTypeQuickFix(PyFunction function, TypeEvalContext context) {
        myFunction = SmartPointerManager.getInstance(function.getProject()).createSmartPsiElementPointer(function);
        PyType type = getReturnType(function, context);
        myReturnTypeName = PythonDocumentationProvider.getTypeHint(type, context);
        myReturnTypeFqName = PythonDocumentationProvider.getFullyQualifiedTypeHint(type, context);
    }

    private static @Nullable PyType getReturnType(PyFunction function, TypeEvalContext context) {
        PyType type = function.getInferredReturnType(context);
        if (function.isAsync()) {
            Ref<PyType> unwrappedType = PyTypingTypeProvider.unwrapCoroutineReturnType(type);
            if (unwrappedType != null) {
                type = unwrappedType.get();
            }
        }
        // Annotate with the wide type (e.g. `int`), not a one-return literal like `Literal[42]`, including literals nested
        // inside generic return types such as `Generator[...]` or `Callable[..., ...]`.
        return PyLiteralType.upcastLiteralToClassDeep(type, context);
    }

    @Override
    @RequiredReadAction
    public LocalizeValue getName() {
        PyFunction function = myFunction.getElement();
        if (function == null) {
            return PyLocalize.qfixNameMakeFunctionReturnType();
        }
        return PyLocalize.qfixMakeFunctionReturnType(function.getName(), myReturnTypeName);
    }

    @Override
    public void applyFix(Project project, ProblemDescriptor descriptor) {
        PyFunction function = myFunction.getElement();
        if (function == null) {
            return;
        }
        PyElementGenerator elementGenerator = PyElementGenerator.getInstance(function.getProject());

        PyAnnotation annotation = function.getAnnotation();
        if (annotation != null) {
            PyExpression annotationExpr = annotation.getValue();
            if (annotationExpr != null) {
                annotationExpr.replace(elementGenerator.createExpressionFromText(LanguageLevel.PYTHON34, myReturnTypeName));
                PyTypeHintGenerationUtil.addImportsForTypeAnnotations(List.of(myReturnTypeFqName), function);
            }
        }

        PsiComment typeComment = function.getTypeComment();
        if (typeComment != null) {
            StringBuilder typeCommentAnnotation = new StringBuilder(typeComment.getText());
            typeCommentAnnotation.delete(typeCommentAnnotation.indexOf("->"), typeCommentAnnotation.length());
            typeCommentAnnotation.append("-> ").append(myReturnTypeName);
            typeComment.replace(
                elementGenerator.createFromText(LanguageLevel.PYTHON27, PsiComment.class, typeCommentAnnotation.toString()));
            PyTypeHintGenerationUtil.addImportsForTypeAnnotations(List.of(myReturnTypeFqName), function);
        }
    }
}
