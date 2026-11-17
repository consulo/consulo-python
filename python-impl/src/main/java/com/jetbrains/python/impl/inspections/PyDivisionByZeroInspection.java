// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.types.PyLiteralType;
import com.jetbrains.python.impl.psi.types.PyTypeUtil;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyAugAssignmentStatement;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyCallable;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElementType;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyGlobalStatement;
import com.jetbrains.python.psi.PyKeywordArgument;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyNonlocalStatement;
import com.jetbrains.python.psi.PyNumericLiteralExpression;
import com.jetbrains.python.psi.PyPrefixExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyStarArgument;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.types.PyInstantiableType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.ResolveResult;
import consulo.language.psi.util.PsiTreeUtil;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ExtensionImpl
public final class PyDivisionByZeroInspection extends PyInspection {
    private static final String FRACTION_QN = "fractions.Fraction";

    private static final String DECIMAL_QN = "decimal.Decimal";

    private static final String FRACTION_DENOMINATOR = "denominator";

    // The following functions are known to raise an error on a zero divisor:

    private static final Set<String> KNOWN_TRUEDIV_FUNCTIONS = Set.of(
        PyNames.FQN.INT + ".__truediv__",
        PyNames.FQN.FLOAT + ".__truediv__",
        PyNames.FQN.COMPLEX + ".__truediv__",
        FRACTION_QN + ".__truediv__",
        DECIMAL_QN + ".__truediv__"
    );

    private static final Set<String> KNOWN_FLOORDIV_FUNCTIONS = Set.of(
        PyNames.FQN.INT + ".__floordiv__",
        PyNames.FQN.FLOAT + ".__floordiv__",
        FRACTION_QN + ".__floordiv__",
        DECIMAL_QN + ".__floordiv__"
    );

    private static final Set<String> KNOWN_MOD_FUNCTIONS = Set.of(
        PyNames.FQN.INT + ".__mod__",
        PyNames.FQN.FLOAT + ".__mod__",
        FRACTION_QN + ".__mod__",
        DECIMAL_QN + ".__mod__"
    );

    private static final Set<String> KNOWN_DIVMOD_FUNCTIONS = Set.of(
        PyNames.FQN.INT + ".__divmod__",
        PyNames.FQN.FLOAT + ".__divmod__",
        FRACTION_QN + ".__divmod__",
        DECIMAL_QN + ".__divmod__"
    );

    /**
     * One flavor of division ({@code /}, {@code //}, {@code %}, {@code divmod}, or their in-place variants).
     * <p>
     * Python operators and calls get mapped to {@code DivisionOperation}. We try to resolve the special
     * {@link #methods} that Python's runtime calls when performing this division operation.
     * If resolution lands on one of the implementations known to raise an error on a zero divisor
     * ({@link #knownFunctions}), we report a warning.
     */
    private enum DivisionOperation {
        TRUEDIV(List.of("__truediv__"), KNOWN_TRUEDIV_FUNCTIONS),
        FLOORDIV(List.of("__floordiv__"), KNOWN_FLOORDIV_FUNCTIONS),
        MOD(List.of("__mod__"), KNOWN_MOD_FUNCTIONS),
        DIVMOD(List.of("__divmod__"), KNOWN_DIVMOD_FUNCTIONS),
        ITRUEDIV(plus("__itruediv__", TRUEDIV.methods), KNOWN_TRUEDIV_FUNCTIONS),
        IFLOORDIV(plus("__ifloordiv__", FLOORDIV.methods), KNOWN_FLOORDIV_FUNCTIONS),
        IMOD(plus("__imod__", MOD.methods), KNOWN_MOD_FUNCTIONS);

        static final Map<PyElementType, DivisionOperation> OPERATOR_TO_OPERATION = Map.of(
            PyTokenTypes.DIV, TRUEDIV,
            PyTokenTypes.DIVEQ, ITRUEDIV,
            PyTokenTypes.FLOORDIV, FLOORDIV,
            PyTokenTypes.FLOORDIVEQ, IFLOORDIV,
            PyTokenTypes.PERC, MOD,
            PyTokenTypes.PERCEQ, IMOD
        );

