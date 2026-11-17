package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.psi.impl.PyCallExpressionHelper;
import com.jetbrains.python.impl.psi.types.PyCallableParameterImpl;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.PyTypeParameter;
import com.jetbrains.python.psi.PyTypeParameterList;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeVarType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.psi.PsiElementVisitor;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reports a PEP 696 type parameter default (e.g. `int` in `def f[T = int]`) that can never be used because a parameter
 * of type `T` has a default value. A call then always solves `T`: an argument solves it, or the parameter default does
 * (see {@link PyCallExpressionHelper#mapArgumentsWithDefaults}).
 * <p>
 * Example:
 * <pre>
 * def f[T = int](a: T = 0) -> T: ...  # `int` is never used: `f()` yields `Literal[0]`, `f(x)` yields the type of `x`
 * </pre>
 */
@ExtensionImpl
public class PyShadowedTypeParameterDefaultInspection extends PyInspection {
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameShadowedTypeParameterDefault();
    }

    @Override
    public LocalizeValue getGroupDisplayName() {
        return PyLocalize.inspGroupPythonTypeHinting();
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
        TypeEvalContext context = PyInspectionVisitor.getContext(session);
        if (context.getUsesExternalTypeEngine()) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        return new Visitor(holder, context);
    }

    private static class Visitor extends PyInspectionVisitor {
        Visitor(ProblemsHolder holder, TypeEvalContext context) {
            super(holder, context);
        }

        @Override
        @RequiredReadAction
        public void visitPyFunction(PyFunction node) {
            super.visitPyFunction(node);

            PyTypeParameterList typeParameterList = node.getTypeParameterList();
            if (typeParameterList == null) {
                return;
            }
            List<PyTypeParameter> defaultedTypeParameters = new ArrayList<>();
            for (PyTypeParameter typeParameter : typeParameterList.getTypeParameters()) {
                if (typeParameter.getDefaultExpression() != null) {
                    defaultedTypeParameters.add(typeParameter);
                }
            }
            if (defaultedTypeParameters.isEmpty()) {
                return;
            }

            // Only a parameter of type `T` itself pins `T`. In `T | str`, an argument of type `str` leaves `T` unsolved.
            Set<String> pinnedTypeParameterNames = new HashSet<>();
            for (PyParameter parameter : node.getParameterList().getParameters()) {
                if (!(parameter instanceof PyNamedParameter namedParameter)) {
                    continue;
                }
                if (namedParameter.isPositionalContainer() || namedParameter.isKeywordContainer()) {
                    continue;
                }
                String defaultValueText = namedParameter.getDefaultValueText();
                if (defaultValueText == null) {
                    continue;
                }
                if (defaultValueText.equals(PyNames.ELLIPSIS) &&
                    PyCallExpressionHelper.isPlaceholderEllipsisDefault(namedParameter, myTypeEvalContext)) {
                    continue;
                }
                PyType parameterType = PyCallableParameterImpl.psi(namedParameter).getArgumentType(myTypeEvalContext);
                if (parameterType instanceof PyTypeVarType typeVarType && typeVarType.getScopeOwner() == node) {
                    pinnedTypeParameterNames.add(typeVarType.getName());
                }
            }
            if (pinnedTypeParameterNames.isEmpty()) {
                return;
            }

            for (PyTypeParameter typeParameter : defaultedTypeParameters) {
                String name = typeParameter.getName();
                if (name == null) {
                    continue;
                }
                if (!pinnedTypeParameterNames.contains(name)) {
                    continue;
                }
                PyExpression defaultExpression = typeParameter.getDefaultExpression();
                if (defaultExpression == null) {
                    continue;
                }
                registerProblem(defaultExpression,
                                PyLocalize.inspShadowedTypeParameterDefault(name).get(),
                                ProblemHighlightType.LIKE_UNUSED_SYMBOL);
            }
        }
    }
}
