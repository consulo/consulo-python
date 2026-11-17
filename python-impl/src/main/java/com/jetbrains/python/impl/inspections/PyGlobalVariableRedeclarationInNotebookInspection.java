// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.impl.inspections.quickfix.PySuppressInspectionFix;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyForStatement;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.collection.ContainerUtil;
import consulo.util.io.FileUtil;
import org.jspecify.annotations.Nullable;

import java.util.Set;

/**
  * Detects global variable redeclaration in Jupyter notebooks.
  * <p>
  * TODO PY-85045: this inspection is disabled by default and needs further refinement
  */
@ExtensionImpl
public final class PyGlobalVariableRedeclarationInNotebookInspection extends PyInspection {
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameGlobalVariableRedeclarationInNotebook();
    }

    @Override
    public LocalizeValue getGroupDisplayName() {
        return PyLocalize.inspGroupPythonNotebooks();
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
    public PsiElementVisitor buildVisitor(ProblemsHolder holder,
                                          boolean isOnTheFly,
                                          LocalInspectionToolSession session,
                                          Object state) {
        // the visitor is applied to every element on its own, so gating it from visitPyFile would not stop the target visits
        if (!isNotebook(session.getFile())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        return new Visitor(holder, PyInspectionVisitor.getContext(session));
    }

    // the session file is the base-language root of the notebook's view provider, i.e. a Jupyter file rather than the
    // Python one the targets below come from, so only its name can tell a notebook apart
    private static boolean isNotebook(PsiFile file) {
        return "ipynb".equals(FileUtil.getExtension(file.getName()));
    }

    private static class Visitor extends PyInspectionVisitor {
        private static final Set<String> PLOTTING_VARIABLE_NAMES = Set.of(
          "fig", "ax", "axs", "axes",
          "g", "grid",
          "p", "plot"
        );

        Visitor(@Nullable ProblemsHolder holder, TypeEvalContext context) {
            super(holder, context);
        }

        @Override
        public void visitPyTargetExpression(PyTargetExpression node) {
            if (node.isQualified() || PyNames.UNDERSCORE.equals(node.getName())) {
                return;
            }

            String name = node.getName();
            if (name == null) {
                return;
            }

            ScopeOwner owner = ScopeUtil.getScopeOwner(node);
            if (!(owner instanceof PyFile pyFile)) {
                return;
            }

            if (isSelfReference(node, name) || isDefinedInLoop(node) || isPlottingVariable(node, name, myTypeEvalContext)) {
                return;
            }

            PyTargetExpression firstDefinition = pyFile.findTopLevelAttribute(name);
            if (firstDefinition != null && firstDefinition != node) {
                PsiElement identifier = node.getNameIdentifier();
                String message = PyLocalize.inspGlobalVariableRedeclarationInNotebook(name).get();

                registerProblem(
                  identifier != null ? identifier : node,
                  message,
                  ProblemHighlightType.WEAK_WARNING,
                  null,
                  new PySuppressInspectionFix("PyGlobalVariableRedeclarationInNotebook",
                                              PyLocalize.inspPythonSuppressorSuppressForStatement(),
                                              PyStatement.class)
                );
            }
        }

        private static boolean isDefinedInLoop(PyTargetExpression node) {
            PyForStatement forStatement = PsiTreeUtil.getParentOfType(node, PyForStatement.class);
            if (forStatement == null) {
                return false;
            }
            PsiElement loopTarget = forStatement.getForPart().getTarget();
            return loopTarget != null && PsiTreeUtil.isAncestor(loopTarget, node, false);
        }

        private static boolean isSelfReference(PyTargetExpression node, String name) {
            PsiElement parent = node.getParent();
            if (parent == null) {
                return false;
            }
            return ContainerUtil.exists(PsiTreeUtil.findChildrenOfType(parent, PyReferenceExpression.class),
                                        ref -> name.equals(ref.getName()) && ref.getQualifier() == null);
        }

        /**
          * Heuristic: plotting objects are frequently and safely redefined in notebooks.
          */
        private static boolean isPlottingVariable(PyTargetExpression target,
                                                  String name,
                                                  TypeEvalContext context) {
            if (PLOTTING_VARIABLE_NAMES.contains(name)) {
                return true;
            }

            PyAssignmentStatement assignment = PsiTreeUtil.getParentOfType(target, PyAssignmentStatement.class);
            if (assignment == null) {
                return false;
            }

            PyExpression value = assignment.getAssignedValue();
            if (value == null) {
                return false;
            }

            PyType valueType = context.getType(value);
            if (!(valueType instanceof PyClassType classType)) {
                return false;
            }

            String qName = classType.getClassQName();
            if (qName == null) {
                return false;
            }

            boolean isMatplotlibFigureOrAxes = qName.startsWith("matplotlib") && (qName.contains("Figure") || qName.contains("axes"));
            boolean isPlotlyFigure = qName.startsWith("plotly") && qName.contains("Figure");
            return isMatplotlibFigureOrAxes || isPlotlyFigure;
        }
    }
}