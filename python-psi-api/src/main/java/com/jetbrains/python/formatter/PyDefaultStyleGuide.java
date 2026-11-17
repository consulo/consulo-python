// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.formatter;

import com.jetbrains.python.PythonLanguage;
import consulo.language.codeStyle.CodeStyleSettings;
import consulo.language.codeStyle.CommonCodeStyleSettings;
import consulo.localize.LocalizeValue;
import consulo.python.psi.localize.PySyntaxCoreLocalize;


/**
 * Industry-standard (PEP 8 / Black / Ruff aligned) Python code style defaults.
 *
 * This is the "default" profile introduced by PY-85946. Collections, argument and parameter lists
 * are chopped down one item per line when they don't fit, a magic trailing comma is added, the
 * closing bracket goes on its own (dedented) line, items are no longer aligned to the opening
 * bracket, and parameter lists use a normal (4-space) indent relying on the dedented closing
 * bracket for disambiguation (see {@link PyBlock}).
 *
 * @see PyClassicStyleGuide for the legacy behavior.
 */
public final class PyDefaultStyleGuide {
  public static final String CODE_STYLE_ID = "PY_DEFAULT";

  private PyDefaultStyleGuide() {
  }

  public static LocalizeValue getCODE_STYLE_TITLE() {
    return PySyntaxCoreLocalize.pythonCodeStyleDefaultTitle();
  }

  public static void apply(CodeStyleSettings settings) {
    applyToPyCustomSettings(settings.getCustomSettings(PyCodeStyleSettings.class));
    applyToCommonSettings(settings.getCommonSettings(PythonLanguage.getInstance()));
  }

  public static void applyToPyCustomSettings(PyCodeStyleSettings settings) {
    applyToPyCustomSettings(settings, true);
  }

  public static void applyToPyCustomSettings(PyCodeStyleSettings settings, boolean modifyCodeStyle) {
    if (modifyCodeStyle) {
      settings.CODE_STYLE_PROFILE = CODE_STYLE_ID;
    }

    // Chop down if long.
    settings.LIST_WRAPPING = PyCodeStyleSettings.CHOP_DOWN_IF_LONG;
    settings.SET_WRAPPING = PyCodeStyleSettings.CHOP_DOWN_IF_LONG;
    settings.TUPLE_WRAPPING = PyCodeStyleSettings.CHOP_DOWN_IF_LONG;
    settings.DICT_WRAPPING = PyCodeStyleSettings.CHOP_DOWN_IF_LONG;
    settings.FROM_IMPORT_WRAPPING = PyCodeStyleSettings.CHOP_DOWN_IF_LONG;

    // New line after the opening parenthesis and a dedented closing bracket when split.
    settings.LIST_NEW_LINE_AFTER_LEFT_BRACKET = true;
    settings.LIST_NEW_LINE_BEFORE_RIGHT_BRACKET = true;
    settings.SET_NEW_LINE_AFTER_LEFT_BRACE = true;
    settings.SET_NEW_LINE_BEFORE_RIGHT_BRACE = true;
    settings.TUPLE_NEW_LINE_AFTER_LEFT_PARENTHESIS = true;
    settings.TUPLE_NEW_LINE_BEFORE_RIGHT_PARENTHESIS = true;
    settings.DICT_NEW_LINE_AFTER_LEFT_BRACE = true;
    settings.DICT_NEW_LINE_BEFORE_RIGHT_BRACE = true;
    settings.FROM_IMPORT_NEW_LINE_AFTER_LEFT_PARENTHESIS = true;
    settings.FROM_IMPORT_NEW_LINE_BEFORE_RIGHT_PARENTHESIS = true;

    // Force a new line after the colon of single-clause compound statements.
    settings.NEW_LINE_AFTER_COLON = true;

    // Magic trailing comma.
    settings.USE_TRAILING_COMMA_IN_COLLECTIONS = true;
    settings.USE_TRAILING_COMMA_IN_PARAMETER_LIST = true;
    settings.USE_TRAILING_COMMA_IN_ARGUMENTS_LIST = true;
    settings.FROM_IMPORT_TRAILING_COMMA_IF_MULTILINE = true;
    settings.FROM_IMPORT_PARENTHESES_FORCE_IF_MULTILINE = true;

    // No alignment to the opening bracket.
    settings.ALIGN_COLLECTIONS_AND_COMPREHENSIONS = false;
    settings.ALIGN_MULTILINE_IMPORTS = false;

    // Normal (4-space) indent for parameters
    settings.USE_CONTINUATION_INDENT_FOR_PARAMETERS = false;
    settings.USE_CONTINUATION_INDENT_FOR_ARGUMENTS = false;
    settings.USE_CONTINUATION_INDENT_FOR_COLLECTION_AND_COMPREHENSIONS = false;

    settings.HANG_CLOSING_BRACKETS = false;
    settings.DICT_ALIGNMENT = PyCodeStyleSettings.DICT_ALIGNMENT_NONE;
  }

  public static void applyToCommonSettings(CommonCodeStyleSettings settings) {
    applyToCommonSettings(settings, true);
  }

  public static void applyToCommonSettings(CommonCodeStyleSettings settings, boolean modifyCodeStyle) {
    settings.RIGHT_MARGIN = 88;
    // Visual guides (soft margins) at the same column as the hard wrap.
    // TODO consulo: CommonCodeStyleSettings#setSoftMargins is not public
    // settings.setSoftMargins(List.of(88));
    // Ensure right margin is not exceeded.
    settings.WRAP_LONG_LINES = true;

    settings.CALL_PARAMETERS_WRAP = PyCodeStyleSettings.CHOP_DOWN_IF_LONG;
    settings.CALL_PARAMETERS_LPAREN_ON_NEXT_LINE = true;
    settings.CALL_PARAMETERS_RPAREN_ON_NEXT_LINE = true;

    settings.METHOD_PARAMETERS_WRAP = PyCodeStyleSettings.CHOP_DOWN_IF_LONG;
    settings.METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE = true;
    settings.METHOD_PARAMETERS_RPAREN_ON_NEXT_LINE = true;

    settings.ALIGN_MULTILINE_PARAMETERS = false;
    settings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = false;

    if (modifyCodeStyle && settings instanceof PyCommonCodeStyleSettings pySettings) {
      pySettings.CODE_STYLE_PROFILE = CODE_STYLE_ID;
    }
  }
}
