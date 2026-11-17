// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import org.jspecify.annotations.Nullable;

public abstract class PyElementGenerator extends PyAstElementGenerator {
    @SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
    public static PyElementGenerator getInstance(Project project) {
        return (PyElementGenerator)PyAstElementGenerator.getInstance(project);
    }

    public PyElementGenerator(Project project) {
        super(project);
    }

    public abstract ASTNode createNameIdentifier(String name, LanguageLevel languageLevel);

    /**
     * str must have quotes around it and be fully escaped.
     */
    public abstract PyStringLiteralExpression createStringLiteralAlreadyEscaped(String str);


    /**
     * Creates a string literal, adding appropriate quotes, properly escaping characters inside.
     *
     * @param destination        where the literal is destined to; used to determine the encoding.
     * @param unescaped          the string
     * @param preferUTF8         try to use UTF8 (would use ascii if false)
     * @param preferDoubleQuotes try to use double/single quotes
     * @return a newly created literal
     */
    protected abstract PyStringLiteralExpression createStringLiteralFromString(@Nullable PsiFile destination,
                                                                               String unescaped,
                                                                               boolean preferUTF8, boolean preferDoubleQuotes);

    public abstract PyStringLiteralExpression createStringLiteralFromString(String unescaped, boolean preferDoubleQuotes);

    public abstract PyStringLiteralExpression createStringLiteralFromString(String unescaped);

    public abstract PyStringLiteralExpression createStringLiteral(StringLiteralExpression oldElement, String unescaped);

    public abstract PyListLiteralExpression createListLiteral();

    public abstract ASTNode createDot();

    public abstract PyBinaryExpression createBinaryExpression(String operator,
                                                              PyExpression leftOperand,
                                                              PyExpression rightOperand);

    @Override
    public PyExpression createExpressionFromText(LanguageLevel languageLevel, String text)
        throws IncorrectOperationException {
        return (PyExpression)super.createExpressionFromText(languageLevel, text);
    }

    public abstract PyPattern createPatternFromText(LanguageLevel languageLevel, String text)
        throws IncorrectOperationException;

    /**
     * Adds elements to list inserting required commas.
     * Method is like {@link #insertItemIntoList(PyElement, PyExpression, PyExpression)} but does not add unneeded commas.
     *
     * @param list      where to add
     * @param afterThis after which element it should be added (null for add to the head)
     * @param toInsert  what to insert
     * @return newly inserted element
     */
    public abstract PsiElement insertItemIntoListRemoveRedundantCommas(
        PyElement list,
        @Nullable PyExpression afterThis,
        PyExpression toInsert);

    public abstract PsiElement insertItemIntoList(PyElement list, @Nullable PyExpression afterThis, PyExpression toInsert)
        throws IncorrectOperationException;

    public abstract PyCallExpression createCallExpression(LanguageLevel langLevel, String functionName);

    public abstract PyImportElement createImportElement(LanguageLevel languageLevel, String name, @Nullable String alias);

    public abstract PyFunction createProperty(LanguageLevel languageLevel,
                                              String propertyName,
                                              String fieldName,
                                              AccessDirection accessDirection);

    public abstract PyNamedParameter createParameter(String name, @Nullable String defaultValue, @Nullable String annotation,
                                                     LanguageLevel level);

    public abstract PyNamedParameter createParameter(String name);

    /**
     * @param text parameters list already in parentheses, e.g. {@code (foo, *args, **kwargs)}.
     */
    public abstract PyParameterList createParameterList(LanguageLevel languageLevel, String text);

    /**
     * @param text argument list already in parentheses, e.g. {@code (1, 2, *xs)}.
     */
    public abstract PyArgumentList createArgumentList(LanguageLevel languageLevel, String text);

    public abstract PyKeywordArgument createKeywordArgument(LanguageLevel languageLevel, String keyword, String value);

    @Override
    public PyExpressionStatement createDocstring(String content) {
        return (PyExpressionStatement)super.createDocstring(content);
    }

    @Override
    public PyPassStatement createPassStatement() {
        return (PyPassStatement)super.createPassStatement();
    }

    public abstract PyDecoratorList createDecoratorList(String... decoratorTexts);

    /**
     * Creates new line whitespace
     */
    public abstract PsiElement createNewLine();

    /**
     * Creates import statement of form {@code from qualifier import name as alias}.
     *
     * @param languageLevel language level for created element
     * @param qualifier     from where {@code name} will be imported (module name)
     * @param name          text of the reference in import element
     * @param alias         optional alias for {@code as alias} part
     * @return created {@link PyFromImportStatement}
     */
    public PyFromImportStatement createFromImportStatement(LanguageLevel languageLevel,
                                                           String qualifier,
                                                           String name,
                                                           @Nullable String alias) {
        return createFromImportStatement(languageLevel, qualifier, name, alias, false);
    }

    /**
     * Same as {@link #createFromImportStatement(LanguageLevel, String, String, String)} but with an optional
     * {@code lazy} prefix (PEP 810).
     */
    public abstract PyFromImportStatement createFromImportStatement(LanguageLevel languageLevel,
                                                                    String qualifier,
                                                                    String name,
                                                                    @Nullable String alias,
                                                                    boolean lazy);

    /**
     * Creates import statement of form {@code import name as alias}.
     *
     * @param languageLevel language level for created element
     * @param name          text of the reference in import element (module name)
     * @param alias         optional alias for {@code as alias} part
     * @return created {@link PyImportStatement}
     * @deprecated use #createImportStatement(com.jetbrains.python.psi.LanguageLevel, java.lang.String, java.lang.String, boolean)
     */
    @Deprecated
    public PyImportStatement createImportStatement(LanguageLevel languageLevel,
                                                   String name,
                                                   @Nullable String alias) {
        return createImportStatement(languageLevel, name, alias, false);
    }

    /**
     * Same as {@link #createImportStatement(LanguageLevel, String, String)} but with an optional
     * {@code lazy} prefix (PEP 810).
     */
    public abstract PyImportStatement createImportStatement(LanguageLevel languageLevel,
                                                            String name,
                                                            @Nullable String alias,
                                                            boolean lazy);

    public abstract PyEllipsisLiteralExpression createEllipsis();

    public abstract PySingleStarParameter createSingleStarParameter();

    public abstract PySlashParameter createSlashParameter();
}
