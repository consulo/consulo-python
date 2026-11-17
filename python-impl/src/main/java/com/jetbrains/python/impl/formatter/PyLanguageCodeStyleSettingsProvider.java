// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.impl.formatter;

import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.formatter.PyCodeStyleDefaultsKt;
import com.jetbrains.python.formatter.PyCodeStyleSettings;
import com.jetbrains.python.formatter.PyCommonCodeStyleSettings;
import com.jetbrains.python.formatter.PyDefaultStyleGuide;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.codeStyle.CommonCodeStyleSettings;
import consulo.language.codeStyle.localize.CodeStyleLocalize;
import consulo.language.codeStyle.setting.CodeStyleSettingsCustomizable;
import consulo.language.codeStyle.setting.IndentOptionsEditor;
import consulo.language.codeStyle.setting.LanguageCodeStyleSettingsProvider;
import consulo.language.codeStyle.ui.setting.SmartIndentOptionsEditor;
import consulo.python.impl.localize.PySyntaxLocalize;

import static consulo.language.codeStyle.setting.CodeStyleSettingsCustomizable.*;

@ExtensionImpl
public final class PyLanguageCodeStyleSettingsProvider extends LanguageCodeStyleSettingsProvider {
    @Override
    public Language getLanguage() {
        return PythonLanguage.getInstance();
    }

    @Override
    public String getCodeSample(SettingsType settingsType) {
        if (settingsType == SettingsType.SPACING_SETTINGS) {
            return SPACING_SETTINGS_PREVIEW;
        }
        if (settingsType == SettingsType.BLANK_LINES_SETTINGS) {
            return BLANK_LINES_SETTINGS_PREVIEW;
        }
        if (settingsType == SettingsType.WRAPPING_AND_BRACES_SETTINGS) {
            return WRAP_SETTINGS_PREVIEW;
        }
        if (settingsType == SettingsType.INDENT_SETTINGS) {
            return INDENT_SETTINGS_PREVIEW;
        }
        return "";
    }

