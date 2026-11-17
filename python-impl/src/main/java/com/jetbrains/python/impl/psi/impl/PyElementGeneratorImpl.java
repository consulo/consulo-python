// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import consulo.annotation.component.ServiceImpl;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.google.common.collect.Collections2;
import com.google.common.collect.Queues;
import consulo.language.ast.ASTNode;
import consulo.project.Project;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.impl.psi.LeafPsiElement;
import consulo.language.ast.TokenSet;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import com.jetbrains.python.impl.NotNullPredicate;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyDecoratorList;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyEllipsisLiteralExpression;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyExpressionStatement;
import com.jetbrains.python.psi.PyFromImportStatement;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.PyImportStatement;
import com.jetbrains.python.psi.PyKeywordArgument;
import com.jetbrains.python.psi.PyListLiteralExpression;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyParameterList;
import com.jetbrains.python.psi.PyParenthesizedExpression;
import com.jetbrains.python.psi.PyPattern;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PySingleStarParameter;
import com.jetbrains.python.psi.PySlashParameter;
import com.jetbrains.python.psi.PyStringLiteralCoreUtil;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.StringLiteralExpression;
import org.jspecify.annotations.Nullable;

import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Deque;
import java.util.Formatter;


@ServiceImpl
@Singleton
public final class PyElementGeneratorImpl extends PyElementGenerator {
    private static final CommasOnly COMMAS_ONLY = new CommasOnly();

    @Inject
    public PyElementGeneratorImpl(Project project) {
        super(project);
    }

    @Override
    public ASTNode createNameIdentifier(String name, LanguageLevel languageLevel) {
        PsiFile dummyFile = createDummyFile(languageLevel, name);
        PyExpressionStatement expressionStatement = (PyExpressionStatement)dummyFile.getFirstChild();
        PyReferenceExpression refExpression = (PyReferenceExpression)expressionStatement.getFirstChild();

        return refExpression.getNode().getFirstChildNode();
    }

    @Override
    public PyStringLiteralExpression createStringLiteralAlreadyEscaped(String str) {
        PsiFile dummyFile = createDummyFile(LanguageLevel.getDefault(), "a=(" + str + ")");
        PyAssignmentStatement expressionStatement = (PyAssignmentStatement)dummyFile.getFirstChild();
        PyExpression assignedValue = expressionStatement.getAssignedValue();
        if (assignedValue != null) {
            return (PyStringLiteralExpression)((PyParenthesizedExpression)assignedValue).getContainedExpression();
        }
        return createStringLiteralFromString(str);
    }

    @Override
    public PyStringLiteralExpression createStringLiteralFromString(String unescaped) {
        return createStringLiteralFromString(null, unescaped, true, true);
    }

    @Override
    public PyStringLiteralExpression createStringLiteral(StringLiteralExpression oldElement, String unescaped) {
        Pair<String, String> quotes = PyStringLiteralCoreUtil.getQuotes(oldElement.getText());
        if (quotes != null) {
            return createStringLiteralAlreadyEscaped(quotes.first + unescaped + quotes.second);
        }
        else {
            return createStringLiteralFromString(unescaped);
        }
    }

