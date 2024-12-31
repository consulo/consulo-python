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
package com.jetbrains.python.impl.psi.impl;

import com.google.common.collect.Collections2;
import com.google.common.collect.Queues;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.PythonStringUtil;
import com.jetbrains.python.impl.NotNullPredicate;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import consulo.annotation.component.ServiceImpl;
import consulo.language.ast.ASTNode;
import consulo.language.ast.TokenSet;
import consulo.language.impl.psi.LeafPsiElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiFileFactory;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.language.version.LanguageVersion;
import consulo.project.Project;
import consulo.util.io.CharsetToolkit;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.Arrays;
import java.util.Deque;
import java.util.Formatter;

/**
 * @author yole
 */
@ServiceImpl
@Singleton
public class PyElementGeneratorImpl extends PyElementGenerator {
  private static final CommasOnly COMMAS_ONLY = new CommasOnly();
  private final Project myProject;

  @Inject
  public PyElementGeneratorImpl(Project project) {
    myProject = project;
  }

  @Override
	public ASTNode createNameIdentifier(String name, LanguageLevel languageLevel) {
    final PsiFile dummyFile = createDummyFile(languageLevel, name);
    final PyExpressionStatement expressionStatement = (PyExpressionStatement)dummyFile.getFirstChild();
    final PyReferenceExpression refExpression = (PyReferenceExpression)expressionStatement.getFirstChild();

    return refExpression.getNode().getFirstChildNode();
  }

  @Override
  public PsiFile createDummyFile(LanguageLevel langLevel, String contents) {
    return createDummyFile(langLevel, contents, false);
  }

  public PsiFile createDummyFile(LanguageLevel langLevel, String contents, boolean physical) {
    final PsiFileFactory factory = PsiFileFactory.getInstance(myProject);
    final String name = getDummyFileName();
    LanguageVersion version = PythonLanguage.INSTANCE.getVersion(langLevel);
    return factory.createFileFromText(name, version, contents, physical, true, true);
  }

  /**
   * @return name used for {@link #createDummyFile(LanguageLevel, String)}
   */
  @Nonnull
  public static String getDummyFileName() {
    return "dummy." + PythonFileType.INSTANCE.getDefaultExtension();
  }

  @Override
	public PyStringLiteralExpression createStringLiteralAlreadyEscaped(String str) {
    final PsiFile dummyFile = createDummyFile(LanguageLevel.getDefault(), "a=(" + str + ")");
    final PyAssignmentStatement expressionStatement = (PyAssignmentStatement)dummyFile.getFirstChild();
    final PyExpression assignedValue = expressionStatement.getAssignedValue();
    if (assignedValue != null) {
      return (PyStringLiteralExpression)((PyParenthesizedExpression)assignedValue).getContainedExpression();
    }
    return createStringLiteralFromString(str);
  }


  @Override
  public PyStringLiteralExpression createStringLiteralFromString(@Nonnull String unescaped) {
    return createStringLiteralFromString(null, unescaped, true);
  }

  @Override
	public PyStringLiteralExpression createStringLiteral(@Nonnull PyStringLiteralExpression oldElement, @Nonnull String unescaped) {
    Pair<String, String> quotes = PythonStringUtil.getQuotes(oldElement.getText());
    if (quotes != null) {
      return createStringLiteralAlreadyEscaped(quotes.first + unescaped + quotes.second);
    }
    else {
      return createStringLiteralFromString(unescaped);
    }
  }