    @Override
    public void customizeSettings(CodeStyleSettingsCustomizable consumer, SettingsType settingsType) {
        if (settingsType == SettingsType.SPACING_SETTINGS) {
            consumer.showStandardOptions("SPACE_BEFORE_METHOD_CALL_PARENTHESES",
                                               "SPACE_BEFORE_METHOD_PARENTHESES",
                                               "SPACE_AROUND_ASSIGNMENT_OPERATORS",
                                               "SPACE_AROUND_EQUALITY_OPERATORS",
                                               "SPACE_AROUND_RELATIONAL_OPERATORS",
                                               "SPACE_AROUND_BITWISE_OPERATORS",
                                               "SPACE_AROUND_ADDITIVE_OPERATORS",
                                               "SPACE_AROUND_MULTIPLICATIVE_OPERATORS",
                                               "SPACE_AROUND_SHIFT_OPERATORS",
                                               "SPACE_WITHIN_METHOD_CALL_PARENTHESES",
                                               "SPACE_WITHIN_EMPTY_METHOD_CALL_PARENTHESES",
                                               "SPACE_WITHIN_METHOD_PARENTHESES",
                                               "SPACE_WITHIN_EMPTY_METHOD_PARENTHESES",
                                               "SPACE_WITHIN_BRACKETS",
                                               "SPACE_AFTER_COMMA",
                                               "SPACE_BEFORE_COMMA",
                                               "SPACE_BEFORE_SEMICOLON");
            consumer.showCustomOption(PyCodeStyleSettings.class, "SPACE_BEFORE_LBRACKET",
                                            PySyntaxLocalize.formatterLeftBracket().get(), SPACES_BEFORE_PARENTHESES);
            consumer.showCustomOption(PyCodeStyleSettings.class, "SPACE_AROUND_POWER_OPERATOR",
                                            PySyntaxLocalize.formatterAroundPowerOperator().get(), SPACES_AROUND_OPERATORS);
            consumer.showCustomOption(PyCodeStyleSettings.class, "SPACE_AROUND_EQ_IN_NAMED_PARAMETER",
                                            PySyntaxLocalize.formatterAroundEqInNamedParameter().get(), SPACES_AROUND_OPERATORS);
            consumer.showCustomOption(PyCodeStyleSettings.class, "SPACE_AROUND_EQ_IN_KEYWORD_ARGUMENT",
                                            PySyntaxLocalize.formatterAroundEqInKeywordArgument().get(), SPACES_AROUND_OPERATORS);
            consumer.showCustomOption(PyCodeStyleSettings.class, "SPACE_WITHIN_BRACES", PySyntaxLocalize.formatterBraces().get(),
                                            SPACES_WITHIN);
            consumer.showCustomOption(PyCodeStyleSettings.class, "SPACE_BEFORE_PY_COLON",
                                            CodeStyleLocalize.checkboxSpacesBeforeColon().get(), SPACES_OTHER);
            consumer.showCustomOption(PyCodeStyleSettings.class, "SPACE_AFTER_PY_COLON",
                                            CodeStyleLocalize.checkboxSpacesAfterColon().get(), SPACES_OTHER);
            consumer.showCustomOption(PyCodeStyleSettings.class, "SPACE_BEFORE_BACKSLASH",
                                            PySyntaxLocalize.formatterBeforeBackslash().get(), SPACES_OTHER);
            consumer.showCustomOption(PyCodeStyleSettings.class, "SPACE_BEFORE_NUMBER_SIGN",
                                            PySyntaxLocalize.formatterBeforeHash().get(), SPACES_OTHER);
            consumer.showCustomOption(PyCodeStyleSettings.class, "SPACE_AFTER_NUMBER_SIGN",
                                            PySyntaxLocalize.formatterAfterHash().get(), SPACES_OTHER);
            consumer.renameStandardOption("SPACE_AROUND_MULTIPLICATIVE_OPERATORS", PySyntaxLocalize.formatterAroundMultiplicativeOperators().get());
        }
        else if (settingsType == SettingsType.BLANK_LINES_SETTINGS) {
            consumer.showStandardOptions("BLANK_LINES_AROUND_CLASS",
                                               "BLANK_LINES_AROUND_METHOD",
                                               "BLANK_LINES_AFTER_IMPORTS",
                                               "KEEP_BLANK_LINES_IN_DECLARATIONS",
                                               "KEEP_BLANK_LINES_IN_CODE");
            consumer.renameStandardOption("BLANK_LINES_AFTER_IMPORTS", PySyntaxLocalize.formatterAroundTopLevelImports().get());
            consumer.renameStandardOption("BLANK_LINES_AROUND_CLASS", PySyntaxLocalize.formatterAroundNestedClasses().get());
            consumer.renameStandardOption("BLANK_LINES_AROUND_METHOD",
                                                PySyntaxLocalize.formatterAroundMethodsAndNestedFunctions().get());
            consumer.renameStandardOption("KEEP_BLANK_LINES_IN_DECLARATIONS", PySyntaxLocalize.formatterKeepAroundDeclarations().get());
            consumer.renameStandardOption("KEEP_BLANK_LINES_IN_CODE", PySyntaxLocalize.formatterKeepInRestOfCode().get());

            consumer.showCustomOption(PyCodeStyleSettings.class, "BLANK_LINES_AROUND_TOP_LEVEL_CLASSES_FUNCTIONS",
                                            PySyntaxLocalize.formatterAroundTopLevelClassesAndFunction().get(), BLANK_LINES);
            consumer.showCustomOption(PyCodeStyleSettings.class, "BLANK_LINES_AFTER_LOCAL_IMPORTS",
                                            PySyntaxLocalize.formatterAfterLocalImports().get(), BLANK_LINES);
            consumer.showCustomOption(PyCodeStyleSettings.class, "BLANK_LINES_BEFORE_FIRST_METHOD",
                                            PySyntaxLocalize.formatterBeforeFirstMethod().get(), BLANK_LINES);
        }
        else if (settingsType == SettingsType.WRAPPING_AND_BRACES_SETTINGS) {
            consumer.showStandardOptions("RIGHT_MARGIN",
                                               "WRAP_ON_TYPING",
                                               "KEEP_LINE_BREAKS",
                                               "WRAP_LONG_LINES",
                                               "CALL_PARAMETERS_WRAP",
                                               "CALL_PARAMETERS_LPAREN_ON_NEXT_LINE",
                                               "CALL_PARAMETERS_RPAREN_ON_NEXT_LINE",
                                               "ALIGN_MULTILINE_PARAMETERS",
                                               "METHOD_PARAMETERS_WRAP",
                                               "METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE",
                                               "METHOD_PARAMETERS_RPAREN_ON_NEXT_LINE",
                                               "ALIGN_MULTILINE_PARAMETERS_IN_CALLS");

            consumer.renameStandardOption("METHOD_PARAMETERS_WRAP", PySyntaxLocalize.formatterFunctionDeclarationParameters().get());
            consumer.renameStandardOption("CALL_PARAMETERS_WRAP", PySyntaxLocalize.formatterFunctionCallArguments().get());

            consumer.showCustomOption(PyCodeStyleSettings.class, "ALIGN_CONSECUTIVE_ASSIGNMENTS",
                                            PySyntaxLocalize.formatterAlignConsecutiveAssignments().get(),
                                            CodeStyleLocalize.wrappingAssignmentStatement().get());

            consumer.showCustomOption(PyCodeStyleSettings.class,
                                            "USE_TRAILING_COMMA_IN_ARGUMENTS_LIST",
                                            PySyntaxLocalize.formatterForceTrailingCommaIfMultiline().get(),
                                            CodeStyleLocalize.wrappingMethodArguments().get());

            consumer.showCustomOption(PyCodeStyleSettings.class,
                                            "USE_TRAILING_COMMA_IN_PARAMETER_LIST",
                                            PySyntaxLocalize.formatterForceTrailingCommaIfMultiline().get(),
                                            CodeStyleLocalize.wrappingMethodParameters().get());

            // "Use continuation indent" checkboxes
            consumer.showCustomOption(PyCodeStyleSettings.class, "USE_CONTINUATION_INDENT_FOR_PARAMETERS",
                                            PySyntaxLocalize.formatterUseContinuationIndent().get(),
                                            WRAPPING_METHOD_PARAMETERS);
            consumer.showCustomOption(PyCodeStyleSettings.class, "USE_CONTINUATION_INDENT_FOR_ARGUMENTS",
                                            PySyntaxLocalize.formatterUseContinuationIndent().get(),
                                            WRAPPING_METHOD_ARGUMENTS_WRAPPING);
            consumer.showCustomOption(PyCodeStyleSettings.class, "USE_CONTINUATION_INDENT_FOR_COLLECTION_AND_COMPREHENSIONS",
                                            PySyntaxLocalize.formatterUseContinuationIndent().get(),
                                            PySyntaxLocalize.formatterCollectionsAndComprehensions().get());


            consumer.showCustomOption(PyCodeStyleSettings.class, "NEW_LINE_AFTER_COLON",
                                            PySyntaxLocalize.formatterSingleClauseStatements().get(),
                                            PySyntaxLocalize.formatterForceNewLineAfterColon().get());
            consumer.showCustomOption(PyCodeStyleSettings.class, "NEW_LINE_AFTER_COLON_MULTI_CLAUSE",
                                            PySyntaxLocalize.formatterMultiClauseStatements().get(),
                                            PySyntaxLocalize.formatterForceNewLineAfterColon().get());
            consumer.showCustomOption(PyCodeStyleSettings.class, "ALIGN_COLLECTIONS_AND_COMPREHENSIONS",
                                            PySyntaxLocalize.formatterAlignWhenMultiline().get(),
                                            PySyntaxLocalize.formatterCollectionsAndComprehensions().get());
            consumer.showCustomOption(PyCodeStyleSettings.class,
                                            "USE_TRAILING_COMMA_IN_COLLECTIONS",
                                            PySyntaxLocalize.formatterForceTrailingCommaIfMultiline().get(),
                                            PySyntaxLocalize.formatterCollectionsAndComprehensions().get());

            consumer.showCustomOption(PyCodeStyleSettings.class, "FROM_IMPORT_WRAPPING",
                                            PySyntaxLocalize.formatterFromImportStatements().get(), null, WRAP_OPTIONS, WRAP_VALUES);
            consumer.showCustomOption(PyCodeStyleSettings.class, "ALIGN_MULTILINE_IMPORTS",
                                            PySyntaxLocalize.formatterAlignWhenMultiline().get(),
                                            PySyntaxLocalize.formatterFromImportStatements().get());
            consumer.showCustomOption(PyCodeStyleSettings.class, "FROM_IMPORT_NEW_LINE_AFTER_LEFT_PARENTHESIS",
                                            CodeStyleLocalize.wrappingNewLineAfterLpar().get(),
                                            PySyntaxLocalize.formatterFromImportStatements().get());
            consumer.showCustomOption(PyCodeStyleSettings.class, "FROM_IMPORT_NEW_LINE_BEFORE_RIGHT_PARENTHESIS",
                                            CodeStyleLocalize.wrappingRparOnNewLine().get(),
                                            PySyntaxLocalize.formatterFromImportStatements().get());
            consumer.showCustomOption(PyCodeStyleSettings.class, "FROM_IMPORT_PARENTHESES_FORCE_IF_MULTILINE",
                                            PySyntaxLocalize.formatterFromImportStatementsForceParenthesesIfMultiline().get(),
                                            PySyntaxLocalize.formatterFromImportStatements().get());
            consumer.showCustomOption(PyCodeStyleSettings.class, "FROM_IMPORT_TRAILING_COMMA_IF_MULTILINE",
                                            PySyntaxLocalize.formatterForceTrailingCommaIfMultiline().get(),
                                            PySyntaxLocalize.formatterFromImportStatements().get());

            consumer.showCustomOption(PyCodeStyleSettings.class, "LIST_WRAPPING",
                                            PySyntaxLocalize.formatterListLiterals().get(), null, WRAP_OPTIONS, WRAP_VALUES);
            consumer.showCustomOption(PyCodeStyleSettings.class, "LIST_NEW_LINE_AFTER_LEFT_BRACKET",
                                            PySyntaxLocalize.wrappingNewLineAfterLbracket().get(), PySyntaxLocalize.formatterListLiterals().get());
            consumer.showCustomOption(PyCodeStyleSettings.class, "LIST_NEW_LINE_BEFORE_RIGHT_BRACKET",
                                            PySyntaxLocalize.wrappingRbracketOnNewLine().get(), PySyntaxLocalize.formatterListLiterals().get());

            consumer.showCustomOption(PyCodeStyleSettings.class, "SET_WRAPPING",
                                            PySyntaxLocalize.formatterSetLiterals().get(), null, WRAP_OPTIONS, WRAP_VALUES);
            consumer.showCustomOption(PyCodeStyleSettings.class, "SET_NEW_LINE_AFTER_LEFT_BRACE",
                                            CodeStyleLocalize.wrappingNewLineAfterLbrace().get(), PySyntaxLocalize.formatterSetLiterals().get());
            consumer.showCustomOption(PyCodeStyleSettings.class, "SET_NEW_LINE_BEFORE_RIGHT_BRACE",
                                            CodeStyleLocalize.wrappingRbraceOnNewLine().get(), PySyntaxLocalize.formatterSetLiterals().get());

            consumer.showCustomOption(PyCodeStyleSettings.class, "TUPLE_WRAPPING",
                                            PySyntaxLocalize.formatterParenthesizedTuples().get(), null, WRAP_OPTIONS, WRAP_VALUES);
            consumer.showCustomOption(PyCodeStyleSettings.class, "TUPLE_NEW_LINE_AFTER_LEFT_PARENTHESIS",
                                            CodeStyleLocalize.wrappingNewLineAfterLpar().get(), PySyntaxLocalize.formatterParenthesizedTuples().get());
            consumer.showCustomOption(PyCodeStyleSettings.class, "TUPLE_NEW_LINE_BEFORE_RIGHT_PARENTHESIS",
                                            CodeStyleLocalize.wrappingRparOnNewLine().get(), PySyntaxLocalize.formatterParenthesizedTuples().get());


            consumer.showCustomOption(PyCodeStyleSettings.class, "DICT_WRAPPING",
                                            PySyntaxLocalize.formatterDictionaryLiterals().get(), null, WRAP_OPTIONS, WRAP_VALUES);
            consumer.showCustomOption(PyCodeStyleSettings.class, "DICT_NEW_LINE_AFTER_LEFT_BRACE",
                                            CodeStyleLocalize.wrappingNewLineAfterLbrace().get(),
                                            PySyntaxLocalize.formatterDictionaryLiterals().get());
            consumer.showCustomOption(PyCodeStyleSettings.class, "DICT_NEW_LINE_BEFORE_RIGHT_BRACE",
                                            CodeStyleLocalize.wrappingRbraceOnNewLine().get(),
                                            PySyntaxLocalize.formatterDictionaryLiterals().get());
            consumer
                .showCustomOption(PyCodeStyleSettings.class, "HANG_CLOSING_BRACKETS", PySyntaxLocalize.formatterHangClosingBrackets().get(), null);
        }
    }

