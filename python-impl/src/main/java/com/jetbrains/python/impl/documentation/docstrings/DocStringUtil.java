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
package com.jetbrains.python.impl.documentation.docstrings;

import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.documentation.PyDocumentationSettings;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.impl.psi.impl.PyStringLiteralExpressionImpl;
import com.jetbrains.python.toolbox.Substring;
import consulo.document.util.TextRange;
import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.ModuleUtilCore;
import consulo.module.Module;
import consulo.module.ModuleManager;
import consulo.ui.ex.awt.Messages;
import consulo.util.collection.ArrayUtil;
import consulo.util.lang.StringUtil;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.List;

/**
 * User: catherine
 */
public class DocStringUtil {
  private DocStringUtil() {
  }

  @Nullable
  public static String getDocStringValue(@Nonnull PyDocStringOwner owner) {
    return PyPsiUtils.strValue(owner.getDocStringExpression());
  }

  /**
   * Attempts to detect docstring format from given text and parses it into corresponding structured docstring.
   * It's recommended to use more reliable {@link #parse(String, PsiElement)} that fallbacks to format specified in settings.
   *
   * @param text docstring text <em>with both quotes and string prefix stripped</em>
   * @return structured docstring for one of supported formats or instance of {@link PlainDocString} if none was recognized.
   * @see #parse(String, PsiElement)
   */
  @Nonnull
  public static StructuredDocString parse(@Nonnull String text) {
    return parse(text, null);
  }

  /**
   * Attempts to detects docstring format first from given text, next from settings and parses text into corresponding structured docstring.
   *
   * @param text   docstring text <em>with both quotes and string prefix stripped</em>
   * @param anchor PSI element that will be used to retrieve docstring format from the containing file or the project module
   * @return structured docstring for one of supported formats or instance of {@link PlainDocString} if none was recognized.
   * @see DocStringFormat#ALL_NAMES_BUT_PLAIN
   * @see #guessDocStringFormat(String, PsiElement)
   */
  @Nonnull
  public static StructuredDocString parse(@Nonnull String text, @Nullable PsiElement anchor) {
    DocStringFormat format = guessDocStringFormat(text, anchor);
    return parseDocStringContent(format, text);
  }

  /**
   * Attempts to detects docstring format first from the text of given string node, next from settings using given expression as an anchor
   * and parses text into corresponding structured docstring.
   *
   * @param stringLiteral supposedly result of {@link PyDocStringOwner#getDocStringExpression()}
   * @return structured docstring for one of supported formats or instance of {@link PlainDocString} if none was recognized.
   */
  @Nonnull
  public static StructuredDocString parseDocString(@Nonnull PyStringLiteralExpression stringLiteral) {
    return parseDocString(guessDocStringFormat(stringLiteral.getStringValue(), stringLiteral), stringLiteral);
  }

  @Nonnull
  public static StructuredDocString parseDocString(@Nonnull DocStringFormat format, @Nonnull PyStringLiteralExpression stringLiteral) {
    return parseDocString(format, stringLiteral.getStringNodes().get(0));
  }

  @Nonnull
  public static StructuredDocString parseDocString(@Nonnull DocStringFormat format, @Nonnull ASTNode node) {
    //Preconditions.checkArgument(node.getElementType() == PyTokenTypes.DOCSTRING);
    return parseDocString(format, node.getText());
  }

  /**
   * @param stringText docstring text with possible string prefix and quotes
   */
  @Nonnull
  public static StructuredDocString parseDocString(@Nonnull DocStringFormat format, @Nonnull String stringText) {
    return parseDocString(format, stripPrefixAndQuotes(stringText));
  }

  /**
   * @param stringContent docstring text without string prefix and quotes, but not escaped, otherwise ranges of {@link Substring} returned
   *                      from {@link StructuredDocString} may be invalid
   */
  @Nonnull
  public static StructuredDocString parseDocStringContent(@Nonnull DocStringFormat format, @Nonnull String stringContent) {
    return parseDocString(format, new Substring(stringContent));
  }

  @Nonnull
  public static StructuredDocString parseDocString(@Nonnull DocStringFormat format, @Nonnull Substring content) {
    switch (format) {
      case REST:
        return new SphinxDocString(content);
      case EPYTEXT:
        return new EpydocString(content);
      case GOOGLE:
        return new GoogleCodeStyleDocString(content);
      case NUMPY:
        return new NumpyDocString(content);
      default:
        return new PlainDocString(content);
    }
  }

