/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.impl.validation;

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.impl.PythonHelpersLocator;
import com.jetbrains.python.psi.*;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.logging.Logger;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.helpers.XMLReaderFactory;

import java.io.CharArrayWriter;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

/**
 * User: catherine
 */
public class UnsupportedFeaturesUtil
{
	public static Map<LanguageLevel, Set<String>> BUILTINS = new HashMap<>();
	public static Map<LanguageLevel, Set<String>> MODULES = new HashMap<>();
	public static Map<String, Map<LanguageLevel, Set<String>>> CLASS_METHODS = new HashMap<>();

	static
	{
		try
		{
			fillMaps();
			fillTestCaseMethods();
		}
		catch(IOException e)
		{
			Logger log = Logger.getInstance(UnsupportedFeaturesUtil.class.getName());
			log.error("Cannot find \"versions.xml\". " + e.getMessage());
		}
	}

	private static void fillTestCaseMethods() throws IOException
	{
		Logger log = Logger.getInstance(UnsupportedFeaturesUtil.class.getName());
		FileReader reader = new FileReader(PythonHelpersLocator.getHelperPath("/tools/class_method_versions.xml"));
		try
		{
			XMLReader xr = XMLReaderFactory.createXMLReader();
			ClassMethodsParser parser = new ClassMethodsParser();
			xr.setContentHandler(parser);
			xr.parse(new InputSource(reader));
		}
		catch(SAXException e)
		{
			log.error("Improperly formed \"class_method_versions.xml\". " + e.getMessage());
		}
		finally
		{
			reader.close();
		}
	}

	private static void fillMaps() throws IOException
	{
		Logger log = Logger.getInstance(UnsupportedFeaturesUtil.class.getName());
		FileReader reader = new FileReader(PythonHelpersLocator.getHelperPath("/tools/versions.xml"));
		try
		{
			XMLReader xr = XMLReaderFactory.createXMLReader();
			VersionsParser parser = new VersionsParser();
			xr.setContentHandler(parser);
			xr.parse(new InputSource(reader));
		}
		catch(SAXException e)
		{
			log.error("Improperly formed \"versions.xml\". " + e.getMessage());
		}
		finally
		{
			reader.close();
		}
	}

	public static boolean raiseHasNoArgs(PyRaiseStatement node, LanguageLevel versionToProcess)
	{
		PyExpression[] expressions = node.getExpressions();
		if(expressions.length == 0 && versionToProcess.isPy3K())
		{
			PyExceptPart exceptPart = PsiTreeUtil.getParentOfType(node, PyExceptPart.class);
			if(exceptPart == null)
			{
				return true;
			}
		}
		return false;
	}

	public static boolean raiseHasMoreThenOneArg(PyRaiseStatement node, LanguageLevel versionToProcess)
	{
		PyExpression[] expressions = node.getExpressions();
		if(expressions.length > 0)
		{
			if(expressions.length < 2)
			{
				return false;
			}
			if(versionToProcess.isPy3K())
			{
				if(expressions.length == 3)
				{
					return true;
				}
				PsiElement element = expressions[0].getNextSibling();
				while(element instanceof PsiWhiteSpace)
				{
					element = element.getNextSibling();
				}
				if(element != null && ",".equals(element.getText()))
				{
					return true;
				}
			}
		}
		return false;
	}

	public static boolean raiseHasFromKeyword(PyRaiseStatement node, LanguageLevel versionToProcess)
	{
		PyExpression[] expressions = node.getExpressions();
		if(expressions.length > 0)
		{
			if(expressions.length < 2)
			{
				return false;
			}
			if(!versionToProcess.isPy3K())
			{
				PsiElement element = expressions[0].getNextSibling();
				while(element instanceof PsiWhiteSpace)
				{
					element = element.getNextSibling();
				}
				if(element != null && element.getNode().getElementType() == PyTokenTypes.FROM_KEYWORD)
				{
					return true;
				}
			}
		}
		return false;
	}

	public static boolean visitPyListCompExpression(PyListCompExpression node, LanguageLevel versionToProcess)
	{
		List<PyComprehensionForComponent> forComponents = node.getForComponents();
		if(versionToProcess.isPy3K())
		{
			for(PyComprehensionForComponent forComponent : forComponents)
			{
				PyExpression iteratedList = forComponent.getIteratedList();
				if(iteratedList instanceof PyTupleExpression)
				{
					return true;
				}
			}
		}
		return false;
	}

	private static class VersionsParser extends DefaultHandler
	{
		private CharArrayWriter myContent = new CharArrayWriter();
		private LanguageLevel myCurrentLevel;

		public void startElement(String namespaceURI, String localName, String qName, Attributes attr) throws SAXException
		{
			myContent.reset();
			if(localName.equals("python"))
			{
				BUILTINS.put(LanguageLevel.fromPythonVersion(attr.getValue("version")), new HashSet<>());
				MODULES.put(LanguageLevel.fromPythonVersion(attr.getValue("version")), new HashSet<>());
				myCurrentLevel = LanguageLevel.fromPythonVersion(attr.getValue("version"));
			}
		}

		public void endElement(String namespaceURI, String localName, String qName) throws SAXException
		{
			if(localName.equals("func"))
			{
				BUILTINS.get(myCurrentLevel).add(myContent.toString());
			}
			if(localName.equals("module"))
			{
				MODULES.get(myCurrentLevel).add(myContent.toString());
			}
		}

		public void characters(char[] ch, int start, int length) throws SAXException
		{
			myContent.write(ch, start, length);
		}
	}

	static class ClassMethodsParser extends DefaultHandler
	{
		private CharArrayWriter myContent = new CharArrayWriter();
		private String myClassName = "";
		private LanguageLevel myCurrentLevel;

		public void startElement(String namespaceURI, String localName, String qName, Attributes attr) throws SAXException
		{
			myContent.reset();
			if(localName.equals("class_name"))
			{
				myClassName = attr.getValue("name");
				if(!CLASS_METHODS.containsKey(myClassName))
				{
					CLASS_METHODS.put(myClassName, new HashMap<>());

				}
			}
			if(localName.equals("python"))
			{
				myCurrentLevel = LanguageLevel.fromPythonVersion(attr.getValue("version"));
				if(myClassName != null)
				{
					Map<LanguageLevel, Set<String>> map = CLASS_METHODS.get(myClassName);
					if(map != null)
					{
						map.put(myCurrentLevel, new HashSet<>());
					}
				}
			}
		}

		public void endElement(String namespaceURI, String localName, String qName) throws SAXException
		{
			if(localName.equals("func"))
			{
				Map<LanguageLevel, Set<String>> levelSetMap = CLASS_METHODS.get(myClassName);
				if(levelSetMap != null)
				{
					Set<String> set = levelSetMap.get(myCurrentLevel);
					if(set != null)
					{
						set.add(myContent.toString());
					}
				}
			}
		}

		public void characters(char[] ch, int start, int length) throws SAXException
		{
			myContent.write(ch, start, length);
		}
	}
}