        static final Map<String, DivisionOperation> CALLEE_TO_OPERATION = Map.ofEntries(
            Map.entry("builtins." + PyNames.DIVMOD, DIVMOD),
            // typeshed defines the `operator` module functions in `_operator`, re-exported by `operator`
            Map.entry("operator.truediv", TRUEDIV), Map.entry("_operator.truediv", TRUEDIV),
            Map.entry("operator.itruediv", ITRUEDIV), Map.entry("_operator.itruediv", ITRUEDIV),
            Map.entry("operator.floordiv", FLOORDIV), Map.entry("_operator.floordiv", FLOORDIV),
            Map.entry("operator.ifloordiv", IFLOORDIV), Map.entry("_operator.ifloordiv", IFLOORDIV),
            Map.entry("operator.mod", MOD), Map.entry("_operator.mod", MOD),
            Map.entry("operator.imod", IMOD), Map.entry("_operator.imod", IMOD)
        );

        /**
         * names of methods Python calls for this operation, in resolution order
         */
        final List<String> methods;
        /**
         * qualified names of {@link #methods} implementations that raise an error on a zero divisor
         */
        final Set<String> knownFunctions;

        DivisionOperation(List<String> methods, Set<String> knownFunctions) {
            this.methods = methods;
            this.knownFunctions = knownFunctions;
        }

