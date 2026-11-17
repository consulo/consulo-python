// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonFQDNNames;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.nameResolver.NameResolverTools;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyBoolLiteralExpression;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyDictLiteralExpression;
import com.jetbrains.python.psi.PyElementType;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFormattedStringElement;
import com.jetbrains.python.psi.PyKeyValueExpression;
import com.jetbrains.python.psi.PyKeywordArgument;
import com.jetbrains.python.psi.PyNumericLiteralExpression;
import com.jetbrains.python.psi.PyParenthesizedExpression;
import com.jetbrains.python.psi.PyPrefixExpression;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PySequenceExpression;
import com.jetbrains.python.psi.PyStringElement;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.PyTupleExpression;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.language.psi.PsiElement;
import consulo.language.psi.ResolveResult;
import consulo.language.psi.util.QualifiedName;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.ContainerUtil;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * TODO: Merge PythonDataflowUtil and {@link PyEvaluator} and all its inheritors and improve Abstract Interpretation
 */
public class PyEvaluator {
    private static final QualifiedName SYS_VERSION_INFO = QualifiedName.fromDottedString("sys.version_info");

    private static final String TYPE_CHECKING = "TYPE_CHECKING";

    /**
     * The expressions on the current evaluation path, each with its depth on the path. A reference to one of them is a cycle,
     * and the evaluator cuts it.
     */
    private final Map<PyExpression, Integer> myEvaluating = new HashMap<>();

    /**
     * The lowest path depth of a cut in the expression that the evaluator evaluates now.
     */
    private int myLowestCutDepth = Integer.MAX_VALUE;

    /**
     * The results of the evaluated expressions. A result that depends on a cut above its expression is not kept,
     * because the value of the cut expression is not known yet.
     */
    private final Map<PyExpression, @Nullable Object> myResults = new HashMap<>();

    private @Nullable Map<String, ?> myNamespace;

    private int @Nullable [] myPythonVersion;

    /**
     * if true, collection items or dict values will be evaluated
     */
    private boolean myEvaluateCollectionItems = true;

    /**
     * if true, dict keys will be evaluated
     */
    private boolean myEvaluateKeys = true;

    /**
     * if true, references will be resolved
     */
    private boolean myEnableResolve = true;

    /**
     * Store expressions as values if they can't be evaludated
     */
    private boolean myAllowExpressionsAsValues = false;

    public void setNamespace(@Nullable Map<String, ?> namespace) {
        myNamespace = namespace;
        myResults.clear();
    }

    public void setLanguageLevel(LanguageLevel languageLevel) {
        myPythonVersion = new int[]{languageLevel.getMajorVersion(), languageLevel.getMinorVersion()};
        myResults.clear();
    }

    public void setEvaluateCollectionItems(boolean evaluateCollectionItems) {
        myEvaluateCollectionItems = evaluateCollectionItems;
        myResults.clear();
    }

    public void setAllowExpressionsAsValues(boolean allowExpressionsAsValues) {
        myAllowExpressionsAsValues = allowExpressionsAsValues;
        myResults.clear();
    }

    public void setEvaluateKeys(boolean evaluateKeys) {
        myEvaluateKeys = evaluateKeys;
        myResults.clear();
    }

    public void enableResolve(boolean enableResolve) {
        myEnableResolve = enableResolve;
        myResults.clear();
    }

    public @Nullable Object evaluate(@Nullable PyExpression expression) {
        if (expression == null) {
            return null;
        }
        if (myResults.containsKey(expression)) {
            return myResults.get(expression);
        }
        Integer cutDepth = myEvaluating.get(expression);
        if (cutDepth != null) {
            myLowestCutDepth = Math.min(myLowestCutDepth, cutDepth);
            return null;
        }
        int depth = myEvaluating.size();
        int outerLowestCutDepth = myLowestCutDepth;
        myEvaluating.put(expression, depth);
        myLowestCutDepth = Integer.MAX_VALUE;
        try {
            PyUtil.verboseOnly(() -> PyPsiUtils.assertValid(expression));
            Object result = evaluateOnce(expression);
            if (myLowestCutDepth >= depth) {
                myResults.put(expression, result);
            }
            return result;
        }
        finally {
            myEvaluating.remove(expression);
            myLowestCutDepth = Math.min(outerLowestCutDepth, myLowestCutDepth < depth ? myLowestCutDepth : Integer.MAX_VALUE);
        }
    }

