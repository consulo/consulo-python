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
package com.jetbrains.python.ast;

import com.jetbrains.python.PyElementTypes;
import consulo.annotation.access.RequiredReadAction;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.math.BigInteger;

public interface PyAstNumericLiteralExpression extends PyAstLiteralExpression {
    /**
     * Returns the value of this literal as a long (with any fraction truncated).
     * This method will return {@code null} if the value is too large or too
     * small to be represented as a long.
     */
    @RequiredReadAction
    default @Nullable Long getLongValue() {
        BigInteger value = getBigIntegerValue();
        long longValue = value.longValue();
        return BigInteger.valueOf(longValue).equals(value) ? longValue : null;
    }

    /**
     * Returns the value of this literal as a {@code BigInteger} (with any
     * fraction truncated).
     */
    @RequiredReadAction
    default BigInteger getBigIntegerValue() {
        if (isIntegerLiteral()) {
            return getBigIntegerValue(getNode().getText());
        }

        BigDecimal bigDecimal = getBigDecimalValue();
        return bigDecimal.toBigInteger();
    }

    /**
     * Returns the exact value of this literal.
     */
    @RequiredReadAction
    default BigDecimal getBigDecimalValue() {
        String text = getNode().getText();
        return isIntegerLiteral() ? new BigDecimal(getBigIntegerValue(text))
                                  : new BigDecimal(prepareLiteralForJava(text, 0));
    }

    @RequiredReadAction
    default boolean isIntegerLiteral() {
        return getNode().getElementType() == PyElementTypes.INTEGER_LITERAL_EXPRESSION;
    }

    /**
     * Returns possible suffix of integer literal like {@code uL}.
     *
     * @return null if this is not integer literal or the suffix is missed; string suffix otherwise
     */
    @RequiredReadAction
    default @Nullable String getIntegerLiteralSuffix() {
        return isIntegerLiteral() ? StringUtil.nullize(retrieveSuffix(getText())) : null;
    }

    private static BigInteger getBigIntegerValue(String text) {
        if (text.equalsIgnoreCase("0" + retrieveSuffix(text))) {
            return BigInteger.ZERO;
        }

        int beginIndex;
        int radix;

        if (StringUtil.startsWithIgnoreCase(text, "0x")) {
            beginIndex = 2;
            radix = 16;
        }
        else if (StringUtil.startsWithIgnoreCase(text, "0b")) {
            beginIndex = 2;
            radix = 2;
        }
        else if (text.startsWith("0")) {
            if (StringUtil.isChar(text, 1, 'o') || StringUtil.isChar(text, 1, 'O')) {
                beginIndex = 2;
            }
            else {
                beginIndex = 1;
            }
            radix = 8;
        }
        else {
            beginIndex = 0;
            radix = 10;
        }

        return new BigInteger(prepareLiteralForJava(text, beginIndex), radix);
    }

    private static String prepareLiteralForJava(String text, int beginIndex) {
        int endIndex = text.length() - retrieveSuffix(text).length();

        StringBuilder result = new StringBuilder(endIndex - beginIndex);
        for (int i = beginIndex; i < endIndex; i++) {
            char c = text.charAt(i);
            if (c != '_') {
                result.append(c);
            }
        }
        return result.toString();
    }

    private static String retrieveSuffix(String text) {
        int lastIndex = text.length();
        while (lastIndex > 0) {
            char last = text.charAt(lastIndex - 1);
            if (last != 'u' && last != 'U' && last != 'l' && last != 'L' && last != 'j' && last != 'J') {
                break;
            }
            --lastIndex;
        }
        return text.substring(lastIndex);
    }

    @Override
    default void acceptPyVisitor(PyAstElementVisitor pyVisitor) {
        pyVisitor.visitPyNumericLiteralExpression(this);
    }
}
