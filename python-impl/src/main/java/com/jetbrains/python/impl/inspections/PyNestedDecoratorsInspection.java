// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.inspections.quickfix.RemoveDecoratorQuickFix;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.PyCallable;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyDecorator;
import com.jetbrains.python.psi.PyDecoratorList;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.psi.PsiElementVisitor;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * Checks nested decorators, especially whatever comes after @classmethod.
 * <br/>
 */
@ExtensionImpl
public final class PyNestedDecoratorsInspection extends PyInspection {
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameNestedDecorators();
    }

    @Override
    public LocalizeValue getGroupDisplayName() {
        return PyLocalize.inspGroupPythonGeneral();
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

    public static class Visitor extends PyInspectionVisitor {
        private static final Set<String> TRANSFORMING_DECORATORS = Set.of(PyNames.CLASSMETHOD, PyNames.STATICMETHOD);
        private static final Set<String> UNAFFECTED_DECORATORS = Set.of(
          "typing.final", "typing.no_type_check", "typing.overload", "typing.override", "typing.type_check_only",
          "typing_extensions.final", "typing_extensions.no_type_check", "typing_extensions.overload", "typing_extensions.override",
          "typing_extensions.type_check_only",
          "functools.singledispatchmethod",
          "pydantic.functional_validators.field_validator", "pydantic.functional_validators.model_validator",
          "pydantic.class_validators.validator",
          "django.views.decorators.cache.cache_page",
          "tenacity.retry"
        );


        public Visitor(@Nullable ProblemsHolder holder, TypeEvalContext context) {
            super(holder, context);
        }

        /**
         * Marks decorators that are applied before a transforming decorator and might be affected by this.
         * A transforming decorator is one that returns other than a function.
         * A decorator might be affected if it is not excluded explicitly here.
         *
         * <pre>
         * \@mydeco # < -- warning here
         * \@classmethod
         * def foo(cls):
         *    ...
         * </pre>
         */
        @Override
        public void visitPyFunction(PyFunction node) {
            PyDecoratorList decolist = node.getDecoratorList();
            if (decolist == null) {
                return;
            }
            PyDecorator[] decos = decolist.getDecorators();
            if (decos.length < 2) {
                return;
            }

            for (int i = decos.length - 1; i >= 1; i -= 1) { // start at the innermost
              PyDecorator decoInner = decos[i];
                String decoInnerName = decoInner.getName();
                boolean isTransforming = decoInnerName != null && TRANSFORMING_DECORATORS.contains(decoInnerName) && decoInner.isBuiltin();
                if (!isTransforming) {
                    continue;
                }
                for (int j = i - 1; j >= 0; j -= 1) {
                    PyDecorator decoOuter = decos[j];
                    boolean maybeAffected = !isUnaffectedDecorator(decoOuter);
                    if (maybeAffected) {
                        registerProblem(
                          decoOuter,
                          PyInspectionMessages.problemMessage(a -> PyLocalize.inspDecoratorReceivesUnexpectedBuiltin(a[0]), decoInnerName),
                          ProblemHighlightType.GENERIC_ERROR_OR_WARNING, new RemoveDecoratorQuickFix()
                        );
                        return;
                    }
                }
            }
        }

        private boolean isUnaffectedDecorator(PyDecorator decorator) {
            List<PyCallable> pyCallables = decorator.multiResolveCalleeFunction(getResolveContext());
            for (PyCallable callable : pyCallables) {
                String decoOuterName = getQualifiedName(callable);
                if (decoOuterName != null && UNAFFECTED_DECORATORS.contains(decoOuterName)) {
                    return true;
                }
            }
            return false;
        }

        private static @Nullable String getQualifiedName(PyCallable callable) {
            String decoOuterName = null;
            if (callable instanceof PyFunction pyFunction) {
                PyClass constrClass = PyUtil.turnConstructorIntoClass(pyFunction);
                if (constrClass != null) {
                    decoOuterName = constrClass.getQualifiedName();
                }
            }
            if (decoOuterName == null) {
                decoOuterName = callable.getQualifiedName();
            }
            return decoOuterName;
        }
    }
}
