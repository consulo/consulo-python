package com.jetbrains.python;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import consulo.util.lang.Pair;
import consulo.document.util.TextRange;
import consulo.language.psi.LiteralTextEscaper;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiFileFactory;
import consulo.language.psi.PsiLanguageInjectionHost;
import consulo.language.psi.util.PsiTreeUtil;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.PyStringLiteralExpression;

/**
 * @author yole
 */
public abstract class PyStringLiteralTest extends PyTestCase
{
	public void testEscaperDecode()
	{
		final PyStringLiteralExpression expr = createLiteralFromText("'\\nfoo'");
		assertNotNull(expr);
		assertEquals("fo", decodeRange(expr, TextRange.create(3, 5)));
		assertEquals("\n", decodeRange(expr, TextRange.create(1, 3)));
	}

	public void testEscaperOffsetInHost()
	{
		final PyStringLiteralExpression expr = createLiteralFromText("'\\nfoo'");
		assertNotNull(expr);
		final LiteralTextEscaper<? extends PsiLanguageInjectionHost> escaper = expr.createLiteralTextEscaper();
		final TextRange newLineFoo = TextRange.create(1, 6);
		assertEquals(1, escaper.getOffsetInHost(0, newLineFoo));
		assertEquals(3, escaper.getOffsetInHost(1, newLineFoo));
		assertEquals(4, escaper.getOffsetInHost(2, newLineFoo));
		assertEquals(5, escaper.getOffsetInHost(3, newLineFoo));
		assertEquals(6, escaper.getOffsetInHost(4, newLineFoo));
		assertEquals(-1, escaper.getOffsetInHost(5, newLineFoo));
	}

	public void testEscaperOffsetInHostSubString()
	{
		final PyStringLiteralExpression expr = createLiteralFromText("'\\nfoo'");
		assertNotNull(expr);
		final LiteralTextEscaper<? extends PsiLanguageInjectionHost> escaper = expr.createLiteralTextEscaper();
		final TextRange fooOnly = TextRange.create(3, 6);
		assertEquals(3, escaper.getOffsetInHost(0, fooOnly));
		assertEquals(4, escaper.getOffsetInHost(1, fooOnly));
		assertEquals(5, escaper.getOffsetInHost(2, fooOnly));
		assertEquals(6, escaper.getOffsetInHost(3, fooOnly));
		assertEquals(-1, escaper.getOffsetInHost(4, fooOnly));
	}

	public void testIterateCharacterRanges()
	{
		final PyStringLiteralExpression expr = createLiteralFromText("'\\nfoo'  'bar'");
		assertNotNull(expr);
		final List<String> characters = new ArrayList<String>();
		List<Pair<TextRange, String>> decodedFragments = expr.getDecodedFragments();
		for(Pair<TextRange, String> decodedFragment : decodedFragments)
		{
			characters.add(decodedFragment.getSecond());
		}

		final List<String> expected = Arrays.asList("\n", "f", "o", "o", "b", "a", "r");
		assertSameElements(characters, expected);
	}

	private static String decodeRange(PyStringLiteralExpression expr, TextRange range)
	{
		final StringBuilder builder = new StringBuilder();
		expr.createLiteralTextEscaper().decode(range, builder);
		return builder.toString();
	}

	private PyStringLiteralExpression createLiteralFromText(final String text)
	{
		final PsiFile file = PsiFileFactory.getInstance(myFixture.getProject()).createFileFromText("test.py", "a = " + text);
		final PyStringLiteralExpression expr = PsiTreeUtil.getParentOfType(file.findElementAt(5), PyStringLiteralExpression.class);
		assert expr != null;
		return expr;
	}

	public void testStringValue()
	{
		assertEquals("foo", createLiteralFromText("\"\"\"foo\"\"\"").getStringValue());
		assertEquals("foo", createLiteralFromText("u\"foo\"").getStringValue());
		assertEquals("foo", createLiteralFromText("b\"foo\"").getStringValue());
		assertEquals("\\b", createLiteralFromText("r'\\b'").getStringValue());
		assertEquals("b\\n", createLiteralFromText("ur'\\u0062\\n'").getStringValue());
		assertEquals("\\8", createLiteralFromText("'\\8'").getStringValue());
	}
}
