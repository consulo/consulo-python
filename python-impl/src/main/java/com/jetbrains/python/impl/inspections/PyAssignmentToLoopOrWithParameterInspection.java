// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.PyElsePart;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyForStatement;
import com.jetbrains.python.psi.PySubscriptionExpression;
import com.jetbrains.python.psi.PyWithItem;
import com.jetbrains.python.psi.PyWithStatement;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiReference;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.function.Condition;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

//TODO: Try to share logic with AssignmentToForLoopParameterInspection

/**
  * Checks for cases when you rewrite loop variable with inner loop.
  * It finds all {@code with} and {@code for} statements, takes variables declared by them and ensures none of parent
  * {@code with} or {@code for} declares variable with the same name
  *
  * @author link
  */
@ExtensionImpl
public final class PyAssignmentToLoopOrWithParameterInspection extends PyInspection {
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameAssignmentToLoopOrWithParameter();
    }

    @Override
    public LocalizeValue getGroupDisplayName() {
        return PyLocalize.inspGroupPythonAssignmentIssues();
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
        return new Visitor(holder, PyInspectionVisitor.getContext(session));
    }

    private static final class Visitor extends PyInspectionVisitor {
        private Visitor(@Nullable ProblemsHolder holder,
                        TypeEvalContext context) {
            super(holder, context);
        }

        @Override
        public void visitPyWithStatement(PyWithStatement node) {
            checkNotReDeclaringUpperLoopOrStatement(node);
        }

        @Override
        public void visitPyForStatement(PyForStatement node) {
            checkNotReDeclaringUpperLoopOrStatement(node);
        }

        /**
          * Finds first parent of specific type (See {@link #isRequiredStatement(PsiElement)})
          * that declares one of names, declared in this statement
          */
        private void checkNotReDeclaringUpperLoopOrStatement(PsiElement statement) {
            for (PyExpression declaredVar : getNamedElementsOfForAndWithStatements(statement)) {
                Filter filter = new Filter(handleSubscriptionsAndResolveSafely(declaredVar));
                PsiElement firstParent = PsiTreeUtil.findFirstParent(statement, true, filter);
                if ((firstParent != null) && isRequiredStatement(firstParent)) {
                    // If parent is "for", we need to check that statement not declared in "else": PY-12367
                    if ((firstParent instanceof PyForStatement) && isDeclaredInElse(statement, (PyForStatement)firstParent)) {
                        continue;
                    }
                    registerProblem(declaredVar,
                                    PyInspectionMessages.problemMessage(a -> PyLocalize.inspAssignmentToLoopOrWithParameter(a[0]), declaredVar.getText()));
                }
            }
        }
    }

    /**
      * Checks that element is declared in "else" statement of "for" statement
      *
      * @param elementToCheck element to check
      * @param forStatement   statement to obtain "else" part from
      * @return true if declared in "Else" block
      */
    private static boolean isDeclaredInElse(PsiElement elementToCheck, PyForStatement forStatement) {
        PyElsePart elsePart = forStatement.getElsePart();
        if (elsePart != null) {
            if (PsiTreeUtil.isAncestor(elsePart, elementToCheck, false)) {
                return true;
            }
        }
        return false;
    }

    /**
      * Filters list of parents trying to find parent that declares var that refers to {@link #myNode}
      * Returns {@link ScopeOwner} if nothing found.
      * Returns parent otherwise.
      */
    private static final class Filter implements Condition<PsiElement> {
        private final PsiElement myNode;

        private Filter(PsiElement node) {
            this.myNode = node;
        }

        @Override
        public boolean value(PsiElement psiElement) {
            if (psiElement instanceof ScopeOwner) {
                return true; //Do not go any further
              }
            if (!isRequiredStatement(psiElement)) {
                return false; //Parent has wrong type, skip
              }
            List<PyExpression> varsDeclaredInStatement = getNamedElementsOfForAndWithStatements(psiElement);
            for (PyExpression varDeclaredInStatement : varsDeclaredInStatement) {
                //For each variable, declared by this parent take first declaration and open subscription list if any
                PsiReference reference = handleSubscriptionsAndResolveSafely(varDeclaredInStatement).getReference();
                if ((reference != null) && reference.isReferenceTo(myNode)) {
                    return true; //One of variables declared by this parent refers to node
                  }
            }
            return false;
        }
    }

    /**
      * Opens subscription list ({@code i[n][q][f] --> i}) and resolves ref recursively to the topmost element,
      * but not further than file borders (to prevent Stub to AST conversion)
      *
      * @param element element to open and resolve
      * @return opened and resolved element
      */
    private static PsiElement handleSubscriptionsAndResolveSafely(PyExpression element) {
        if (element instanceof PySubscriptionExpression) {
            element = ((PySubscriptionExpression)element).getRootOperand();
        }
        return PyUtil.resolveToTheTop(element);
    }

    /**
      * Checks if element is statement this inspection should work with
      *
      * @param element to check
      * @return true if inspection should work with this element
      */
    private static boolean isRequiredStatement(PsiElement element) {
        assert element != null;
        return (element instanceof PyWithStatement) || (element instanceof PyForStatement);
    }

    private static List<PyExpression> getNamedElementsOfForAndWithStatements(PsiElement element) {
        if (element instanceof PyForStatement forStatement) {
            PyExpression target = forStatement.getForPart().getTarget();

            return dropUnderscores(PyUtil.flattenedParensAndStars(target));
        }
        else if (element instanceof PyWithStatement withStatement) {
            List<PyExpression> result = new ArrayList<>();

            for (PyWithItem item : withStatement.getWithItems()) {
                PyExpression target = item.getTarget();
                if (target != null) {
                    result.addAll(PyUtil.flattenedParensAndTuples(target));
                }
            }

            return dropUnderscores(result);
        }

        return Collections.emptyList();
    }

    private static List<PyExpression> dropUnderscores(List<PyExpression> expressions) {
        return ContainerUtil.filter(expressions,
                                    expression -> !PyNames.UNDERSCORE.equals(expression.getText()));
    }
}
