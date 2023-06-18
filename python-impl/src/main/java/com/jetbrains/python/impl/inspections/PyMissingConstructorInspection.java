/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.jetbrains.python.impl.PyBundle;
import com.jetbrains.python.impl.inspections.quickfix.AddCallSuperQuickFix;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiReference;
import consulo.util.lang.Comparing;
import org.jetbrains.annotations.Nls;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Optional;

import static com.jetbrains.python.PyNames.*;

/**
 * User: catherine
 * <p>
 * Inspection to warn if call to super constructor in class is missed
 */
@ExtensionImpl
public class PyMissingConstructorInspection extends PyInspection {

  @Nls
  @Nonnull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.missing.super.constructor");
  }

  @Nonnull
  @Override
  public PsiElementVisitor buildVisitor(@Nonnull ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @Nonnull LocalInspectionToolSession session,
                                        Object state) {
    return new Visitor(holder, session);
  }

  private static class Visitor extends PyInspectionVisitor {

    public Visitor(@Nullable ProblemsHolder holder, @Nonnull LocalInspectionToolSession session) {
      super(holder, session);
    }

    @Override
    public void visitPyClass(@Nonnull PyClass node) {
      final PsiElement[] superClasses = node.getSuperClassExpressions();

      if (superClasses.length == 0 ||
        superClasses.length == 1 && OBJECT.equals(superClasses[0].getText()) ||
        !superHasConstructor(node, myTypeEvalContext)) {
        return;
      }

      final PyFunction initMethod = node.findMethodByName(INIT, false, myTypeEvalContext);

      if (initMethod == null || isExceptionClass(node, myTypeEvalContext) || hasConstructorCall(node, initMethod, myTypeEvalContext)) {
        return;
      }

      if (superClasses.length == 1 || node.isNewStyleClass(myTypeEvalContext)) {
        registerProblem(initMethod.getNameIdentifier(),
                        PyBundle.message("INSP.missing.super.constructor.message"),
                        new AddCallSuperQuickFix());
      }
      else {
        registerProblem(initMethod.getNameIdentifier(), PyBundle.message("INSP.missing.super.constructor.message"));
      }
    }

    private static boolean superHasConstructor(@Nonnull PyClass cls, @Nonnull TypeEvalContext context) {
      final String className = cls.getName();

      for (PyClass baseClass : cls.getAncestorClasses(context)) {
        if (!PyUtil.isObjectClass(baseClass) &&
          !Comparing.equal(className, baseClass.getName()) &&
          baseClass.findMethodByName(INIT, false, context) != null) {
          return true;
        }
      }

      return false;
    }

    private static boolean isExceptionClass(@Nonnull PyClass cls, @Nonnull TypeEvalContext context) {
      if (PyBroadExceptionInspection.equalsException(cls, context)) {
        return true;
      }

      return cls.getAncestorClasses(context)
                .stream()
                .filter(baseClass -> PyBroadExceptionInspection.equalsException(baseClass, context))
                .findAny()
                .isPresent();
    }

    private static boolean hasConstructorCall(@Nonnull PyClass cls, @Nonnull PyFunction initMethod, @Nonnull TypeEvalContext context) {
      final CallVisitor visitor = new CallVisitor(cls, context);
      initMethod.getStatementList().accept(visitor);
      return visitor.myHasConstructorCall;
    }

    private static class CallVisitor extends PyRecursiveElementVisitor {

      @Nonnull
      private final PyClass myClass;

      @Nonnull
      private final TypeEvalContext myContext;

      private boolean myHasConstructorCall = false;

      public CallVisitor(@Nonnull PyClass cls, @Nonnull TypeEvalContext context) {
        myClass = cls;
        myContext = context;
      }

      @Override
      public void visitPyCallExpression(@Nonnull PyCallExpression node) {
        if (isConstructorCall(node, myClass, myContext)) {
          myHasConstructorCall = true;
        }
      }

      private static boolean isConstructorCall(@Nonnull PyCallExpression call, @Nonnull PyClass cls, @Nonnull TypeEvalContext context) {
        final PyExpression callee = call.getCallee();

        if (callee == null || !INIT.equals(callee.getName())) {
          return false;
        }

        final PyExpression calleeQualifier =
          Optional.of(callee).filter(PyQualifiedExpression.class::isInstance).map(PyQualifiedExpression.class::cast).map
            (PyQualifiedExpression::getQualifier).orElse(null);

        return calleeQualifier != null && (isSuperCall(calleeQualifier, cls, context) || isSuperClassCall(calleeQualifier, cls, context));
      }

      private static boolean isSuperCall(@Nonnull PyExpression calleeQualifier, @Nonnull PyClass cls, @Nonnull TypeEvalContext context) {
        final String prevCalleeName = Optional.of(calleeQualifier)
                                              .filter(PyCallExpression.class::isInstance)
                                              .map(PyCallExpression.class::cast)
                                              .map(PyCallExpression::getCallee)
                                              .map
                                                (PyExpression::getName)
                                              .orElse(null);

        if (!SUPER.equals(prevCalleeName)) {
          return false;
        }

        final PyExpression[] args = ((PyCallExpression)calleeQualifier).getArguments();

        if (args.length == 0) {
          return true;
        }

        final String firstArg = args[0].getText();
        final String classQName = cls.getQualifiedName();

        if (firstArg.equals(cls.getName()) ||
          firstArg.equals(CANONICAL_SELF + "." + __CLASS__) ||
          classQName != null && classQName.endsWith(firstArg) ||
          firstArg.equals(__CLASS__) && LanguageLevel.forElement(cls).isAtLeast(LanguageLevel.PYTHON30)) {
          return true;
        }

        return cls.getAncestorClasses(context).stream().map(PyClass::getName).filter(firstArg::equals).findAny().isPresent();
      }

      private static boolean isSuperClassCall(@Nonnull PyExpression calleeQualifier,
                                              @Nonnull PyClass cls,
                                              @Nonnull TypeEvalContext context) {
        final PsiElement callingClass = resolveCallingClass(calleeQualifier);

        return callingClass != null && cls.getAncestorClasses(context).stream().filter(callingClass::equals).findAny().isPresent();
      }

      @Nullable
      private static PsiElement resolveCallingClass(@Nonnull PyExpression calleeQualifier) {
        if (calleeQualifier instanceof PyCallExpression) {
          return Optional.of((PyCallExpression)calleeQualifier)
                         .map(PyCallExpression::getCallee)
                         .map(PyExpression::getReference)
                         .map(PsiReference::resolve)
                         .orElse(null);
        }
        else {
          return Optional.ofNullable(calleeQualifier.getReference()).map(PsiReference::resolve).orElse(null);
        }
      }
    }
  }
}
