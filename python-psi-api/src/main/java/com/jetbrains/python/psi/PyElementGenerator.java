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
package com.jetbrains.python.psi;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.ide.ServiceManager;
import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

@ServiceAPI(ComponentScope.PROJECT)
public abstract class PyElementGenerator {
  public static PyElementGenerator getInstance(Project project) {
    return ServiceManager.getService(project, PyElementGenerator.class);
  }

  public abstract ASTNode createNameIdentifier(String name, LanguageLevel languageLevel);

  /**
   * str must have quotes around it and be fully escaped.
   */
  public abstract PyStringLiteralExpression createStringLiteralAlreadyEscaped(String str);


  /**
   * Creates a string literal, adding appropriate quotes, properly escaping characters inside.
   *
   * @param destination where the literal is destined to; used to determine the encoding.
   * @param unescaped   the string
   * @param preferUTF8  try to use UTF8 (would use ascii if false)
   * @return a newly created literal
   */
  public abstract PyStringLiteralExpression createStringLiteralFromString(@Nullable PsiFile destination,
                                                                          String unescaped,
                                                                          boolean preferUTF8);

  public abstract PyStringLiteralExpression createStringLiteralFromString(@Nonnull String unescaped);

  public abstract PyStringLiteralExpression createStringLiteral(@Nonnull PyStringLiteralExpression oldElement, @Nonnull String unescaped);

  public abstract PyListLiteralExpression createListLiteral();

  public abstract ASTNode createComma();

  public abstract ASTNode createDot();

  public abstract PyBinaryExpression createBinaryExpression(String s, PyExpression expr, PyExpression listLiteral);

  /**
   * @param text the text to create an expression from
   * @return the expression
   * @deprecated use the overload with language level specified
   */
  public abstract PyExpression createExpressionFromText(String text);

  public abstract PyExpression createExpressionFromText(final LanguageLevel languageLevel, String text);

  /**
   * Adds elements to list inserting required commas.
   * Method is like {@link #insertItemIntoList(PyElement, PyExpression, PyExpression)} but does not add unneeded commas.
   *
   * @param list      where to add
   * @param afterThis after which element it should be added (null for add to the head)
   * @param toInsert  what to insert
   * @return newly inserted element
   */
  @Nonnull
  public abstract PsiElement insertItemIntoListRemoveRedundantCommas(@Nonnull PyElement list,
                                                                     @Nullable PyExpression afterThis,
                                                                     @Nonnull PyExpression toInsert);

  public abstract PsiElement insertItemIntoList(PyElement list,
                                                @Nullable PyExpression afterThis,
                                                PyExpression toInsert) throws IncorrectOperationException;

  @Nonnull
  public abstract PyCallExpression createCallExpression(final LanguageLevel langLevel, String functionName);

  public abstract PyImportElement createImportElement(final LanguageLevel languageLevel, String name);

  public abstract PyFunction createProperty(final LanguageLevel languageLevel,
                                            String propertyName,
                                            String fieldName,
                                            AccessDirection accessDirection);

  @Nonnull
  public abstract <T> T createFromText(LanguageLevel langLevel, Class<T> aClass, final String text);

  @Nonnull
  public abstract <T> T createPhysicalFromText(LanguageLevel langLevel, Class<T> aClass, final String text);

  /**
   * Creates an arbitrary PSI element from text, by creating a bigger construction and then cutting the proper subelement.
   * Will produce all kinds of exceptions if the path or class would not match the PSI tree.
   *
   * @param langLevel the language level to use for parsing the text
   * @param aClass    class of the PSI element; may be an interface not descending from PsiElement, as long as target node can be cast to it
   * @param text      text to parse
   * @param path      a sequence of numbers, each telling which child to select at current tree level; 0 means first child, etc.
   * @return the newly created PSI element
   */
  @Nonnull
  public abstract <T> T createFromText(LanguageLevel langLevel, Class<T> aClass, final String text, final int[] path);

  public abstract PyNamedParameter createParameter(@Nonnull String name,
                                                   @Nullable String defaultValue,
                                                   @Nullable String annotation,
                                                   @Nonnull LanguageLevel level);

  public abstract PyNamedParameter createParameter(@Nonnull String name);

  /**
   * @param text parameters list already in parentheses, e.g. {@code (foo, *args, **kwargs)}.
   */
  @Nonnull
  public abstract PyParameterList createParameterList(@Nonnull LanguageLevel languageLevel, @Nonnull String text);

  /**
   * @param text argument list already in parentheses, e.g. {@code (1, 2, *xs)}.
   */
  @Nonnull
  public abstract PyArgumentList createArgumentList(@Nonnull LanguageLevel languageLevel, @Nonnull String text);

  public abstract PyKeywordArgument createKeywordArgument(LanguageLevel languageLevel, String keyword, String value);

  public abstract PsiFile createDummyFile(LanguageLevel langLevel, String contents);

  public abstract PyExpressionStatement createDocstring(String content);

  public abstract PyPassStatement createPassStatement();

  @Nonnull
  public abstract PyDecoratorList createDecoratorList(@Nonnull final String... decoratorTexts);

  /**
   * Creates new line whitespace
   */
  @Nonnull
  public abstract PsiElement createNewLine();

  /**
   * Creates import statement of form {@code from qualifier import name as alias}.
   *
   * @param languageLevel language level for created element
   * @param qualifier     from where {@code name} will be imported (module name)
   * @param name          text of the reference in import element
   * @param alias         optional alias for {@code as alias} part
   * @return created {@link com.jetbrains.python.psi.PyFromImportStatement}
   */
  @Nonnull
  public abstract PyFromImportStatement createFromImportStatement(@Nonnull LanguageLevel languageLevel,
                                                                  @Nonnull String qualifier,
                                                                  @Nonnull String name,
                                                                  @Nullable String alias);

  /**
   * Creates import statement of form {@code import name as alias}.
   *
   * @param languageLevel language level for created element
   * @param name          text of the reference in import element (module name)
   * @param alias         optional alias for {@code as alias} part
   * @return created {@link com.jetbrains.python.psi.PyImportStatement}
   */
  @Nonnull
  public abstract PyImportStatement createImportStatement(@Nonnull LanguageLevel languageLevel,
                                                          @Nonnull String name,
                                                          @Nullable String alias);
}
