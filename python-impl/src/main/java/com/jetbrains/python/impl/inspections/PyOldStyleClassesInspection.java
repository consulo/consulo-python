// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.inspections.quickfix.PyChangeBaseClassQuickFix;
import com.jetbrains.python.impl.inspections.quickfix.PyConvertToNewStyleQuickFix;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.TypeEvalContext;
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
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * User: catherine
 * <p>
 * Inspection to detect occurrences of new-style class features in old-style classes
 */
@ExtensionImpl
public final class PyOldStyleClassesInspection extends PyInspection {
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameOldstyleClass();
    }

    @Override
    public LocalizeValue getGroupDisplayName() {
        return PyLocalize.inspGroupPythonPython2();
    }

    @Override
    public PsiElementVisitor buildVisitor(ProblemsHolder holder,
                                          boolean isOnTheFly,
                                          LocalInspectionToolSession session,
                                          Object state) {
        return new Visitor(holder, PyInspectionVisitor.getContext(session));
    }

    private static class Visitor extends PyInspectionVisitor {
        Visitor(@Nullable ProblemsHolder holder, TypeEvalContext context) {
            super(holder, context);
        }

        @Override
        public void visitPyClass(PyClass node) {
            List<PyClassLikeType> expressions = node.getSuperClassTypes(myTypeEvalContext);
            List<LocalQuickFix> quickFixes = new ArrayList<>();
            quickFixes.add(new PyConvertToNewStyleQuickFix());
            if (!expressions.isEmpty()) {
                quickFixes.add(new PyChangeBaseClassQuickFix());
            }
            if (!node.isNewStyleClass(myTypeEvalContext)) {
                for (PyTargetExpression attr : node.getClassAttributes()) {
                    if (PyNames.SLOTS.equals(attr.getName())) {
                        registerProblem(attr, PyLocalize.inspOldstyleClassSlots().get(), ProblemHighlightType.GENERIC_ERROR_OR_WARNING, null,
                                        quickFixes.toArray(LocalQuickFix.EMPTY_ARRAY));
                    }
                }
                for (PyFunction attr : node.getMethods()) {
                    if (PyNames.GETATTRIBUTE.equals(attr.getName())) {
                        ASTNode nameNode = attr.getNameNode();
                        assert nameNode != null;
                        registerProblem(nameNode.getPsi(), PyLocalize.inspOldstyleClassGetattribute().get(),
                                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING, null, quickFixes.toArray(LocalQuickFix.EMPTY_ARRAY));
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
                List<LocalQuickFix> quickFixes = new ArrayList<>();
                quickFixes.add(new PyConvertToNewStyleQuickFix());
                if (!types.isEmpty()) {
                    quickFixes.add(new PyChangeBaseClassQuickFix());
                }

                if (PyUtil.isSuperCall(node)) {
                    PyExpression callee = node.getCallee();
                    if (callee != null) {
                        registerProblem(callee, PyLocalize.inspOldstyleClassSuper().get(), ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                        null, quickFixes.toArray(LocalQuickFix.EMPTY_ARRAY));
                    }
                }
            }
        }
    }
}
