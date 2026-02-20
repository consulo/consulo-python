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
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.debugger.PySignature;
import com.jetbrains.python.impl.debugger.PySignatureCacheManager;
import com.jetbrains.python.impl.debugger.PySignatureUtil;
import com.jetbrains.python.impl.documentation.docstrings.DocStringUtil;
import com.jetbrains.python.impl.documentation.docstrings.PlainDocString;
import com.jetbrains.python.impl.psi.types.PyTypeChecker;
import com.jetbrains.python.impl.psi.types.PyTypeParser;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.StructuredDocString;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.toolbox.Substring;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.*;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author traff
 */
@ExtensionImpl
public class PyDocstringTypesInspection extends PyInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameDocstringTypes();
    }

    @Override
    public boolean isEnabledByDefault() {
        return false;
    }

    @Nonnull
    @Override
    public PsiElementVisitor buildVisitor(
        @Nonnull ProblemsHolder holder,
        boolean isOnTheFly,
        @Nonnull LocalInspectionToolSession session,
        Object state
    ) {
        return new Visitor(holder, session);
    }

    public static class Visitor extends PyInspectionVisitor {
        public Visitor(@Nullable ProblemsHolder holder, @Nonnull LocalInspectionToolSession session) {
            super(holder, session);
        }

        @Override
        public void visitPyFunction(PyFunction function) {
            String name = function.getName();
            if (name != null && !name.startsWith("_")) {
                checkDocString(function);
            }
        }

        private void checkDocString(@Nonnull PyFunction function) {
            PyStringLiteralExpression docStringExpression = function.getDocStringExpression();
            if (docStringExpression != null) {
                PySignatureCacheManager manager = PySignatureCacheManager.getInstance(function.getProject());
                PySignature signature = manager.findSignature(function);
                if (signature != null) {
                    checkParameters(function, docStringExpression, signature);
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
                        String dynamicTypeShortName = PySignatureUtil.getShortestImportableName(function, dynamicType);
                        if (!match(function, dynamicType, type.getValue())) {
                            registerProblem(
                                node,
                                "Dynamically inferred type '" +
                                    dynamicTypeShortName +
                                    "' doesn't match specified type '" +
                                    type + "'",
                                ProblemHighlightType.WEAK_WARNING,
                                null,
                                type.getTextRange(),
                                new ChangeTypeQuickFix(param, type, dynamicTypeShortName, node)
                            );
                        }
                    }
                }
            }
        }

        private boolean match(PsiElement anchor, String dynamicTypeName, String specifiedTypeName) {
            PyType dynamicType = PyTypeParser.getTypeByName(anchor, dynamicTypeName);
            PyType specifiedType = PyTypeParser.getTypeByName(anchor, specifiedTypeName);
            return PyTypeChecker.match(specifiedType, dynamicType, myTypeEvalContext);
        }
    }


    private static class ChangeTypeQuickFix implements LocalQuickFix {
        private final String myParamName;
        private final Substring myTypeSubstring;
        private final String myNewType;
        private final PyStringLiteralExpression myStringLiteralExpression;

        private ChangeTypeQuickFix(String name, Substring substring, String type, PyStringLiteralExpression expression) {
            myParamName = name;
            myTypeSubstring = substring;
            myNewType = type;
            myStringLiteralExpression = expression;
        }

        @Nonnull
        @Override
        public LocalizeValue getName() {
            return LocalizeValue.localizeTODO("Change " + myParamName + " type from " + myTypeSubstring.getValue() + " to " + myNewType);
        }

        @Override
        public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
            String newValue = myTypeSubstring.getTextRange().replace(myTypeSubstring.getSuperString(), myNewType);

            PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);

            myStringLiteralExpression.replace(elementGenerator.createDocstring(newValue).getExpression());
        }
    }
}

