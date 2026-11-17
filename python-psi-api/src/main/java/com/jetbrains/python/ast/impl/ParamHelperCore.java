package com.jetbrains.python.ast.impl;

import com.jetbrains.python.ast.PyAstExpression;
import com.jetbrains.python.ast.PyAstNamedParameter;
import com.jetbrains.python.ast.PyAstStringLiteralExpression;
import com.jetbrains.python.psi.PyStringLiteralCoreUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

public final class ParamHelperCore {
    private ParamHelperCore() {
    }

    @RequiredReadAction
    public static String getNameInSignature(PyAstNamedParameter parameter) {
        StringBuilder sb = new StringBuilder();

        if (parameter.isPositionalContainer()) {
            sb.append("*");
        }
        else if (parameter.isKeywordContainer()) {
            sb.append("**");
        }

        String name = parameter.getName();
        sb.append(name != null ? name : "...");

        return sb.toString();
    }

    /**
     * @param defaultValue             string returned by {@link PyCallableParameter#getDefaultValueText()} or {@link PyParameter#getDefaultValueText()}.
     * @param parameterRenderedAsTyped true if parameter is rendered with type annotation.
     * @return equal sign (surrounded with spaces if {@code parameterRenderedAsTyped}) +
     * {@code defaultValue} (with body escaped if it is a string literal)
     */
    public static @Nullable String getDefaultValuePartInSignature(@Nullable String defaultValue, boolean parameterRenderedAsTyped) {
        if (defaultValue == null) {
            return null;
        }

        StringBuilder sb = new StringBuilder();

        // According to PEP 8 equal sign should be surrounded by spaces if annotation is present
        sb.append(parameterRenderedAsTyped ? " = " : "=");

        Pair<String, String> quotes = PyStringLiteralCoreUtil.getQuotes(defaultValue);
        if (quotes != null) {
            String value = defaultValue.substring(quotes.getFirst().length(), defaultValue.length() - quotes.getSecond().length());
            sb.append(quotes.getFirst());
            StringUtil.escapeStringCharacters(value.length(), value, sb);
            sb.append(quotes.getSecond());
        }
        else {
            sb.append(defaultValue);
        }

        return sb.toString();
    }

    /**
     * @param defaultValue expression returned by {@link PyCallableParameter#getDefaultValue()} or {@link PyParameter#getDefaultValue()}.
     * @return {@code defaultValue} value surrounded by quotes if it is a multi-line string literal, {@code defaultValue} text otherwise.
     */
    @RequiredReadAction
    public static @Nullable String getDefaultValueText(@Nullable PyAstExpression defaultValue) {
        if (defaultValue instanceof PyAstStringLiteralExpression) {
            Pair<String, String> quotes = PyStringLiteralCoreUtil.getQuotes(defaultValue.getText());
            if (quotes != null) {
                return quotes.getFirst() + ((PyAstStringLiteralExpression)defaultValue).getStringValue() + quotes.getSecond();
            }
        }

        return defaultValue == null ? null : defaultValue.getText();
    }
}
