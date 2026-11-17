// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.impl.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.impl.documentation.PythonDocumentationProvider;
import com.jetbrains.python.impl.psi.types.PyTypeUtil;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyCallable;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.types.PyCallableType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.document.util.TextRange;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.lang.ref.Ref;

@ExtensionImpl
public class PyUnnecessaryCastInspection extends PyInspection {
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameUnnecessaryCast();
    }

    @Override
    public LocalizeValue getGroupDisplayName() {
        return PyLocalize.inspGroupPythonTypeHinting();
    }

    @Override
    public HighlightDisplayLevel getDefaultLevel() {
        return HighlightDisplayLevel.WEAK_WARNING;
    }

    // JB registration: editorAttributes="NOT_USED_ELEMENT_ATTRIBUTES"
    // TODO consulo: per-inspection default editor attributes (editorAttributes) missing

    @Override
    public PsiElementVisitor buildVisitor(ProblemsHolder holder,
                                          boolean isOnTheFly,
                                          LocalInspectionToolSession session,
                                          Object state) {
        TypeEvalContext context = PyInspectionVisitor.getContext(session);
        if (context.getUsesExternalTypeEngine()) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        return new PyInspectionVisitor(holder, context) {
            @Override
            @RequiredReadAction
            public void visitPyCallExpression(PyCallExpression callExpression) {
                PyExpression callee = callExpression.getCallee();
                if (callee == null) {
                    return;
                }
                boolean isCastCall = false;
                for (PyCallableType callableType : PyTypeUtil.getCallableItems(context.getType(callee))) {
                    PyCallable callable = callableType.getCallable();
                    String qualifiedName = callable != null ? callable.getQualifiedName() : null;
                    if (PyTypingTypeProvider.CAST.equals(qualifiedName) || PyTypingTypeProvider.CAST_EXT.equals(qualifiedName)) {
                        isCastCall = true;
                        break;
                    }
                }
                if (!isCastCall) {
                    return;
                }

                PyExpression[] args = callExpression.getArguments();
                if (args.length != 2) {
                    return;
                }
                Ref<PyType> targetTypeRef = PyTypingTypeProvider.getType(args[0], myTypeEvalContext);
                // TODO consulo: Ref.deref missing
                PyType targetType = targetTypeRef != null ? targetTypeRef.get() : null;
                PyType actualType = myTypeEvalContext.getType(args[1]);

                if (!PyTypeUtil.isSameType(targetType, actualType, myTypeEvalContext)) {
                    return;
                }
                String toName = PythonDocumentationProvider.getTypeName(targetType, myTypeEvalContext);
                registerProblem(
                    callExpression,
                    PyLocalize.inspUnnecessaryCastMessage(toName).get(),
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                    null,
                    new TextRange(0, callExpression.getArguments()[0].getNextSibling().getTextRange().getEndOffset() -
                        callExpression.getTextRange().getStartOffset()),
                    new RemoveUnnecessaryCastQuickFix()
                );
                registerProblem(
                    callExpression,
                    PyLocalize.inspUnnecessaryCastMessage(toName).get(),
                    ProblemHighlightType.INFORMATION,
                    null,
                    new RemoveUnnecessaryCastQuickFix()
                );
            }
        };
    }

    // JB: PsiUpdateModCommandQuickFix (ModCommand API is not available in Consulo)
    private static class RemoveUnnecessaryCastQuickFix implements LocalQuickFix {
        @Override
        public LocalizeValue getName() {
            return PyLocalize.qfixRemoveCastCall();
        }

        @Override
        @RequiredWriteAction
        public void applyFix(Project project, ProblemDescriptor descriptor) {
            if (!(descriptor.getPsiElement() instanceof PyCallExpression call)) {
                return;
            }
            PyExpression[] args = call.getArguments();
            if (args.length != 2) {
                return;
            }
            PyExpression expr = args[1];
            if (expr == null) {
                return;
            }
            call.replace(expr.copy());
        }
    }
}
