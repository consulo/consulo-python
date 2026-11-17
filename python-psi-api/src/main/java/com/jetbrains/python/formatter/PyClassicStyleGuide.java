// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.formatter;

import com.jetbrains.python.PythonLanguage;
import consulo.language.codeStyle.CodeStyleSettings;
import consulo.language.codeStyle.CommonCodeStyleSettings;
import consulo.localize.LocalizeValue;
import consulo.python.psi.localize.PySyntaxCoreLocalize;

/**
 * The legacy ("classic") Python code style defaults — exactly the behavior that shipped before
 * PY-85946. Every field is set to its historical default so that projects pinned to this profile
 * serialize no extra diffs and format identically to older IDE versions.
 *
 * @see PyDefaultStyleGuide for the new industry-standard defaults.
 */
public final class PyClassicStyleGuide {
  public static final String CODE_STYLE_ID = "PY_CLASSIC_DEFAULTS";

  private PyClassicStyleGuide() {
  }

  public static LocalizeValue getCODE_STYLE_TITLE() {
    return PySyntaxCoreLocalize.pythonCodeStyleClassicTitle();
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

    settings.LIST_WRAPPING = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    settings.SET_WRAPPING = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    settings.TUPLE_WRAPPING = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    settings.DICT_WRAPPING = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    settings.FROM_IMPORT_WRAPPING = CommonCodeStyleSettings.WRAP_AS_NEEDED;

    settings.LIST_NEW_LINE_AFTER_LEFT_BRACKET = false;
    settings.LIST_NEW_LINE_BEFORE_RIGHT_BRACKET = false;
    settings.SET_NEW_LINE_AFTER_LEFT_BRACE = false;
    settings.SET_NEW_LINE_BEFORE_RIGHT_BRACE = false;
    settings.TUPLE_NEW_LINE_AFTER_LEFT_PARENTHESIS = false;
    settings.TUPLE_NEW_LINE_BEFORE_RIGHT_PARENTHESIS = false;
    settings.DICT_NEW_LINE_AFTER_LEFT_BRACE = false;
    settings.DICT_NEW_LINE_BEFORE_RIGHT_BRACE = false;
    settings.FROM_IMPORT_NEW_LINE_AFTER_LEFT_PARENTHESIS = false;
    settings.FROM_IMPORT_NEW_LINE_BEFORE_RIGHT_PARENTHESIS = false;

    settings.USE_TRAILING_COMMA_IN_COLLECTIONS = false;
    settings.USE_TRAILING_COMMA_IN_PARAMETER_LIST = false;
    settings.USE_TRAILING_COMMA_IN_ARGUMENTS_LIST = false;
    settings.FROM_IMPORT_TRAILING_COMMA_IF_MULTILINE = false;
    settings.FROM_IMPORT_PARENTHESES_FORCE_IF_MULTILINE = false;

    settings.ALIGN_COLLECTIONS_AND_COMPREHENSIONS = true;
    settings.ALIGN_MULTILINE_IMPORTS = true;

    settings.USE_CONTINUATION_INDENT_FOR_PARAMETERS = true;
    settings.USE_CONTINUATION_INDENT_FOR_ARGUMENTS = false;
    settings.USE_CONTINUATION_INDENT_FOR_COLLECTION_AND_COMPREHENSIONS = false;

    settings.NEW_LINE_AFTER_COLON = false;

    settings.HANG_CLOSING_BRACKETS = false;
    settings.DICT_ALIGNMENT = PyCodeStyleSettings.DICT_ALIGNMENT_NONE;
  }

  public static void applyToCommonSettings(CommonCodeStyleSettings settings) {
    applyToCommonSettings(settings, true);
  }

  public static void applyToCommonSettings(CommonCodeStyleSettings settings, boolean modifyCodeStyle) {
    settings.RIGHT_MARGIN = -1;
    // TODO consulo: CommonCodeStyleSettings#setSoftMargins is not public
    // settings.setSoftMargins(List.of());
    settings.WRAP_LONG_LINES = false;

    settings.CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    settings.CALL_PARAMETERS_LPAREN_ON_NEXT_LINE = false;
    settings.CALL_PARAMETERS_RPAREN_ON_NEXT_LINE = false;

    settings.METHOD_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    settings.METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE = false;
    settings.METHOD_PARAMETERS_RPAREN_ON_NEXT_LINE = false;

    settings.ALIGN_MULTILINE_PARAMETERS = true;
    settings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;

    if (modifyCodeStyle && settings instanceof PyCommonCodeStyleSettings pySettings) {
      pySettings.CODE_STYLE_PROFILE = CODE_STYLE_ID;
    }
  }
}