  @Override
  public PyStringLiteralExpression createStringLiteralFromString(@Nullable PsiFile destination,
                                                                 @Nonnull String unescaped,
                                                                 final boolean preferUTF8) {
    boolean useDouble = !unescaped.contains("\"");
    boolean useMulti = unescaped.matches(".*(\r|\n).*");
    String quotes;
    if (useMulti) {
      quotes = useDouble ? "\"\"\"" : "'''";
    }
    else {
      quotes = useDouble ? "\"" : "'";
    }
    StringBuilder buf = new StringBuilder(unescaped.length() * 2);
    buf.append(quotes);
    VirtualFile vfile = destination == null ? null : destination.getVirtualFile();
    Charset charset;
    if (vfile == null) {
      charset = (preferUTF8 ? CharsetToolkit.UTF8_CHARSET : Charset.forName("US-ASCII"));
    }
    else {
      charset = vfile.getCharset();
    }
    CharsetEncoder encoder = charset.newEncoder();
    Formatter formatter = new Formatter(buf);
    boolean unicode = false;
    for (int i = 0; i < unescaped.length(); i++) {
      int c = unescaped.codePointAt(i);
      if (c == '"' && useDouble) {
        buf.append("\\\"");
      }
      else if (c == '\'' && !useDouble) {
        buf.append("\\'");
      }
      else if ((c == '\r' || c == '\n') && !useMulti) {
        if (c == '\r') {
          buf.append("\\r");
        }
        else if (c == '\n') {
          buf.append("\\n");
        }
      }
      else if (!encoder.canEncode(new String(Character.toChars(c)))) {
        if (c <= 0xff) {
          formatter.format("\\x%02x", c);
        }
        else if (c < 0xffff) {
          unicode = true;
          formatter.format("\\u%04x", c);
        }
        else {
          unicode = true;
          formatter.format("\\U%08x", c);
        }
      }
      else {
        buf.appendCodePoint(c);
      }
    }
    buf.append(quotes);
    if (unicode) {
      buf.insert(0, "u");
    }

    return createStringLiteralAlreadyEscaped(buf.toString());
  }

  @Override
	public PyListLiteralExpression createListLiteral() {
    final PsiFile dummyFile = createDummyFile(LanguageLevel.getDefault(), "[]");
    final PyExpressionStatement expressionStatement = (PyExpressionStatement)dummyFile.getFirstChild();
    return (PyListLiteralExpression)expressionStatement.getFirstChild();
  }

  @Override
	public ASTNode createComma() {
    final PsiFile dummyFile = createDummyFile(LanguageLevel.getDefault(), "[0,]");
    final PyExpressionStatement expressionStatement = (PyExpressionStatement)dummyFile.getFirstChild();
    ASTNode zero = expressionStatement.getFirstChild().getNode().getFirstChildNode().getTreeNext();
    return zero.getTreeNext().copyElement();
  }

  @Override
	public ASTNode createDot() {
    final PsiFile dummyFile = createDummyFile(LanguageLevel.getDefault(), "a.b");
    final PyExpressionStatement expressionStatement = (PyExpressionStatement)dummyFile.getFirstChild();
    ASTNode dot = expressionStatement.getFirstChild().getNode().getFirstChildNode().getTreeNext();
    return dot.copyElement();
  }

  @Override
  @Nonnull
  public PsiElement insertItemIntoListRemoveRedundantCommas(@Nonnull final PyElement list,
                                                            @Nullable final PyExpression afterThis,
                                                            @Nonnull final PyExpression toInsert) {
    // TODO: #insertItemIntoList is probably buggy. In such case, fix it and get rid of this method
    final PsiElement result = insertItemIntoList(list, afterThis, toInsert);
    final LeafPsiElement[] leafs = PsiTreeUtil.getChildrenOfType(list, LeafPsiElement.class);
    if (leafs != null) {
      final Deque<LeafPsiElement> commas = Queues.newArrayDeque(Collections2.filter(Arrays.asList(leafs), COMMAS_ONLY));
      if (!commas.isEmpty()) {
        final LeafPsiElement lastComma = commas.getLast();
        if (PsiTreeUtil.getNextSiblingOfType(lastComma, PyExpression.class) == null) { //Comma has no expression after it
          lastComma.delete();
        }
      }
    }

    return result;
  }

  // TODO: Adds comma to empty list: adding "foo" to () will create (foo,). That is why "insertItemIntoListRemoveRedundantCommas" was created.
  // We probably need to fix this method and delete insertItemIntoListRemoveRedundantCommas
  @Override
	public PsiElement insertItemIntoList(PyElement list,
																			 @Nullable PyExpression afterThis,
																			 PyExpression toInsert) throws IncorrectOperationException {
    ASTNode add = toInsert.getNode().copyElement();
    if (afterThis == null) {
      ASTNode exprNode = list.getNode();
      ASTNode[] closingTokens = exprNode.getChildren(TokenSet.create(PyTokenTypes.LBRACKET, PyTokenTypes.LPAR));
      if (closingTokens.length == 0) {
        // we tried our best. let's just insert it at the end
        exprNode.addChild(add);
      }
      else {
        ASTNode next = PyPsiUtils.getNextNonWhitespaceSibling(closingTokens[closingTokens.length - 1]);
        if (next != null) {
          ASTNode comma = createComma();
          exprNode.addChild(comma, next);
          exprNode.addChild(add, comma);
        }
        else {
          exprNode.addChild(add);
        }
      }
    }
    else {
      ASTNode lastArgNode = afterThis.getNode();
      ASTNode comma = createComma();
      ASTNode parent = lastArgNode.getTreeParent();
      ASTNode afterLast = lastArgNode.getTreeNext();
      if (afterLast == null) {
        parent.addChild(add);
      }
      else {
        parent.addChild(add, afterLast);
      }
      parent.addChild(comma, add);
    }
    return add.getPsi();
  }

