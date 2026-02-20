/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.jetbrains.python.impl.inspections.quickfix.DocstringQuickFix;
import com.jetbrains.python.inspections.PyInspectionExtension;
import com.jetbrains.python.psi.*;
import consulo.annotation.component.ExtensionImpl;
import consulo.document.util.TextRange;
import consulo.language.ast.ASTNode;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;

/**
 * @author Mikhail Golubev
 */
@ExtensionImpl
public class PyMissingOrEmptyDocstringInspection extends PyBaseDocstringInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameMissingOrEmptyDocstring();
    }

    @Nonnull
    @Override
    public Visitor buildVisitor(
        @Nonnull ProblemsHolder holder,
        boolean isOnTheFly,
        @Nonnull LocalInspectionToolSession session,
        Object state
    ) {
        return new Visitor(holder, session) {
            @Override
            protected void checkDocString(@Nonnull PyDocStringOwner node) {
                PyStringLiteralExpression docStringExpression = node.getDocStringExpression();
                if (docStringExpression == null) {
                    for (PyInspectionExtension extension : PyInspectionExtension.EP_NAME.getExtensionList()) {
                        if (extension.ignoreMissingDocstring(node)) {
                            return;
                        }
                    }
                    PsiElement marker = null;
                    if (node instanceof PyClass) {
                        ASTNode n = ((PyClass) node).getNameNode();
                        if (n != null) {
                            marker = n.getPsi();
                        }
                    }
                    else if (node instanceof PyFunction) {
                        ASTNode n = ((PyFunction) node).getNameNode();
                        if (n != null) {
                            marker = n.getPsi();
                        }
                    }
                    else if (node instanceof PyFile) {
                        TextRange tr = new TextRange(0, 0);
                        ProblemsHolder holder = getHolder();
                        if (holder != null) {
                            holder.newProblem(PyLocalize.inspNoDocstring())
                                .range(node, tr)
                                .create();
                        }
                        return;
                    }
                    if (marker == null) {
                        marker = node;
                    }
                    if (node instanceof PyFunction || (node instanceof PyClass && ((PyClass) node).findInitOrNew(false, null) != null)) {
                        registerProblem(marker, PyLocalize.inspNoDocstring().get(), new DocstringQuickFix(null, null));
                    }
                    else {
                        registerProblem(marker, PyLocalize.inspNoDocstring().get());
                    }
                }
                else if (StringUtil.isEmptyOrSpaces(docStringExpression.getStringValue())) {
                    registerProblem(docStringExpression, PyLocalize.inspEmptyDocstring().get());
                }
            }
        };
    }
}
