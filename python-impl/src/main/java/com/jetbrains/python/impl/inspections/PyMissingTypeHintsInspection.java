// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.debugger.PySignature;
import com.jetbrains.python.impl.PythonUiService;
import com.jetbrains.python.impl.debugger.PySignatureCacheManager;
import com.jetbrains.python.impl.inspections.quickfix.PyQuickFixUtil;
import com.jetbrains.python.impl.pyi.PyiUtil;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.ast.ASTNode;
import consulo.language.editor.inspection.InspectionToolState;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;

@ExtensionImpl
public final class PyMissingTypeHintsInspection extends PyInspection {
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameMissingTypeHints();
    }

    @Override
    public LocalizeValue getGroupDisplayName() {
        return PyLocalize.inspGroupPythonTypeHinting();
    }

    @Override
    public boolean isEnabledByDefault() {
        return false;
    }

    @Override
    public HighlightDisplayLevel getDefaultLevel() {
        return HighlightDisplayLevel.WEAK_WARNING;
    }
    @Override
    public InspectionToolState<?> createStateProvider() {
        return new PyMissingTypeHintsInspectionState();
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
        PyMissingTypeHintsInspectionState inspectionState = (PyMissingTypeHintsInspectionState) state;
        return new PyInspectionVisitor(holder, context) {
            @Override
            public void visitPyFunction(PyFunction function) {
                if (function.getTypeComment() == null &&
                    !typeAnnotationsExist(function) &&
                    PyiUtil.getOverloads(function, getResolveContext().getTypeEvalContext()).isEmpty()) {
                    boolean flag = shouldRegisterProblem(function, inspectionState.m_onlyWhenTypesAreKnown);
                    if (flag) {
                        ASTNode nameNode = function.getNameNode();
                        if (nameNode != null) {
                            registerProblem(nameNode.getPsi(),
                                            PyLocalize.inspMissingTypeHintsTypeHintingMissingForFunctionDefinition().get(),
                                            new AddTypeHintsQuickFix(function.getName()));
                        }
                    }
                }
            }
        };
    }

    private static boolean shouldRegisterProblem(PyFunction function, boolean onlyWhenTypesAreKnown) {
        if (onlyWhenTypesAreKnown) {
            PySignatureCacheManager instance = PySignatureCacheManager.getInstance(function.getProject());
            if (instance != null) {
                PySignature signature = instance.findSignature(function);
                return signature != null && canAnnotate(signature);
            }
            else {
                return false;
            }
        }
        else {
            return true;
        }
    }

    private static boolean canAnnotate(PySignature signature) {
        return !"NoneType".equals(signature.getReturnTypeQualifiedName())
               || signature.getArgs().size() > 1
               || (signature.getArgs().size() == 1 && !"self".equals(signature.getArgs().get(0).getName()));
    }

    private static boolean typeAnnotationsExist(PyFunction function) {
        for (PyParameter param : function.getParameterList().getParameters()) {
            PyNamedParameter namedParameter = param.getAsNamed();
            if (namedParameter != null) {
                if (namedParameter.getAnnotation() != null) {
                    return true;
                }
            }
        }
        if (function.getAnnotation() != null) {
            return true;
        }

        return false;
    }

    private static class AddTypeHintsQuickFix implements LocalQuickFix {
        private final String myName;

        AddTypeHintsQuickFix(String name) {
            myName = name;
        }

        @Override
        public LocalizeValue getName() {
            return PyLocalize.inspMissingTypeHintsAddTypeHintsFor(myName);
        }

        @Override
        public void applyFix(Project project, ProblemDescriptor descriptor) {
            PyFunction function = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PyFunction.class);

            if (function != null) {
                PythonUiService.getInstance().annotateTypesIntention(PyQuickFixUtil.getEditor(function), function);
            }
        }
    }
}
