// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.inspections.quickfix.AddCallSuperQuickFix;
import com.jetbrains.python.impl.psi.PyKnownDecoratorUtil;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.PyRecursiveElementVisitor;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiReference;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

import static com.jetbrains.python.PyNames.CANONICAL_SELF;
import static com.jetbrains.python.PyNames.INIT;
import static com.jetbrains.python.PyNames.SUPER;
import static com.jetbrains.python.PyNames.__CLASS__;

/**
  * User: catherine
  * <p>
  * Inspection to warn if call to super constructor in class is missed
  */
@ExtensionImpl
public final class PyMissingConstructorInspection extends PyInspection {
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameMissingSuperConstructor();
    }

    @Override
    public LocalizeValue getGroupDisplayName() {
        return PyLocalize.inspGroupPythonInheritance();
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
            PsiElement[] superClasses = node.getSuperClassExpressions();

            if (superClasses.length == 0 ||
                superClasses.length == 1 && PyNames.OBJECT.equals(superClasses[0].getText()) ||
                !superHasNonAbstractConstructor(node, myTypeEvalContext)) {
                return;
            }

            PyFunction initMethod = node.findMethodByName(INIT, false, myTypeEvalContext);

            if (initMethod == null || isExceptionClass(node, myTypeEvalContext) || hasConstructorCall(node, initMethod, myTypeEvalContext)) {
                return;
            }

            if (superClasses.length == 1 || node.isNewStyleClass(myTypeEvalContext)) {
                registerProblem(initMethod.getNameIdentifier(), PyLocalize.inspMissingSuperConstructorMessage().get(),
                                new AddCallSuperQuickFix());
            }
            else {
                registerProblem(initMethod.getNameIdentifier(), PyLocalize.inspMissingSuperConstructorMessage().get());
            }
        }

        private static boolean superHasNonAbstractConstructor(PyClass cls, TypeEvalContext context) {
            String className = cls.getName();

            for (PyClass baseClass : cls.getAncestorClasses(context)) {
                if (!PyUtil.isObjectClass(baseClass) &&
                    !Objects.equals(className, baseClass.getName())) {
                    PyFunction initMethod = baseClass.findMethodByName(INIT, false, context);
                    if (initMethod != null && !PyKnownDecoratorUtil.hasAbstractDecorator(initMethod, context)) {
                        return true;
                    }
                }
            }

            return false;
        }

        private static boolean isExceptionClass(PyClass cls, TypeEvalContext context) {
            if (PyBroadExceptionInspection.equalsException(cls, context)) {
                return true;
            }

            return cls.getAncestorClasses(context)
              .stream()
              .anyMatch(baseClass -> PyBroadExceptionInspection.equalsException(baseClass, context));
        }

        private static boolean hasConstructorCall(PyClass cls, PyFunction initMethod, TypeEvalContext context) {
            CallVisitor visitor = new CallVisitor(cls, context);
            initMethod.getStatementList().accept(visitor);
            return visitor.myHasConstructorCall;
        }

        private static class CallVisitor extends PyRecursiveElementVisitor {

            private final PyClass myClass;

            private final TypeEvalContext myContext;

            private boolean myHasConstructorCall = false;

            CallVisitor(PyClass cls, TypeEvalContext context) {
                myClass = cls;
                myContext = context;
            }

            @Override
            public void visitPyCallExpression(PyCallExpression node) {
                if (isConstructorCall(node, myClass, myContext)) {
                    myHasConstructorCall = true;
                }
            }

            private static boolean isConstructorCall(PyCallExpression call, PyClass cls, TypeEvalContext context) {
                PyExpression callee = call.getCallee();

                if (callee == null || !INIT.equals(callee.getName())) {
                    return false;
                }

                PyExpression calleeQualifier = Optional
                  .of(callee)
                  .filter(PyQualifiedExpression.class::isInstance)
                  .map(PyQualifiedExpression.class::cast)
                  .map(PyQualifiedExpression::getQualifier)
                  .orElse(null);

                return calleeQualifier != null && (isSuperCall(calleeQualifier, cls, context) || isSuperClassCall(calleeQualifier, cls, context));
            }

            private static boolean isSuperCall(PyExpression calleeQualifier,
                                               PyClass cls,
                                               TypeEvalContext context) {
                String prevCalleeName = Optional
                  .of(calleeQualifier)
                  .filter(PyCallExpression.class::isInstance)
                  .map(PyCallExpression.class::cast)
                  .map(PyCallExpression::getCallee)
                  .map(PyExpression::getName)
                  .orElse(null);

                if (!SUPER.equals(prevCalleeName)) {
                    return false;
                }

                PyExpression[] args = ((PyCallExpression)calleeQualifier).getArguments();

                if (args.length == 0) {
                    return true;
                }

                String firstArg = args[0].getText();
                String classQName = cls.getQualifiedName();

                if (firstArg.equals(cls.getName()) ||
                    firstArg.equals(CANONICAL_SELF + "." + __CLASS__) ||
                    classQName != null && classQName.endsWith(firstArg) ||
                    firstArg.equals(__CLASS__) && !LanguageLevel.forElement(cls).isPython2()) {
                    return true;
                }

                return cls.getAncestorClasses(context)
                  .stream()
                  .map(PyClass::getName)
                  .anyMatch(firstArg::equals);
            }

            private static boolean isSuperClassCall(PyExpression calleeQualifier,
                                                    PyClass cls,
                                                    TypeEvalContext context) {
                PsiElement callingClass = resolveCallingClass(calleeQualifier);

                return callingClass != null &&
                       cls.getAncestorClasses(context)
                         .stream()
                         .anyMatch(callingClass::equals);
            }

            private static @Nullable PsiElement resolveCallingClass(PyExpression calleeQualifier) {
                if (calleeQualifier instanceof PyCallExpression) {
                    return Optional
                      .of((PyCallExpression)calleeQualifier)
                      .map(PyCallExpression::getCallee)
                      .map(PyExpression::getReference)
                      .map(PsiReference::resolve)
                      .orElse(null);
                }
                else {
                    return Optional
                      .ofNullable(calleeQualifier.getReference())
                      .map(PsiReference::resolve)
                      .orElse(null);
                }
            }
        }
    }
}