  @Override
	public PyBinaryExpression createBinaryExpression(String s, PyExpression expr, PyExpression listLiteral) {
    final PsiFile dummyFile = createDummyFile(LanguageLevel.getDefault(), "a " + s + " b");
    final PyExpressionStatement expressionStatement = (PyExpressionStatement)dummyFile.getFirstChild();
    PyBinaryExpression binExpr = (PyBinaryExpression)expressionStatement.getExpression();
    ASTNode binnode = binExpr.getNode();
    binnode.replaceChild(binExpr.getLeftExpression().getNode(), expr.getNode().copyElement());
    binnode.replaceChild(binExpr.getRightExpression().getNode(), listLiteral.getNode().copyElement());
    return binExpr;
  }

  @Override
	public PyExpression createExpressionFromText(final String text) {
    return createExpressionFromText(LanguageLevel.getDefault(), text);
  }

  @Override
	@Nonnull
  public PyExpression createExpressionFromText(final LanguageLevel languageLevel, final String text) {
    final PsiFile dummyFile = createDummyFile(languageLevel, text);
    final PsiElement element = dummyFile.getFirstChild();
    if (element instanceof PyExpressionStatement) {
      return ((PyExpressionStatement)element).getExpression();
    }
    throw new IncorrectOperationException("could not parse text as expression: " + text);
  }

  @Override
	@Nonnull
  public PyCallExpression createCallExpression(final LanguageLevel langLevel, String functionName) {
    final PsiFile dummyFile = createDummyFile(langLevel, functionName + "()");
    final PsiElement child = dummyFile.getFirstChild();
    if (child != null) {
      final PsiElement element = child.getFirstChild();
      if (element instanceof PyCallExpression) {
        return (PyCallExpression)element;
      }
    }
    throw new IllegalArgumentException("Invalid call expression text " + functionName);
  }

  @Override
  public PyImportElement createImportElement(final LanguageLevel languageLevel, String name) {
    return createFromText(languageLevel, PyImportElement.class, "from foo import " + name, new int[]{
      0,
      6
    });
  }

  @Override
  public PyFunction createProperty(LanguageLevel languageLevel, String propertyName, String fieldName, AccessDirection accessDirection) {
    String propertyText;
    if (accessDirection == AccessDirection.DELETE) {
      propertyText = "@" + propertyName + ".deleter\ndef " + propertyName + "(self):\n  del self." + fieldName;
    }
    else if (accessDirection == AccessDirection.WRITE) {
      propertyText = "@" + propertyName + ".setter\ndef " + propertyName + "(self, value):\n  self." + fieldName + " = value";
    }
    else {
      propertyText = "@property\ndef " + propertyName + "(self):\n  return self." + fieldName;
    }
    return createFromText(languageLevel, PyFunction.class, propertyText);
  }

  static final int[] FROM_ROOT = new int[]{0};

  @Override
	@Nonnull
  public <T> T createFromText(LanguageLevel langLevel, Class<T> aClass, final String text) {
    return createFromText(langLevel, aClass, text, FROM_ROOT);
  }

  @Nonnull
  @Override
  public <T> T createPhysicalFromText(LanguageLevel langLevel, Class<T> aClass, String text) {
    return createFromText(langLevel, aClass, text, FROM_ROOT, true);
  }

  static int[] PATH_PARAMETER = {
    0,
    3,
    1
  };

  @Override
	public PyNamedParameter createParameter(@Nonnull String name) {
    return createParameter(name, null, null, LanguageLevel.getDefault());
  }

  @Nonnull
  @Override
  public PyParameterList createParameterList(@Nonnull LanguageLevel languageLevel, @Nonnull String text) {
    return createFromText(languageLevel, PyParameterList.class, "def f" + text + ": pass", new int[]{
      0,
      3
    });
  }

  @Nonnull
  @Override
  public PyArgumentList createArgumentList(@Nonnull LanguageLevel languageLevel, @Nonnull String text) {
    return createFromText(languageLevel, PyArgumentList.class, "f" + text, new int[]{
      0,
      0,
      1
    });
  }


