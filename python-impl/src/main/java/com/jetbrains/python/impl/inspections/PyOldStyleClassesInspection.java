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

import com.google.common.collect.Lists;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.inspections.quickfix.PyChangeBaseClassQuickFix;
import com.jetbrains.python.impl.inspections.quickfix.PyConvertToNewStyleQuickFix;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.PyClassType;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.ast.ASTNode;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.List;

/**
 * Inspection to detect occurrences of new-style class features in old-style classes
 *
 * @author catherine
 */
@ExtensionImpl
public class PyOldStyleClassesInspection extends PyInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameOldstyleClass();
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

    private static class Visitor extends PyInspectionVisitor {
        public Visitor(@Nullable ProblemsHolder holder, @Nonnull LocalInspectionToolSession session) {
            super(holder, session);
        }

        @Override
        public void visitPyClass(PyClass node) {
            List<PyClassLikeType> expressions = node.getSuperClassTypes(myTypeEvalContext);
            List<LocalQuickFix> quickFixes = Lists.<LocalQuickFix>newArrayList(new PyConvertToNewStyleQuickFix());
            if (!expressions.isEmpty()) {
                quickFixes.add(new PyChangeBaseClassQuickFix());
            }
            if (!node.isNewStyleClass(myTypeEvalContext)) {
                for (PyTargetExpression attr : node.getClassAttributes()) {
                    if (PyNames.SLOTS.equals(attr.getName())) {
                        registerProblem(
                            attr,
                            PyLocalize.inspOldstyleClassSlots().get(),
                            ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                            null,
                            quickFixes.toArray(new LocalQuickFix[quickFixes.size()])
                        );
                    }
                }
                for (PyFunction attr : node.getMethods()) {
                    if (PyNames.GETATTRIBUTE.equals(attr.getName())) {
                        ASTNode nameNode = attr.getNameNode();
                        assert nameNode != null;
                        registerProblem(
                            nameNode.getPsi(),
                            PyLocalize.inspOldstyleClassGetattribute().get(),
                            ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                            null,
                            quickFixes.toArray(new
                                LocalQuickFix[quickFixes.size()])
                        );
                    }
                }
            }
        }

        @Override
        public void visitPyCallExpression(PyCallExpression node) {
            PyClass klass = PsiTreeUtil.getParentOfType(node, PyClass.class);
            if (klass != null && !klass.isNewStyleClass(myTypeEvalContext)) {
                List<PyClassLikeType> types = klass.getSuperClassTypes(myTypeEvalContext);
                for (PyClassLikeType type : types) {
                    if (type == null) {
                        return;
                    }
                    String qName = type.getClassQName();
                    if (qName != null && qName.contains("PyQt")) {
                        return;
                    }
                    if (!(type instanceof PyClassType)) {
                        return;
                    }
                }
                List<LocalQuickFix> quickFixes = Lists.<LocalQuickFix>newArrayList(new PyConvertToNewStyleQuickFix());
                if (!types.isEmpty()) {
                    quickFixes.add(new PyChangeBaseClassQuickFix());
                }

                if (PyUtil.isSuperCall(node)) {
                    PyExpression callee = node.getCallee();
                    if (callee != null) {
                        registerProblem(
                            callee,
                            PyLocalize.inspOldstyleClassSuper().get(),
                            ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                            null,
                            quickFixes.toArray(quickFixes.toArray(new
                                LocalQuickFix[quickFixes.size()]))
                        );
                    }
                }
            }
        }
    }
}