  @Nonnull
  private static Substring stripPrefixAndQuotes(@Nonnull String text) {
    TextRange contentRange = PyStringLiteralExpressionImpl.getNodeTextRange(text);
    return new Substring(text, contentRange.getStartOffset(), contentRange.getEndOffset());
  }

  /**
   * @return docstring format inferred heuristically solely from its content. For more reliable result use anchored version
   * {@link #guessDocStringFormat(String, PsiElement)} of this method.
   * @see #guessDocStringFormat(String, PsiElement)
   */
  @Nonnull
  public static DocStringFormat guessDocStringFormat(@Nonnull String text) {
    if (isLikeEpydocDocString(text)) {
      return DocStringFormat.EPYTEXT;
    }
    if (isLikeSphinxDocString(text)) {
      return DocStringFormat.REST;
    }
    if (isLikeNumpyDocstring(text)) {
      return DocStringFormat.NUMPY;
    }
    if (isLikeGoogleDocString(text)) {
      return DocStringFormat.GOOGLE;
    }
    return DocStringFormat.PLAIN;
  }

  /**
   * @param text   docstring text <em>with both quotes and string prefix stripped</em>
   * @param anchor PSI element that will be used to retrieve docstring format from the containing file or the project module
   * @return docstring inferred heuristically and if unsuccessful fallback to configured format retrieved from anchor PSI element
   * @see #getConfiguredDocStringFormat(PsiElement)
   */
  @Nonnull
  public static DocStringFormat guessDocStringFormat(@Nonnull String text, @Nullable PsiElement anchor) {
    DocStringFormat guessed = guessDocStringFormat(text);
    return guessed == DocStringFormat.PLAIN && anchor != null ? getConfiguredDocStringFormat(anchor) : guessed;
  }

  /**
   * @param anchor PSI element that will be used to retrieve docstring format from the containing file or the project module
   * @return docstring format configured for file or module containing given anchor PSI element
   * @see PyDocumentationSettings#getFormatForFile(PsiFile)
   */
  @Nonnull
  public static DocStringFormat getConfiguredDocStringFormat(@Nonnull PsiElement anchor) {
    PyDocumentationSettings settings = PyDocumentationSettings.getInstance(getModuleForElement(anchor));
    return settings.getFormatForFile(anchor.getContainingFile());
  }

  public static boolean isLikeSphinxDocString(@Nonnull String text) {
    return text.contains(":param ") ||
      text.contains(":return:") || text.contains(":returns:") ||
      text.contains(":rtype") || text.contains(":type");
  }

  public static boolean isLikeEpydocDocString(@Nonnull String text) {
    return text.contains("@param ") || text.contains("@return:") || text.contains("@rtype") || text.contains("@type");
  }

  public static boolean isLikeGoogleDocString(@Nonnull String text) {
    for (@NonNls String title : StringUtil.findMatches(text, GoogleCodeStyleDocString.SECTION_HEADER, 1)) {
      if (SectionBasedDocString.isValidSectionTitle(title)) {
        return true;
      }
    }
    return false;
  }

