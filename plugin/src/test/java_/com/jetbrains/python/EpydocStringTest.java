package com.jetbrains.python;

import java.util.List;

import com.intellij.testFramework.UsefulTestCase;
import com.jetbrains.python.impl.documentation.docstrings.EpydocString;
import com.jetbrains.python.toolbox.Substring;

/**
 * @author yole
 */
public abstract class EpydocStringTest extends UsefulTestCase
{
	public void testTagValue()
	{
		EpydocString docString = new EpydocString(new Substring("@rtype: C{str}"));
		Substring s = docString.getTagValue("rtype");
		assertNotNull(s);
		assertEquals("C{str}", s.toString());
	}

	public void testTagWithParamValue()
	{
		EpydocString docString = new EpydocString(new Substring("@type m: number"));
		final Substring s = docString.getTagValue("type", "m");
		assertNotNull(s);
		assertEquals("number", s.toString());
	}

	public void testMultilineTag()
	{
		EpydocString docString = new EpydocString(new Substring("    @param b: The y intercept of the line.  The X{y intercept} of a\n" + "              line is the point at which it crosses the y "
				+ "axis (M{x=0})."));
		final Substring s = docString.getTagValue("param", "b");
		assertNotNull(s);
		assertEquals("The y intercept of the line.  The X{y intercept} of a line is the point at which it crosses the y axis (M{x=0}).", s.concatTrimmedLines(" "));

	}

	public void testInlineMarkup()
	{
		assertEquals("The y intercept of the line.  The y intercept of a line is the point at which it crosses the y axis (x=0).", EpydocString.removeInlineMarkup("The y intercept of the line.  The " +
				"" + "X{y intercept} of a line is the point at which it crosses the y axis (M{x=0})."));

	}

	public void testMultipleTags()
	{
		EpydocString docString = new EpydocString(new Substring("    \"\"\"\n" + "    Run the given function wrapped with seteuid/setegid calls.\n" + "\n" + "    This will try to minimize the number" +
				" of " + "seteuid/setegid calls, comparing\n" + "    current and wanted permissions\n" + "\n" + "    @param euid: effective UID used to call the function.\n" + "    @type  euid: " +
				"C{int}\n" + "\n" + "    @param egid: effective GID used to call the function.\n" + "    @type  egid: C{int}\n" + "\n" + "    @param function: the function run with the specific " +
				"permission.\n" + "    @type  function: any callable\n" + "\n" + "    @param *args: arguments passed to function\n" + "    @param **kwargs: keyword arguments passed to C{function}\n"
				+ "    \"\"\""));

		final List<String> params = docString.getParameters();
		assertOrderedEquals(params, "euid", "egid", "function", "*args", "**kwargs");
		assertEquals("effective UID used to call the function.", docString.getParamDescription("euid"));
		assertEquals("effective GID used to call the function.", docString.getParamDescription("egid"));
		assertEquals("arguments passed to function.", docString.getParamDescription("args"));
	}

	public void testInlineMarkupToHTML()
	{
		assertEquals("can contain <i>inline markup</i> and <b>bold text</b>", EpydocString.inlineMarkupToHTML("can contain I{inline markup} and B{bold text}"));
	}

	public void testCodeToHTML()
	{
		assertEquals("<code>my_dict={1:2, 3:4}</code>", EpydocString.inlineMarkupToHTML("C{my_dict={1:2, 3:4}}"));
	}

	public void testUrlToHTML()
	{
		assertEquals("<a href=\"http://www.python.org\">www.python.org</a>", EpydocString.inlineMarkupToHTML("U{www.python.org}"));
		assertEquals("<a href=\"http://www.python.org\">www.python.org</a>", EpydocString.inlineMarkupToHTML("U{www.python.org}"));
		assertEquals("<a href=\"http://epydoc.sourceforge.net\">The epydoc homepage</a>", EpydocString.inlineMarkupToHTML("U{The epydoc homepage<http://\n" + "    epydoc.sourceforge.net>}"));
	}

	public void testNestedInlineMarkup()
	{
		assertEquals("<i><b>Inline markup</b> may be nested; and it may span</i> multiple lines.", EpydocString.inlineMarkupToHTML("I{B{Inline markup} may be nested; and\n" + "    it may span} " +
				"multiple lines."));

	}

	public void testParagraph()
	{
		assertEquals("foo<p>bar", EpydocString.inlineMarkupToHTML("foo\n\nbar"));
	}

	public void testRemoveNestedInlineMarkup()
	{
		assertEquals("(ParsedDocstring, list of Field)", EpydocString.removeInlineMarkup("C{(L{ParsedDocstring}, list of L{Field})}"));
	}
}
