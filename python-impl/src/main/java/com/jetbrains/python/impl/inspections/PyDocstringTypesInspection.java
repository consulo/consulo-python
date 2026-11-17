// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import consulo.localize.LocalizeValue;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.project.Project;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import com.jetbrains.python.PyNames;
import consulo.python.impl.localize.PyLocalize;
import com.jetbrains.python.debugger.PySignature;
import com.jetbrains.python.impl.debugger.PySignatureCacheManager;
import com.jetbrains.python.impl.documentation.docstrings.DocStringUtil;
import com.jetbrains.python.documentation.docstrings.PlainDocString;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.StructuredDocString;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.impl.psi.types.PyLegacyDocstringTypeParser;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.impl.psi.types.PyTypeChecker;
import com.jetbrains.python.impl.psi.types.PyUnionType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.toolbox.Substring;
import one.util.streamex.StreamEx;
import org.jspecify.annotations.Nullable;

import static com.jetbrains.python.impl.psi.PyUtil.as;

@ExtensionImpl
public final class PyDocstringTypesInspection extends PyInspection {
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameDocstringTypes();
    }


    @Override
    public PsiElementVisitor buildVisitor(ProblemsHolder holder,
                                          boolean isOnTheFly,
                                          LocalInspectionToolSession session,
                                          Object state) {
        TypeEvalContext context = PyInspectionVisitor.getContext(session);
        if (context.getUsesExternalTypeEngine()) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        return new Visitor(holder, context);
    }

    public static class Visitor extends PyInspectionVisitor {
        public Visitor(@Nullable ProblemsHolder holder, TypeEvalContext context) {
            super(holder, context);
        }

        @Override
        public void visitPyFunction(PyFunction function) {
            String name = function.getName();
            if (name != null && !name.startsWith("_")) {
                checkDocString(function);
            }
        }

        private void checkDocString(PyFunction function) {
            PyStringLiteralExpression docStringExpression = function.getDocStringExpression();
            if (docStringExpression != null) {
                PySignatureCacheManager manager = PySignatureCacheManager.getInstance(function.getProject());
                if (manager != null) {
                    PySignature signature = manager.findSignature(function);
                    if (signature != null) {
                        checkParameters(function, docStringExpression, signature);
                    }
                }
            }
        }

        private void checkParameters(PyFunction function, PyStringLiteralExpression node, PySignature signature) {
            StructuredDocString docString = DocStringUtil.parseDocString(node);
            if (docString instanceof PlainDocString) {
                return;
            }

            for (String param : docString.getParameters()) {
                Substring type = docString.getParamTypeSubstring(param);
                if (type != null) {
                    String dynamicType = signature.getArgTypeQualifiedName(param);
                    if (dynamicType != null) {
                        String dynamicTypeShortName = getShortestImportableName(function, dynamicType);
                        if (dynamicTypeShortName != null && !match(function, dynamicType, type.getValue())) {
                            registerProblem(node, PyLocalize.inspDocstringTypesDynamicallyInferredTypeDoesNotMatchSpecifiedType(dynamicTypeShortName, type).get(),
                                            ProblemHighlightType.WEAK_WARNING, null, type.getTextRange(),
                                            new ChangeTypeQuickFix(param, type, dynamicTypeShortName)
                            );
                        }
                    }
                }
            }
        }

        private @Nullable String getShortestImportableName(@Nullable PsiElement anchor, String type) {
            PyType pyType = PyLegacyDocstringTypeParser.getTypeByName(anchor, type, myTypeEvalContext);
            if (pyType instanceof PyClassType) {
                // a builtin is spelled without its module both in docstrings and in the message
                return PyNames.FQN.unqualifyBuiltinName(((PyClassType)pyType).getPyClass().getQualifiedName());
            }

            if (pyType != null) {
                return getPrintableName(pyType);
            }
            else {
                return type;
            }
        }

        private static @Nullable String getPrintableName(@Nullable PyType type) {
            if (type instanceof PyUnionType unionType) {
                return StreamEx
                    .of(unionType.getMembers())
                    .map(Visitor::getPrintableName)
                    .joining(" or ");
            }
            else if (type != null) {
                return type.getName();
            }
            else {
                return PyNames.ANY_TYPE;
            }
        }

        private boolean match(PsiElement anchor, String dynamicTypeName, String specifiedTypeName) {
            PyType dynamicType = PyLegacyDocstringTypeParser.getTypeByName(anchor, dynamicTypeName, myTypeEvalContext);
            PyType specifiedType = PyLegacyDocstringTypeParser.getTypeByName(anchor, specifiedTypeName, myTypeEvalContext);
            return PyTypeChecker.match(specifiedType, dynamicType, myTypeEvalContext);
        }
    }


    private static final class ChangeTypeQuickFix implements LocalQuickFix {
        private final String myParamName;
        private final Substring myTypeSubstring;
        private final String myNewType;

        private ChangeTypeQuickFix(String name, Substring substring, String type) {
            myParamName = name;
            myTypeSubstring = substring;
            myNewType = type;
        }

        @Override
        public LocalizeValue getName() {
            return PyLocalize.inspDocstringTypesChangeType(myParamName, myTypeSubstring.getValue(), myNewType);
        }

        @Override
        public void applyFix(Project project, ProblemDescriptor descriptor) {
            PsiElement element = descriptor.getPsiElement();
            String newValue = myTypeSubstring.getTextRange().replace(myTypeSubstring.getSuperString(), myNewType);

            PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);

            PyStringLiteralExpression stringLiteralExpression = as(element, PyStringLiteralExpression.class);
            if (stringLiteralExpression != null) {
                stringLiteralExpression.replace(elementGenerator.createDocstring(newValue).getExpression());
            }
        }
    }
}