        private static List<String> plus(String first, List<String> rest) {
            List<String> result = new ArrayList<>();
            result.add(first);
            result.addAll(rest);
            return List.copyOf(result);
        }
    }

    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameDivisionByZero();
    }

    @Override
    public LocalizeValue getGroupDisplayName() {
        return PyLocalize.inspGroupPythonGeneral();
    }

    @Override
    public PsiElementVisitor buildVisitor(ProblemsHolder holder,
                                          boolean isOnTheFly,
                                          LocalInspectionToolSession session,
                                          Object state) {
        return new Visitor(holder, PyInspectionVisitor.getContext(session));
    }

    private static final class Visitor extends PyInspectionVisitor {
        Visitor(ProblemsHolder holder, TypeEvalContext context) {
            super(holder, context);
        }

        @Override
        public void visitPyBinaryExpression(PyBinaryExpression node) {
            DivisionOperation operation = DivisionOperation.OPERATOR_TO_OPERATION.get(node.getOperator());
            if (operation == null) {
                return;
            }
            PyExpression dividend = node.getLeftExpression();
            if (dividend == null) {
                return;
            }
            if (isZero(node.getRightExpression()) && raisesOnZeroDivisor(operation, dividend)) {
                registerProblem(node, PyLocalize.inspDivisionByZero().get());
            }
        }

        @Override
        public void visitPyAugAssignmentStatement(PyAugAssignmentStatement node) {
            PsiElement operationElement = node.getOperation();
            if (operationElement == null || !(operationElement.getNode().getElementType() instanceof PyElementType operator)) {
                return;
            }
            DivisionOperation operation = DivisionOperation.OPERATOR_TO_OPERATION.get(operator);
            if (operation == null) {
                return;
            }
            if (isZero(node.getValue()) && raisesOnZeroDivisor(operation, node.getTarget())) {
                registerProblem(node, PyLocalize.inspDivisionByZero().get());
            }
        }

        @Override
        public void visitPyCallExpression(PyCallExpression node) {
            PyExpression[] arguments = node.getArguments();
            if (arguments.length != 2 || Arrays.stream(arguments).anyMatch(it -> it instanceof PyStarArgument)) {
                return;
            }
            PyExpression first = arguments[0];
            PyExpression second = arguments[1];
            // `divmod` and the `operator` functions take positional-only arguments, `Fraction` also takes keyword arguments
            boolean isPositional = !(first instanceof PyKeywordArgument) && !(second instanceof PyKeywordArgument);
            PyExpression divisor = isPositional ? second : node.getKeywordArgument(FRACTION_DENOMINATOR);
            // Check the divisor before the callee resolution, because the resolution is more expensive
            if (!isZero(divisor)) {
                return;
            }

            List<String> callees = new ArrayList<>();
            for (PyCallable callable : node.multiResolveCalleeFunction(getResolveContext())) {
                if (callable instanceof PyFunction function) {
                    PyClass constructedClass = PyUtil.turnConstructorIntoClass(function);
                    String qualifiedName = constructedClass != null ? constructedClass.getQualifiedName() : null;
                    if (qualifiedName == null) {
                        qualifiedName = function.getQualifiedName();
                    }
                    if (qualifiedName != null) {
                        callees.add(qualifiedName);
                    }
                }
            }

            boolean isReported;
            if (callees.contains(FRACTION_QN)) {
                isReported = true;
            }
            else if (!isPositional) {
                isReported = false;
            }
            else {
                DivisionOperation operation = null;
                for (String callee : callees) {
                    operation = DivisionOperation.CALLEE_TO_OPERATION.get(callee);
                    if (operation != null) {
                        break;
                    }
                }
                isReported = operation != null && raisesOnZeroDivisor(operation, first);
            }
            if (isReported) {
                registerProblem(node, PyLocalize.inspDivisionByZero().get());
            }
        }

        private boolean isZero(@Nullable PyExpression expression) {
            PyExpression unwrapped = PyPsiUtils.flattenParens(expression);
            if (unwrapped == null) {
                return false;
            }
            if (unwrapped instanceof PyPrefixExpression prefix
                && (prefix.getOperator() == PyTokenTypes.PLUS || prefix.getOperator() == PyTokenTypes.MINUS)) {
                return isZero(prefix.getOperand());
            }
            if (unwrapped instanceof PyNumericLiteralExpression numeric) {
                return numeric.getBigDecimalValue().signum() == 0;
            }
            if (!(myTypeEvalContext.getType(unwrapped) instanceof PyLiteralType literalType)) {
                return false;
            }
            BigInteger intValue = literalType.getIntValue();
            if ((intValue == null || intValue.signum() != 0) && !Boolean.FALSE.equals(literalType.getBoolValue())) {
                return false;
            }
            return !(unwrapped instanceof PyReferenceExpression reference) || isNotReboundInOtherScope(reference);
        }

        /**
         * Checks that {@code reference} resolves only to variables of its own scope, and that no nested scope
         * rebinds them with {@code global} or {@code nonlocal}. Otherwise, the inferred literal type is not reliable.
         */
        private boolean isNotReboundInOtherScope(PyReferenceExpression reference) {
            if (reference.isQualified()) {
                return false;
            }
            String name = reference.getReferencedName();
            if (name == null) {
                return false;
            }
            ScopeOwner owner = ScopeUtil.getScopeOwner(reference);
            if (owner == null) {
                return false;
            }
            ResolveResult[] resolved = reference.getReference(getResolveContext()).multiResolve(false);
            if (resolved.length == 0) {
                return false;
            }
            for (ResolveResult result : resolved) {
                PsiElement element = result.getElement();
                if ((!(element instanceof PyTargetExpression) && !(element instanceof PyNamedParameter))
                    || ScopeUtil.getScopeOwner(element) != owner) {
                    return false;
                }
            }
            Collection<PsiElement> statements =
                PsiTreeUtil.findChildrenOfAnyType(owner, PyGlobalStatement.class, PyNonlocalStatement.class);
            for (PsiElement statement : statements) {
                PyTargetExpression[] rebindings = statement instanceof PyGlobalStatement globalStatement
                    ? globalStatement.getGlobals()
                    : ((PyNonlocalStatement) statement).getVariables();
                for (PyTargetExpression rebinding : rebindings) {
                    if (name.equals(rebinding.getName())) {
                        return false;
                    }
                }
            }
            return true;
        }

        /**
         * Checks that every union member of the {@code dividend} type resolves {@link DivisionOperation#methods}
         * to one of {@link DivisionOperation#knownFunctions}.
         */
        private boolean raisesOnZeroDivisor(DivisionOperation operation, PyExpression dividend) {
            PyType dividendType = myTypeEvalContext.getType(dividend);
            if (dividendType == null) {
                return false;
            }
            for (PyType member : PyTypeUtil.asUnionSequence(dividendType)) {
                if (member == null || !resolvesToKnownFunction(operation, member, dividend)) {
                    return false;
                }
            }
            return true;
        }

        private boolean resolvesToKnownFunction(DivisionOperation operation, PyType type, PyExpression dividend) {
            // A class object does not support the division operators of its instances
            if (type instanceof PyInstantiableType<?> instantiableType && instantiableType.isDefinition()) {
                return false;
            }
            List<? extends RatedResolveResult> resolved = List.of();
            for (String method : operation.methods) {
                List<? extends RatedResolveResult> members = type.resolveMember(method, dividend, AccessDirection.READ, getResolveContext());
                if (members != null && !members.isEmpty()) {
                    resolved = members;
                    break;
                }
            }
            for (RatedResolveResult result : resolved) {
                if (result.getElement() instanceof PyFunction function) {
                    String qualifiedName = function.getQualifiedName();
                    if (qualifiedName != null && operation.knownFunctions.contains(qualifiedName)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }
}
