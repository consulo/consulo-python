// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.ast.PyAstFunction;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyAugAssignmentStatement;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyForStatement;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyNamedElementContainer;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.PyTupleExpression;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiNamedElement;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
  * Reports assignment to 'self' or 'cls'.
  */
@ExtensionImpl
public final class PyMethodFirstArgAssignmentInspection extends PyInspection {
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameFirstArgAssign();
    }

    @Override
    public LocalizeValue getGroupDisplayName() {
        return PyLocalize.inspGroupPythonAssignmentIssues();
    }

    @Override
    public PsiElementVisitor buildVisitor(ProblemsHolder holder,
                                          boolean isOnTheFly,
                                          LocalInspectionToolSession session,
                                          Object state) {
        return new Visitor(holder, PyInspectionVisitor.getContext(session));
    }

    public static class Visitor extends PyInspectionVisitor {
        public Visitor(@Nullable ProblemsHolder holder, TypeEvalContext context) {
            super(holder, context);
        }

        private void complain(PsiElement element, String name) {
            registerProblem(element, PyInspectionMessages.problemMessage(a -> PyLocalize.inspFirstArgAssignMethodParameterReassigned(a[0]), name));
        }

        private void handleTarget(PyQualifiedExpression target, String name) {
            if (!target.isQualified() && name.equals(target.getText())) {
                complain(target, name);
            }
        }

        private static @Nullable String extractFirstParamName(PyElement node) {
            // are we a method?
            List<? extends PsiElement> place = PyUtil.searchForWrappingMethod(node, true);
            if (place == null || place.size() < 2) {
                return null;
            }
            PyFunction method = (PyFunction)place.get(place.size() - 2);
            //PyClass owner = (PyClass)place.get(place.size()-1);
            // what is our first param?
            PyParameter[] params = method.getParameterList().getParameters();
            if (params.length < 1) {
                return null; // no params
            }
            PyNamedParameter first_parm = params[0].getAsNamed();
            if (first_parm == null) {
                return null;
            }
            if (first_parm.isKeywordContainer() || first_parm.isPositionalContainer()) {
                return null; // legal but crazy cases; back off
            }
            String first_param_name = first_parm.getName();
            if (first_param_name == null || first_param_name.isEmpty()) {
                return null; // ignore cases of incorrect code
            }
            // is it a static method?
            PyFunction.Modifier modifier = method.getModifier();
            if (modifier == PyAstFunction.Modifier.STATICMETHOD) {
                return null; // these may do whatever they please
            }
            return first_param_name;
        }

        private void markNameDefiner(PyNamedElementContainer definer) {
            String first_param_name = extractFirstParamName((PyElement)definer);
            if (first_param_name != null) {
                // check the targets
                for (PsiNamedElement elt : definer.getNamedElements()) {
                    if (elt instanceof PyTargetExpression) {
                        handleTarget((PyTargetExpression)elt, first_param_name);
                    }
                }
            }
        }

        @Override
        public void visitPyAssignmentStatement(PyAssignmentStatement node) {
            markNameDefiner(node);
        }

        @Override
        public void visitPyAugAssignmentStatement(PyAugAssignmentStatement node) {
            String first_param_name = extractFirstParamName(node);
            if (first_param_name != null) {
                PyExpression target = node.getTarget();
                if (target instanceof PyQualifiedExpression) {
                    handleTarget((PyQualifiedExpression)target, first_param_name);
                }
                else if (target instanceof PyTupleExpression) {
                    for (PyExpression elt : PyUtil.flattenedParensAndTuples(((PyTupleExpression)target).getElements())) {
                        if (elt instanceof PyQualifiedExpression) {
                            handleTarget((PyQualifiedExpression)elt, first_param_name);
                        }
                    }
                }
            }
        }

        @Override
        public void visitPyForStatement(PyForStatement node) {
            markNameDefiner(node);
        }

        /* TODO: implement when WithStmt is part-ified
        @Override
        public void visitPyWithStatement(PyWithStatement node) {
            super.visitPyWithStatement(node);
        }
        */

        private void markDefinition(PyElement definer) {
            String first_param_name = extractFirstParamName(definer);
            if (first_param_name != null && first_param_name.equals(definer.getName())) {
                complain(definer.getNode().findChildByType(PyTokenTypes.IDENTIFIER).getPsi(),
                         first_param_name); // no NPE here, or we won't have the name
              }
        }

        @Override
        public void visitPyFunction(PyFunction definer) {
            markDefinition(definer);
        }

        @Override
        public void visitPyClass(PyClass definer) {
            markDefinition(definer);
        }
    }
}