  @Override
	public PyNamedParameter createParameter(@Nonnull String name,
																					@Nullable String defaultValue,
																					@Nullable String annotation,
																					@Nonnull LanguageLevel languageLevel) {
    String parameterText = name;
    if (annotation != null) {
      parameterText += ": " + annotation;
    }
    if (defaultValue != null) {
      parameterText += " = " + defaultValue;
    }

    return createFromText(languageLevel, PyNamedParameter.class, "def f(" + parameterText + "): pass", PATH_PARAMETER);
  }

  @Override
  public PyKeywordArgument createKeywordArgument(LanguageLevel languageLevel, String keyword, String value) {
    PyCallExpression callExpression = (PyCallExpression)createExpressionFromText(languageLevel, "foo(" + keyword + "=" + value + ")");
    return (PyKeywordArgument)callExpression.getArguments()[0];
  }

  @Override
	@Nonnull
  public <T> T createFromText(LanguageLevel langLevel, Class<T> aClass, final String text, final int[] path) {
    return createFromText(langLevel, aClass, text, path, false);
  }

  @Nonnull
  public <T> T createFromText(LanguageLevel langLevel, Class<T> aClass, final String text, final int[] path, boolean physical) {
    PsiElement ret = createDummyFile(langLevel, text, physical);
    for (int skip : path) {
      if (ret != null) {
        ret = ret.getFirstChild();
        for (int i = 0; i < skip; i += 1) {
          if (ret != null) {
            ret = ret.getNextSibling();
          }
          else {
            ret = null;
            break;
          }
        }
      }
      else {
        break;
      }
    }
    if (ret == null) {
      throw new IllegalArgumentException("Can't find element matching path " + Arrays.toString(path) + " in text '" + text + "'");
    }
    try {
      //noinspection unchecked
      return (T)ret;
    }
    catch (ClassCastException e) {
      throw new IllegalArgumentException("Can't create an expression of type " + aClass + " from text '" + text + "'");
    }
  }

  @Override
  public PyPassStatement createPassStatement() {
    final PyStatementList statementList = createPassStatementList();
    return (PyPassStatement)statementList.getStatements()[0];
  }

  @Nonnull
  @Override
  public PyDecoratorList createDecoratorList(@Nonnull final String... decoratorTexts) {
    assert decoratorTexts.length > 0;
    StringBuilder functionText = new StringBuilder();
    for (String decoText : decoratorTexts) {
      functionText.append(decoText).append("\n");
    }
    functionText.append("def foo():\n\tpass");
    final PyFunction function = createFromText(LanguageLevel.getDefault(), PyFunction.class, functionText.toString());
    final PyDecoratorList decoratorList = function.getDecoratorList();
    assert decoratorList != null;
    return decoratorList;
  }

  private PyStatementList createPassStatementList() {
    final PyFunction function = createFromText(LanguageLevel.getDefault(), PyFunction.class, "def foo():\n\tpass");
    return function.getStatementList();
  }

  @Override
	public PyExpressionStatement createDocstring(String content) {
    return createFromText(LanguageLevel.getDefault(), PyExpressionStatement.class, content + "\n");
  }

  @Nonnull
  @Override
  public PsiElement createNewLine() {
    return createFromText(LanguageLevel.getDefault(), PsiWhiteSpace.class, " \n\n ");
  }

  @Nonnull
  @Override
  public PyFromImportStatement createFromImportStatement(@Nonnull LanguageLevel languageLevel,
                                                         @Nonnull String qualifier,
                                                         @Nonnull String name,
                                                         @Nullable String alias) {
    final String asClause = StringUtil.isNotEmpty(alias) ? " as " + alias : "";
    final String statement = "from " + qualifier + " import " + name + asClause;
    return createFromText(languageLevel, PyFromImportStatement.class, statement);
  }

  @Nonnull
  @Override
  public PyImportStatement createImportStatement(@Nonnull LanguageLevel languageLevel, @Nonnull String name, @Nullable String alias) {
    final String asClause = StringUtil.isNotEmpty(alias) ? " as " + alias : "";
    final String statement = "import " + name + asClause;
    return createFromText(languageLevel, PyImportStatement.class, statement);
  }

  private static class CommasOnly extends NotNullPredicate<LeafPsiElement> {
    @Override
    protected boolean applyNotNull(@Nonnull final LeafPsiElement input) {
      return input.getNode().getElementType().equals(PyTokenTypes.COMMA);
    }
  }
}