    private @Nullable Object evaluateOnce(PyExpression expression) {
        if (expression instanceof PyParenthesizedExpression parenthesizedExpression) {
            return evaluate(parenthesizedExpression.getContainedExpression());
        }
        if (expression instanceof PySequenceExpression sequenceExpression) {
            return evaluateSequence(sequenceExpression);
        }
        Boolean booleanLiteralValue = getBooleanLiteralValue(expression);
        if (booleanLiteralValue != null) {
            return booleanLiteralValue;
        }
        if (expression instanceof PyCallExpression callExpression) {
            return evaluateCall(callExpression);
        }
        if (expression instanceof PyReferenceExpression referenceExpression) {
            return evaluateReference(referenceExpression);
        }
        if (expression instanceof PyStringLiteralExpression stringLiteralExpression) {
            return evaluateString(stringLiteralExpression);
        }
        if (expression instanceof PyBinaryExpression binaryExpression) {
            return evaluateBinary(binaryExpression);
        }
        if (expression instanceof PyNumericLiteralExpression numericLiteralExpression) {
            return evaluateNumeric(numericLiteralExpression);
        }
        if (expression instanceof PyPrefixExpression prefixExpression) {
            return evaluatePrefix(prefixExpression);
        }
        return null;
    }

    protected @Nullable Object evaluateBinary(PyBinaryExpression expression) {
        PyElementType op = expression.getOperator();

        int[] pythonVersion = myPythonVersion;
        if (op != null && pythonVersion != null && PyTokenTypes.RELATIONAL_OPERATIONS.contains(op)) {
            if (isSysVersionInfoExpression(expression.getLeftExpression())) {
                int[] version = evaluateAsVersion(expression.getRightExpression());
                if (version != null) {
                    return evaluateVersionCheck(op, pythonVersion, version);
                }
            }
            else if (isSysVersionInfoExpression(expression.getRightExpression())) {
                int[] version = evaluateAsVersion(expression.getLeftExpression());
                if (version != null) {
                    return evaluateVersionCheck(op, version, pythonVersion);
                }
            }
        }

        Object lhs = evaluate(expression.getLeftExpression());
        Object rhs = evaluate(expression.getRightExpression());
        if (op == PyTokenTypes.PLUS && lhs != null && rhs != null) {
            return applyPlus(lhs, rhs);
        }
        if (op == null) {
            return null;
        }
        return fromConstant(PyConstantFolder.binary(op, toConstant(lhs), toConstant(rhs)));
    }

    private @Nullable Object evaluatePrefix(PyPrefixExpression expression) {
        PyElementType operator = expression.getOperator();
        if (!PyConstantFolder.isPrefixOperator(operator)) {
            return null;
        }
        return fromConstant(PyConstantFolder.prefix(operator, toConstant(evaluate(expression.getOperand()))));
    }

    /**
     * Evaluates sequence (tuple, list, set, dict)
     *
     * @param expression sequence expression
     * @return evaluated sequence
     */
    protected Object evaluateSequence(PySequenceExpression expression) {
        if (expression instanceof PyDictLiteralExpression dictLiteralExpression) {
            Map<Object, Object> result = new HashMap<>();
            for (PyKeyValueExpression keyValue : dictLiteralExpression.getElements()) {
                addRecordFromDict(result, keyValue.getKey(), keyValue.getValue());
            }
            return result;
        }

        PyExpression[] elements = expression.getElements();
        List<Object> result = new ArrayList<>(elements.length);
        for (PyExpression element : elements) {
            result.add(myEvaluateCollectionItems ? evaluate(element) : element);
        }
        return result;
    }

    public @Nullable Object applyPlus(@Nullable Object lhs, @Nullable Object rhs) {
        if (lhs instanceof List<?> lhsList && rhs instanceof List<?> rhsList) {
            return ContainerUtil.concat(lhsList, rhsList);
        }
        return fromConstant(PyConstantFolder.binary(PyTokenTypes.PLUS, toConstant(lhs), toConstant(rhs)));
    }