    @Override
    protected PyStringLiteralExpression createStringLiteralFromString(@Nullable PsiFile destination,
            String unescaped,
            boolean preferUTF8,
            boolean preferDoubleQuotes) {
        boolean useDouble = (!unescaped.contains("\"") && preferDoubleQuotes) || unescaped.contains("'");
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
            charset = (preferUTF8 ? StandardCharsets.UTF_8 : StandardCharsets.US_ASCII);
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
                buf.append(c == '\r' ? "\\r" : "\\n");
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
    public PyStringLiteralExpression createStringLiteralFromString(String unescaped, boolean preferDoubleQuotes) {
        return createStringLiteralFromString(null, unescaped, true, preferDoubleQuotes);
    }

    @Override
    public PyListLiteralExpression createListLiteral() {
        PsiFile dummyFile = createDummyFile(LanguageLevel.getDefault(), "[]");
        PyExpressionStatement expressionStatement = (PyExpressionStatement)dummyFile.getFirstChild();
        return (PyListLiteralExpression)expressionStatement.getFirstChild();
    }

    @Override
    public ASTNode createDot() {
        PsiFile dummyFile = createDummyFile(LanguageLevel.getDefault(), "a.b");
        PyExpressionStatement expressionStatement = (PyExpressionStatement)dummyFile.getFirstChild();
        ASTNode dot = expressionStatement.getFirstChild().getNode().getFirstChildNode().getTreeNext();
        return dot.copyElement();
    }

    @Override
    public PsiElement insertItemIntoListRemoveRedundantCommas(
        PyElement list,
        @Nullable PyExpression afterThis,
        PyExpression toInsert) {
        // TODO: #insertItemIntoList is probably buggy. In such case, fix it and get rid of this method
        PsiElement result = insertItemIntoList(list, afterThis, toInsert);
        LeafPsiElement[] leafs = PsiTreeUtil.getChildrenOfType(list, LeafPsiElement.class);
        if (leafs != null) {
            Deque<LeafPsiElement> commas = Queues.newArrayDeque(Collections2.filter(Arrays.asList(leafs), COMMAS_ONLY));
            if (!commas.isEmpty()) {
                LeafPsiElement lastComma = commas.getLast();
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
    public PsiElement insertItemIntoList(PyElement list, @Nullable PyExpression afterThis, PyExpression toInsert)
        throws IncorrectOperationException {
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
    public PyBinaryExpression createBinaryExpression(String operator,
            PyExpression leftOperand,
            PyExpression rightOperand) {
        PsiFile dummyFile = createDummyFile(LanguageLevel.getDefault(), "a " + operator + " b");
        PyExpressionStatement expressionStatement = (PyExpressionStatement)dummyFile.getFirstChild();
        PyBinaryExpression binExpr = (PyBinaryExpression)expressionStatement.getExpression();
        ASTNode binnode = binExpr.getNode();
        binnode.replaceChild(binExpr.getLeftExpression().getNode(), leftOperand.getNode().copyElement());
        binnode.replaceChild(binExpr.getRightExpression().getNode(), rightOperand.getNode().copyElement());
        return binExpr;
    }

    @Override
    public PyPattern createPatternFromText(LanguageLevel languageLevel, String text)
        throws IncorrectOperationException {
        String matchStatement = "match x:\n" +
                "    case C(" + text + "):\n" +
                "        pass ";
        int[] pathToAttrPattern = {0, 5, 2, 1, 1};
        return createFromText(languageLevel, PyPattern.class, matchStatement, pathToAttrPattern);
    }

    @Override
    public PyCallExpression createCallExpression(LanguageLevel langLevel, String functionName) {
        PsiFile dummyFile = createDummyFile(langLevel, functionName + "()");
        PsiElement child = dummyFile.getFirstChild();
        if (child != null) {
            PsiElement element = child.getFirstChild();
            if (element instanceof PyCallExpression) {
                return (PyCallExpression)element;
            }
        }
        throw new IllegalArgumentException("Invalid call expression text " + functionName);
    }

    @Override
    public PyImportElement createImportElement(LanguageLevel languageLevel, String name, @Nullable String alias) {
        String importStatement = "from foo import " + name + (alias != null ? " as " + alias : "");
        return createFromText(languageLevel, PyImportElement.class, importStatement, new int[]{0, 6});
    }

    @Override
    public PyFunction createProperty(LanguageLevel languageLevel,
            String propertyName,
            String fieldName,
            AccessDirection accessDirection) {
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

    static int[] PATH_PARAMETER = {0, 3, 1};

    @Override
    public PyNamedParameter createParameter(String name) {
        return createParameter(name, null, null, LanguageLevel.getDefault());
    }

    @Override
    public PyParameterList createParameterList(LanguageLevel languageLevel, String text) {
        return createFromText(languageLevel, PyParameterList.class, "def f" + text + ": pass", new int[]{0, 3});
    }

    @Override
    public PyArgumentList createArgumentList(LanguageLevel languageLevel, String text) {
        return createFromText(languageLevel, PyArgumentList.class, "f" + text, new int[]{0, 0, 1});
    }

    @Override
    public PyNamedParameter createParameter(String name, @Nullable String defaultValue, @Nullable String annotation,
            LanguageLevel languageLevel) {
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
    public PyDecoratorList createDecoratorList(String ... decoratorTexts) {
        assert decoratorTexts.length > 0;
        StringBuilder functionText = new StringBuilder();
        for (String decoText : decoratorTexts) {
            functionText.append(decoText).append("\n");
        }
        functionText.append("def foo():\n\tpass");
        PyFunction function = createFromText(LanguageLevel.getDefault(), PyFunction.class,
                functionText.toString());
        PyDecoratorList decoratorList = function.getDecoratorList();
        assert decoratorList != null;
        return decoratorList;
    }

    @Override
    public PsiElement createNewLine() {
        return createFromText(LanguageLevel.getDefault(), PsiWhiteSpace.class, " \n\n ");
    }

    @Override
    public PyFromImportStatement createFromImportStatement(LanguageLevel languageLevel, String qualifier,
            String name, @Nullable String alias, boolean lazy) {
        String asClause = StringUtil.isNotEmpty(alias) ? " as " + alias : "";
        String lazyPrefix = lazy ? "lazy " : "";
        String statement = lazyPrefix + "from " + qualifier + " import " + name + asClause;
        return createFromText(languageLevel, PyFromImportStatement.class, statement);
    }

    @Override
    public PyImportStatement createImportStatement(LanguageLevel languageLevel,
            String name,
            @Nullable String alias,
            boolean lazy) {
        String asClause = StringUtil.isNotEmpty(alias) ? " as " + alias : "";
        String lazyPrefix = lazy ? "lazy " : "";
        String statement = lazyPrefix + "import " + name + asClause;
        return createFromText(languageLevel, PyImportStatement.class, statement);
    }

    @Override
    public PyEllipsisLiteralExpression createEllipsis() {
        return createFromText(LanguageLevel.PYTHON30, PyEllipsisLiteralExpression.class, "...", new int[]{0, 0});
    }

    @Override
    public PySingleStarParameter createSingleStarParameter() {
        return createFromText(LanguageLevel.PYTHON30, PySingleStarParameter.class, "def foo(*): pass", new int[]{0, 3, 1});
    }

    @Override
    public PySlashParameter createSlashParameter() {
        return createFromText(LanguageLevel.PYTHON30, PySlashParameter.class, "def foo(/): pass", new int[]{0, 3, 1});
    }

    private static class CommasOnly extends NotNullPredicate<LeafPsiElement> {
        @Override
        protected boolean applyNotNull(LeafPsiElement input) {
            return input.getNode().getElementType().equals(PyTokenTypes.COMMA);
        }
    }
}
