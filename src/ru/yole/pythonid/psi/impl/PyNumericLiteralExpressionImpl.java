/*
 * Copyright 2006 Dmitry Jemerov (yole)
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

package ru.yole.pythonid.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.Nullable;
import ru.yole.pythonid.AbstractPythonLanguage;
import ru.yole.pythonid.psi.PyElementVisitor;
import ru.yole.pythonid.psi.PyNumericLiteralExpression;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PyNumericLiteralExpressionImpl extends PyElementImpl
		implements PyNumericLiteralExpression {
	private static final Pattern PATTERN_INT = Pattern.compile("(?:([1-9]\\d*)|(0)|(0[0-7]+)|(?:0x([0-9a-f]+)))L?", 2);

	private static final Pattern PATTERN_FLOAT = Pattern.compile("((\\d+)(?:\\.(\\d+)?)?|\\.(\\d+))(e(\\+|-)?(\\d))?", 2);

	public PyNumericLiteralExpressionImpl(ASTNode astNode, AbstractPythonLanguage language) {
		super(astNode, language);
	}

	@Override
	protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
		pyVisitor.visitPyNumericLiteralExpression(this);
	}

	@Override
	public Long getLongValue() {
		BigInteger value = getBigIntegerValue();
		long longValue = value.longValue();
		if (BigInteger.valueOf(longValue).equals(value)) {
			return Long.valueOf(longValue);
		}
		return null;
	}

	@Override
	public BigInteger getBigIntegerValue() {
		ASTNode node = getNode();
		String text = node.getText();
		IElementType type = node.getElementType();
		if (type == getPyElementTypes().INTEGER_LITERAL_EXPRESSION) {
			return getBigIntegerValue(text);
		}
		return getBigDecimalValue().toBigInteger();
	}

	@Override
	public BigDecimal getBigDecimalValue() {
		ASTNode node = getNode();
		String text = node.getText();
		IElementType type = node.getElementType();
		if (type == getPyElementTypes().INTEGER_LITERAL_EXPRESSION) {
			return new BigDecimal(getBigIntegerValue(text));
		}
		Matcher m = PATTERN_FLOAT.matcher(text);
		boolean matches = m.matches();
		assert (matches);

		if (m.group(2) != null) {
			BigDecimal whole = new BigDecimal(m.group(2));
			String fractionStr = m.group(3);
			BigDecimal fraction = BigDecimal.ZERO;
			if (fractionStr != null) {
				fraction = new BigDecimal("0." + fractionStr);
			}
			whole = whole.add(fraction);
		} else {
			BigDecimal whole;
			if (m.group(4) != null)
				whole = new BigDecimal("0." + m.group(4));
			else
				throw new IllegalStateException("Cannot parse BigDecimal for " + text);
		}
		BigDecimal whole;
		if (m.group(5) != null) {
			String sign = m.group(6);
			if (sign == null) sign = "+";
			String exp = m.group(7);
			whole = whole.multiply(new BigDecimal("1e" + sign + exp));
		}
		return whole;
	}

	@Nullable
	private static BigInteger getBigIntegerValue(String text) {
		Matcher m = PATTERN_INT.matcher(text);
		if (!m.matches()) return null;
		int radix;
		if (m.group(1) != null) {
			radix = 10;
		} else {
			if (m.group(2) != null)
				return BigInteger.ZERO;
			int radix;
			if (m.group(3) != null) {
				radix = 8;
			} else {
				int radix;
				if (m.group(4) != null)
					radix = 16;
				else
					throw new IllegalStateException("No radix found: " + text);
			}
		}
		int radix;
		return new BigInteger(text, radix);
	}
}