    protected @Nullable Object evaluateReference(PyReferenceExpression expression) {
        if (isTypeCheckingExpression(expression)) {
            return true;
        }
        if (!expression.isQualified()) {
            Map<String, ?> namespace = myNamespace;
            if (namespace != null) {
                String referencedName = expression.getReferencedName();
                return referencedName != null ? namespace.get(referencedName) : null;
            }
            if (!myEnableResolve) {
                return null;
            }
            TypeEvalContext context = TypeEvalContext.codeInsightFallback(expression.getProject());
            ResolveResult[] results = expression.getReference(PyResolveContext.defaultContext(context)).multiResolve(false);
            if (results.length != 1) {
                return null;
            }
            PsiElement result = results[0].getElement();
            if (result instanceof PyTargetExpression targetExpression) {
                result = targetExpression.findAssignedValue();
            }
            if (result instanceof PyExpression resultExpression) {
                return evaluate(resultExpression);
            }
        }
        return null;
    }

    protected @Nullable Object evaluateCall(PyCallExpression expression) {
        PyExpression[] args = expression.getArguments();
        if (expression.isCalleeText(PyNames.REPLACE) && args.length == 2) {
            PyExpression callee = expression.getCallee();
            if (callee instanceof PyQualifiedExpression qualifiedExpression) {
                Object evaluatedQualifier = evaluate(qualifiedExpression.getQualifier());
                if (evaluatedQualifier instanceof String evaluatedQualifierString) {
                    Object oldSubstring = evaluate(args[0]);
                    Object newSubstring = evaluate(args[1]);
                    if (oldSubstring instanceof String oldString && newSubstring instanceof String newString) {
                        return evaluatedQualifierString.replace(oldString, newString);
                    }
                }
            }
        }

        if (myEnableResolve && NameResolverTools.isCalleeShortCut(expression, PythonFQDNNames.DICT_CLASS)) {
            return evaluateDictCall(expression);
        }
        return null;
    }

    /**
     * Evaluates {@code dict([("k", "v")], foo="spam")}: the pairs of a sequence literal and the keyword arguments.
     * The result skips the other arguments, so it can hold only a part of the keys. Template completion uses such a part.
     */
    private @Nullable Map<Object, Object> evaluateDictCall(PyCallExpression expression) {
        PyExpression[] arguments = expression.getArguments();
        if (arguments.length == 0) {
            return null;
        }
        Map<Object, Object> result = new HashMap<>();
        for (PyExpression argument : arguments) {
            if (argument instanceof PyKeywordArgument keywordArgument) {
                String keyword = keywordArgument.getKeyword();
                if (keyword != null) {
                    addRecordFromDict(result, keyword, keywordArgument.getValueExpression());
                }
            }
            else if (!addRecordsFromPairs(result, argument)) {
                return null;
            }
        }
        return result;
    }

    /**
     * Returns {@code false} for a sequence literal with an element that is not a pair. Another argument adds nothing.
     */
    private boolean addRecordsFromPairs(Map<Object, Object> result, PyExpression argument) {
        PyExpression pairs = PyPsiUtils.flattenParens(argument);
        if (!(pairs instanceof PySequenceExpression sequenceExpression) || pairs instanceof PyDictLiteralExpression) {
            return true;
        }
        for (PyExpression pair : sequenceExpression.getElements()) {
            PyExpression tuple = PyPsiUtils.flattenParens(pair);
            if (!(tuple instanceof PyTupleExpression tupleExpression) || tupleExpression.getElements().length != 2) {
                return false;
            }
            addRecordFromDict(result, tupleExpression.getElements()[0], tupleExpression.getElements()[1]);
        }
        return true;
    }

    private void addRecordFromDict(Map<Object, Object> result, PyExpression key, @Nullable PyExpression value) {
        result.put(myEvaluateKeys ? evaluate(key) : key, myEvaluateCollectionItems ? evaluateOrGet(value) : value);
    }

    private void addRecordFromDict(Map<Object, Object> result, String key, @Nullable PyExpression value) {
        result.put(key, myEvaluateCollectionItems ? evaluateOrGet(value) : value);
    }

    private @Nullable Object evaluateOrGet(@Nullable PyExpression expression) {
        Object result = evaluate(expression);
        if (result != null) {
            return result;
        }
        return myAllowExpressionsAsValues ? expression : null;
    }