    @Override
    public IndentOptionsEditor getIndentOptionsEditor() {
        return new SmartIndentOptionsEditor();
    }

    // TODO consulo: LanguageCodeStyleSettingsProvider#customizeDefaults missing
    protected void customizeDefaults(CommonCodeStyleSettings commonSettings,
                                     CommonCodeStyleSettings.IndentOptions indentOptions) {
        indentOptions.INDENT_SIZE = 4;
        commonSettings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
        commonSettings.KEEP_BLANK_LINES_IN_DECLARATIONS = 1;
        // Don't set it to 2 -- this setting is used implicitly in a lot of methods related to spacing,
        // e.g. in SpacingBuilder#blankLines(), and can lead to unexpected side-effects in formatter's
        // behavior
        commonSettings.KEEP_BLANK_LINES_IN_CODE = 1;
        commonSettings.METHOD_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
        commonSettings.CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    }

    @Override
    public CommonCodeStyleSettings getDefaultCommonSettings() {
        // Return the Python-specific subclass so it carries the active code style profile baseline for
        // common fields (PY-85946). Seed indent options and run customizeDefaults() for the indent /
        // keep-blank-lines baseline.
        PyCommonCodeStyleSettings defaultSettings = new PyCommonCodeStyleSettings();
        defaultSettings.initIndentOptions();
        //noinspection ConstantConditions
        customizeDefaults(defaultSettings, defaultSettings.getIndentOptions());
        // customizeDefaults() overrides a few wrap/align fields back to their historical values, so
        // re-apply the modern profile afterwards when the opt-in flag is on. Otherwise those fields
        // (METHOD/CALL_PARAMETERS_WRAP, ALIGN_MULTILINE_PARAMETERS_IN_CALLS) would diverge from the active
        // "default" scheme and be reported as "modified".
        if (PyCodeStyleDefaultsKt.isPyNewFormatterDefaultsActive()) {
            PyDefaultStyleGuide.applyToCommonSettings(defaultSettings);
        }
        return defaultSettings;
    }