  public static boolean isLikeNumpyDocstring(@Nonnull String text) {
    String[] lines = StringUtil.splitByLines(text, false);
    for (int i = 0; i < lines.length; i++) {
      String line = lines[i];
      if (NumpyDocString.SECTION_HEADER.matcher(line).matches() && i > 0) {
        @NonNls String lineBefore = lines[i - 1];
        if (SectionBasedDocString.SECTION_NAMES.contains(lineBefore.trim().toLowerCase())) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Looks for a doc string under given parent.
   *
   * @param parent where to look. For classes and functions, this would be PyStatementList, for modules, PyFile.
   * @return the defining expression, or null.
   */
  @Nullable
  public static PyStringLiteralExpression findDocStringExpression(@Nullable PyElement parent) {
    if (parent != null) {
      PsiElement seeker = PyPsiUtils.getNextNonCommentSibling(parent.getFirstChild(), false);
      if (seeker instanceof PyExpressionStatement) {
        seeker = PyPsiUtils.getNextNonCommentSibling(seeker.getFirstChild(), false);
      }
      if (seeker instanceof PyStringLiteralExpression) {
        return (PyStringLiteralExpression)seeker;
      }
    }
    return null;
  }

  @Nullable
  public static StructuredDocString getStructuredDocString(@Nonnull PyDocStringOwner owner) {
    String value = owner.getDocStringValue();
    return value == null ? null : parse(value, owner);
  }

  /**
   * Returns containing docstring expression of class definition, function definition or module.
   * Useful to test whether particular PSI element is or belongs to such docstring.
   */
  @Nullable
  public static PyStringLiteralExpression getParentDefinitionDocString(@Nonnull PsiElement element) {
    PyDocStringOwner docStringOwner = PsiTreeUtil.getParentOfType(element, PyDocStringOwner.class);
    if (docStringOwner != null) {
      PyStringLiteralExpression docString = docStringOwner.getDocStringExpression();
      if (PsiTreeUtil.isAncestor(docString, element, false)) {
        return docString;
      }
    }
    return null;
  }

  public static boolean isDocStringExpression(@Nonnull PyExpression expression) {
    if (getParentDefinitionDocString(expression) == expression) {
      return true;
    }
    if (expression instanceof PyStringLiteralExpression) {
      return isVariableDocString((PyStringLiteralExpression)expression);
    }
    return false;
  }

  @Nullable
  public static String getAttributeDocComment(@Nonnull PyTargetExpression attr) {
    if (attr.getParent() instanceof PyAssignmentStatement) {
      PyAssignmentStatement assignment = (PyAssignmentStatement)attr.getParent();
      PsiElement prevSibling = PyPsiUtils.getPrevNonWhitespaceSibling(assignment);
      if (prevSibling instanceof PsiComment && prevSibling.getText().startsWith("#:")) {
        return prevSibling.getText().substring(2);
      }
    }
    return null;
  }

  public static boolean isVariableDocString(@Nonnull PyStringLiteralExpression expr) {
    PsiElement parent = expr.getParent();
    if (!(parent instanceof PyExpressionStatement)) {
      return false;
    }
    PsiElement prevElement = PyPsiUtils.getPrevNonCommentSibling(parent, true);
    if (prevElement instanceof PyAssignmentStatement) {
      if (expr.getText().contains("type:")) {
        return true;
      }

      PyAssignmentStatement assignmentStatement = (PyAssignmentStatement)prevElement;
      ScopeOwner scope = PsiTreeUtil.getParentOfType(prevElement, ScopeOwner.class);
      if (scope instanceof PyClass || scope instanceof PyFile) {
        return true;
      }
      if (scope instanceof PyFunction) {
        for (PyExpression target : assignmentStatement.getTargets()) {
          if (PyUtil.isInstanceAttribute(target)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  /**
   * Checks that docstring format is set either via element module's {@link com.jetbrains.python.PyNames.DOCFORMAT} attribute or
   * in module settings. If none of them applies, show standard choose dialog, asking user to pick one and updates module settings
   * accordingly.
   *
   * @param anchor PSI element that will be used to locate containing file and project module
   * @return false if no structured docstring format was specified initially and user didn't select any, true otherwise
   */
  public static boolean ensureNotPlainDocstringFormat(@Nonnull PsiElement anchor) {
    return ensureNotPlainDocstringFormatForFile(anchor.getContainingFile(), getModuleForElement(anchor));
  }

  @Nonnull
  private static Module getModuleForElement(@Nonnull PsiElement element) {
    Module module = ModuleUtilCore.findModuleForPsiElement(element);
    if (module == null) {
      module = ModuleManager.getInstance(element.getProject()).getModules()[0];
    }
    return module;
  }

  private static boolean ensureNotPlainDocstringFormatForFile(@Nonnull PsiFile file, @Nonnull Module module) {
    PyDocumentationSettings settings = PyDocumentationSettings.getInstance(module);
    if (settings.isPlain(file)) {
      List<String> values = DocStringFormat.ALL_NAMES_BUT_PLAIN;
      int i =
        Messages.showChooseDialog("Docstring format:", "Select Docstring Type", ArrayUtil.toStringArray(values), values.get(0), null);
      if (i < 0) {
        return false;
      }
      settings.setFormat(DocStringFormat.fromNameOrPlain(values.get(i)));
    }
    return true;
  }
}