    /**
     * Returns {@code null} for a bytes literal, a t-string and an f-string with an interpolated fragment. Their values are not a {@code str}
     * that the literal text gives.
     */
    private static @Nullable String evaluateString(PyStringLiteralExpression expression) {
        for (PyStringElement element : expression.getStringElements()) {
            if (element.isBytes() || element.isTemplate()) {
                return null;
            }
            if (element instanceof PyFormattedStringElement formattedStringElement && !formattedStringElement.getFragments().isEmpty()) {
                return null;
            }
        }
        return expression.getStringValue();
    }

    private static @Nullable Object evaluateNumeric(PyNumericLiteralExpression expression) {
        if (expression.isIntegerLiteral()) {
            return fromBigInteger(expression.getBigIntegerValue());
        }
        return null;
    }

    public static int @Nullable [] evaluateAsVersion(@Nullable PyExpression expression) {
        if (!(PyPsiUtils.flattenParens(expression) instanceof PyTupleExpression tupleExpression)) {
            return null;
        }
        PyExpression[] elements = tupleExpression.getElements();
        int[] result = new int[elements.length];
        for (int i = 0; i < elements.length; i++) {
            Integer element = evaluateNoResolve(elements[i], Integer.class);
            if (element == null) {
                return null;
            }
            result[i] = element;
        }
        return result;
    }

    private static boolean evaluateVersionCheck(PyElementType op, int[] lhs, int[] rhs) {
        if (op == PyTokenTypes.LT) {
            return ArrayUtil.lexicographicCompare(lhs, rhs) < 0;
        }
        if (op == PyTokenTypes.LE) {
            return ArrayUtil.lexicographicCompare(lhs, rhs) <= 0;
        }
        if (op == PyTokenTypes.GT) {
            return ArrayUtil.lexicographicCompare(lhs, rhs) > 0;
        }
        if (op == PyTokenTypes.GE) {
            return ArrayUtil.lexicographicCompare(lhs, rhs) >= 0;
        }
        throw new IllegalArgumentException();
    }

    public static @Nullable Boolean getBooleanLiteralValue(PsiElement expression) {
        if (expression instanceof PyBoolLiteralExpression boolLiteralExpression) {
            if (PyNames.DEBUG.equals(boolLiteralExpression.getText())) {
                return null;
            }
            return boolLiteralExpression.getValue();
        }
        else if (expression instanceof PyReferenceExpression referenceExpression && LanguageLevel.forElement(expression).isPython2()) {
            String referencedName = referenceExpression.getReferencedName();
            if (PyNames.TRUE.equals(referencedName)) {
                return true;
            }
            if (PyNames.FALSE.equals(referencedName)) {
                return false;
            }
        }

        return null;
    }

    /**
     * Converts a value of the evaluator to the form of {@link PyConstantFolder}. The evaluator keeps an {@code int} as an {@link Integer},
     * a {@link Long} or a {@link BigInteger}, whichever is the smallest that holds it.
     */
    private static @Nullable PyConstantValue toConstant(@Nullable Object value) {
        if (value instanceof String stringValue) {
            return new PyConstantValue.Str(stringValue);
        }
        if (value instanceof Boolean booleanValue) {
            return new PyConstantValue.Bool(booleanValue);
        }
        if (value instanceof Integer intValue) {
            return new PyConstantValue.Int(BigInteger.valueOf(intValue.longValue()));
        }
        if (value instanceof Long longValue) {
            return new PyConstantValue.Int(BigInteger.valueOf(longValue));
        }
        if (value instanceof BigInteger bigIntegerValue) {
            return new PyConstantValue.Int(bigIntegerValue);
        }
        return null;
    }

    private static @Nullable Object fromConstant(@Nullable PyConstantValue value) {
        if (value == null) {
            return null;
        }
        return switch (value) {
            case PyConstantValue.Str str -> str.value();
            case PyConstantValue.Bool bool -> bool.value();
            case PyConstantValue.Int intValue -> fromBigInteger(intValue.value());
        };
    }

    private static Number fromBigInteger(BigInteger value) {
        int intValue = value.intValue();
        if (BigInteger.valueOf(intValue).equals(value)) {
            return intValue;
        }

        long longValue = value.longValue();
        if (BigInteger.valueOf(longValue).equals(value)) {
            return longValue;
        }

        return value;
    }