    // TODO consulo: LanguageCodeStyleSettingsProvider#createCustomSettings, #getAdditionalAccessors (CodeStylePropertyAccessor)
    // and #createConfigurable missing - custom settings and the configurable are registered by PyCodeStyleSettingsProvider

    private static final String SPACING_SETTINGS_PREVIEW = """
    def settings_preview(argument, key=value):
        dict = {1:'a', 2:'b', 3:'c'}
        x = dict[1]
        expr = (1+2)*3 << 4**5 & 16
        if expr == 0 or abs(expr) < 0: print('weird'); return
        settings_preview(key=1)

    foo =\\
        bar

    def no_params():
        return globals()""";

    private static final String BLANK_LINES_SETTINGS_PREVIEW = """
    import os
    class C(object):
        import sys
        x = 1
        def foo(self):
            import platform
            print(platform.processor())""";
    private static final String WRAP_SETTINGS_PREVIEW = """
    from module import foo, bar, baz, quux

    short = 1
    long_name = 2
    counter += 1

    long_expression = component_one + component_two + component_three + component_four + component_five + component_six

    def xyzzy(a1, a2, long_parameter_1, a3, a4, long_parameter_2):
        pass

    xyzzy(1, 2, 'long_string_constant1', 3, 4, 'long_string_constant2')

    xyzzy(
        'with',
        'hanging',
          'indent'
    )
    attrs = [e.attr for e in
        items]
    
    num_dict = {"one": 1, "two": 2, "three": 3, "four": 4, "five": 5}
    
    colors = ['red', 'green', 'blue', 'black', 'white', 'gray']
    
    star_names = {"Sirius", "Betelgeuse", "Polaris", "Vega", "Arcturus", "Aldebaran"}
    
    planets = ("Mercury", "Venus", "Earth", "Mars", "Jupiter", "Saturn", "Uranus", "Neptune")

    ingredients = [
        'green',
        'eggs',
    ]

    if True: pass

    try: pass
    finally: pass
    """;
    private static final String INDENT_SETTINGS_PREVIEW = """
    def foo():
        print('bar')

    def long_function_name(
            var_one, var_two, var_three,
            var_four):
        print(var_one)""";
}
