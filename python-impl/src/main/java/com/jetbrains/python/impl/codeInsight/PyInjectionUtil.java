/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package com.jetbrains.python.impl.codeInsight;

import consulo.language.inject.MultiHostRegistrar;
import consulo.document.util.TextRange;
import consulo.language.psi.PsiElement;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.List;

import static com.jetbrains.python.impl.inspections.PyStringFormatParser.*;

/**
 * @author vlan
 */
public class PyInjectionUtil {
  private PyInjectionUtil() {}

  /**
   * Returns true if the element is the largest expression that represents a string literal, possibly with concatenation, parentheses,
   * or formatting.
   */
  public static boolean isLargestStringLiteral(@Nonnull PsiElement element) {
    PsiElement parent = element.getParent();
    return isStringLiteralPart(element) && (parent == null || !isStringLiteralPart(parent));
  }

  /**
   * Registers language injections in the given registrar for the specified string literal element or its ancestor that contains
   * string concatenations or formatting.
   */
  public static void registerStringLiteralInjection(@Nonnull PsiElement element, @Nonnull MultiHostRegistrar registrar) {
    processStringLiteral(element, registrar, "", "", Formatting.NONE);
  }

  private static boolean isStringLiteralPart(@Nonnull PsiElement element) {
    if (element instanceof PyStringLiteralExpression) {
      return true;
    }
    else if (element instanceof PyParenthesizedExpression) {
      PyExpression contained = ((PyParenthesizedExpression)element).getContainedExpression();
      return contained != null && isStringLiteralPart(contained);
    }
    else if (element instanceof PyBinaryExpression) {
      PyBinaryExpression expr = (PyBinaryExpression)element;
      PyExpression left = expr.getLeftExpression();
      PyExpression right = expr.getRightExpression();
      return (expr.isOperator("+") && (isStringLiteralPart(left) || right != null && isStringLiteralPart(right))) ||
              expr.isOperator("%") && isStringLiteralPart(left);
    }
    else if (element instanceof PyCallExpression) {
      PyExpression qualifier = getFormatCallQualifier((PyCallExpression)element);
      return qualifier != null && isStringLiteralPart(qualifier);
    }
    return false;
  }

  @Nullable
  private static PyExpression getFormatCallQualifier(@Nonnull PyCallExpression element) {
    PyExpression callee = element.getCallee();
    if (callee instanceof PyQualifiedExpression) {
      PyQualifiedExpression qualifiedExpr = (PyQualifiedExpression)callee;
      PyExpression qualifier = qualifiedExpr.getQualifier();
      if (qualifier != null && PyNames.FORMAT.equals(qualifiedExpr.getReferencedName())) {
        return qualifier;
      }
    }
    return null;
  }

  private static void processStringLiteral(@Nonnull PsiElement element, @Nonnull MultiHostRegistrar registrar, @Nonnull String prefix,
                                           @Nonnull String suffix, @Nonnull Formatting formatting) {
    String missingValue = "missing";
    if (element instanceof PyStringLiteralExpression) {
      PyStringLiteralExpression expr = (PyStringLiteralExpression)element;
      List<TextRange> ranges = expr.getStringValueTextRanges();
      String text = expr.getText();
      for (TextRange range : ranges) {
        if (formatting != Formatting.NONE) {
          String part = range.substring(text);
          List<FormatStringChunk> chunks = formatting == Formatting.NEW_STYLE ? parseNewStyleFormat(part) : parsePercentFormat(part);
          for (int i = 0; i < chunks.size(); i++) {
            FormatStringChunk chunk = chunks.get(i);
            if (chunk instanceof ConstantChunk) {
              int nextIndex = i + 1;
              String chunkPrefix = i == 1 && chunks.get(0) instanceof SubstitutionChunk ? missingValue : "";
              String chunkSuffix = nextIndex < chunks.size() &&
                                         chunks.get(nextIndex) instanceof SubstitutionChunk ? missingValue : "";
              TextRange chunkRange = chunk.getTextRange().shiftRight(range.getStartOffset());
              registrar.addPlace(chunkPrefix, chunkSuffix, expr, chunkRange);
            }
          }
        }
        else {
          registrar.addPlace(prefix, suffix, expr, range);
        }
      }
    }
    else if (element instanceof PyParenthesizedExpression) {
      PyExpression contained = ((PyParenthesizedExpression)element).getContainedExpression();
      if (contained != null) {
        processStringLiteral(contained, registrar, prefix, suffix, formatting);
      }
    }
    else if (element instanceof PyBinaryExpression) {
      PyBinaryExpression expr = (PyBinaryExpression)element;
      PyExpression left = expr.getLeftExpression();
      PyExpression right = expr.getRightExpression();
      boolean isLeftString = isStringLiteralPart(left);
      if (expr.isOperator("+")) {
        boolean isRightString = right != null && isStringLiteralPart(right);
        if (isLeftString) {
          processStringLiteral(left, registrar, prefix, isRightString ? "" : missingValue, formatting);
        }
        if (isRightString) {
          processStringLiteral(right, registrar, isLeftString ? "" : missingValue, suffix, formatting);
        }
      }
      else if (expr.isOperator("%")) {
        processStringLiteral(left, registrar, prefix, suffix, Formatting.PERCENT);
      }
    }
    else if (element instanceof PyCallExpression) {
      PyExpression qualifier = getFormatCallQualifier((PyCallExpression)element);
      if (qualifier != null) {
        processStringLiteral(qualifier, registrar, prefix, suffix, Formatting.NEW_STYLE);
      }
    }
  }

  private enum Formatting {
    NONE,
    PERCENT,
    NEW_STYLE
  }
}