    /**
     * Shortcut that evaluates expression with default params and casts it to particular type (if possible)
     *
     * @param <T>        expected type
     * @param expression expression to evaluate
     * @param resultType expected type
     * @return value if expression is evaluated to this type, null otherwise
     */
    public static <T> @Nullable T evaluate(@Nullable PyExpression expression, Class<T> resultType) {
        return PyUtil.as(new PyEvaluator().evaluate(expression), resultType);
    }

    /**
     * Shortcut that evaluates expression with default params and disabled resolve and casts it to particular type (if possible)
     *
     * @param <T>        expected type
     * @param expression expression to evaluate
     * @param resultType expected type
     * @return value if expression is evaluated to this type, null otherwise
     */
    public static <T> @Nullable T evaluateNoResolve(@Nullable PyExpression expression, Class<T> resultType) {
        PyEvaluator evaluator = new PyEvaluator();
        evaluator.enableResolve(false);
        return PyUtil.as(evaluator.evaluate(expression), resultType);
    }

    /**
     * Shortcut that evaluates expression and tries to determine if {@code bool} will return true for it
     *
     * @param expression expression to evaluate
     * @return true if expression is evaluated to value so {@code bool} returns true for it
     */
    public static @Nullable Boolean evaluateAsBoolean(@Nullable PyExpression expression) {
        return evaluateAsBoolean(prepareEvaluatorForBoolean(true), expression);
    }

    /**
     * Shortcut that evaluates expression and tries to determine if {@code bool} will return true for it
     *
     * @param expression expression to evaluate
     * @return true if expression is evaluated to value so {@code bool} returns true for it
     */
    public static @Nullable Boolean evaluateAsBooleanNoResolve(@Nullable PyExpression expression) {
        return evaluateAsBoolean(prepareEvaluatorForBoolean(false), expression);
    }

    public static @Nullable Boolean evaluateAsBooleanNoResolve(@Nullable PyExpression expression, @Nullable LanguageLevel languageLevel) {
        PyEvaluator evaluator = prepareEvaluatorForBoolean(false);
        if (languageLevel != null) {
            evaluator.setLanguageLevel(languageLevel);
        }
        return evaluateAsBoolean(evaluator, expression);
    }

    public static boolean evaluateAsBoolean(@Nullable PyExpression expression, boolean defaultValue) {
        Boolean result = evaluateAsBoolean(expression);
        return result != null ? result : defaultValue;
    }

    public static boolean evaluateAsBooleanNoResolve(@Nullable PyExpression expression, boolean defaultValue) {
        Boolean result = evaluateAsBooleanNoResolve(expression);
        return result != null ? result : defaultValue;
    }

    private static PyEvaluator prepareEvaluatorForBoolean(boolean enableResolve) {
        PyEvaluator evaluator = new PyEvaluator();
        evaluator.setEvaluateCollectionItems(false);
        evaluator.setEvaluateKeys(false);
        evaluator.enableResolve(enableResolve);
        return evaluator;
    }

    private static @Nullable Boolean evaluateAsBoolean(PyEvaluator evaluator, @Nullable PyExpression expression) {
        Object result = evaluator.evaluate(expression);
        if (result instanceof Collection<?> collection) {
            return !collection.isEmpty();
        }
        if (result instanceof Map<?, ?> map) {
            return !map.isEmpty();
        }
        PyConstantValue constant = toConstant(result);
        return constant != null ? PyConstantFolder.isTruthy(constant) : null;
    }

    public static boolean isSysVersionInfoExpression(@Nullable PyExpression expression) {
        if (!(PyPsiUtils.flattenParens(expression) instanceof PyReferenceExpression referenceExpression)) {
            return false;
        }
        return SYS_VERSION_INFO.equals(referenceExpression.asQualifiedName());
    }

    public static boolean isTypeCheckingExpression(@Nullable PyExpression expression) {
        return expression instanceof PyReferenceExpression referenceExpression && isTypeCheckingReference(referenceExpression);
    }

    private static boolean isTypeCheckingReference(PyReferenceExpression expression) {
        QualifiedName qualifiedName = expression.asQualifiedName();
        // The control flow, the scopes and the lists of file and class members use this check, and name resolution uses them.
        // Thus, the check cannot resolve `t` in `t.TYPE_CHECKING` to `typing`, and it accepts any qualifier. mypy also does this.
        return qualifiedName != null && qualifiedName.getComponentCount() <= 2 && TYPE_CHECKING.equals(qualifiedName.getLastComponent());
    }
}
