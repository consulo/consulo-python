// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.highlighting;

import com.google.common.collect.ImmutableMap;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.highlighting.PyHighlighter;
import com.jetbrains.python.psi.LanguageLevel;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.Application;
import consulo.colorScheme.TextAttributesKey;
import consulo.colorScheme.setting.AttributesDescriptor;
import consulo.colorScheme.setting.ColorDescriptor;
import consulo.language.Language;
import consulo.language.editor.colorScheme.setting.RainbowColorSettingsPage;
import consulo.language.editor.highlight.SyntaxHighlighter;
import consulo.language.editor.highlight.SyntaxHighlighterFactory;
import consulo.language.editor.rawHighlight.RainbowHighlighter;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PySyntaxLocalize;
import org.jspecify.annotations.Nullable;

import java.util.Map;

// TODO consulo: InspectionColorSettingsPage, DisplayPrioritySortable missing
@ExtensionImpl
public class PythonColorsPage implements RainbowColorSettingsPage {
    private static final AttributesDescriptor[] ATTRS = new AttributesDescriptor[]{
        new AttributesDescriptor(PySyntaxLocalize.pythonColorsNumber(), PyHighlighter.PY_NUMBER),
        new AttributesDescriptor(PySyntaxLocalize.pythonColorsKeyword(), PyHighlighter.PY_KEYWORD),
        new AttributesDescriptor(PySyntaxLocalize.pythonColorsLineComment(), PyHighlighter.PY_LINE_COMMENT),

        new AttributesDescriptor(PySyntaxLocalize.pythonColorsStringBinaryBytes(), PyHighlighter.PY_BYTE_STRING),
        new AttributesDescriptor(PySyntaxLocalize.pythonColorsStringTextUnicode(), PyHighlighter.PY_UNICODE_STRING),
        new AttributesDescriptor(PySyntaxLocalize.pythonColorsStringEscapeSequenceValid(), PyHighlighter.PY_VALID_STRING_ESCAPE),
        new AttributesDescriptor(PySyntaxLocalize.pythonColorsStringEscapeSequenceInvalid(), PyHighlighter.PY_INVALID_STRING_ESCAPE),
        new AttributesDescriptor(PySyntaxLocalize.pythonColorsStringFStringExpressionBraces(), PyHighlighter.PY_FSTRING_FRAGMENT_BRACES),
        new AttributesDescriptor(PySyntaxLocalize.pythonColorsStringFStringTypeConversion(), PyHighlighter.PY_FSTRING_FRAGMENT_TYPE_CONVERSION),
        new AttributesDescriptor(PySyntaxLocalize.pythonColorsStringFStringFormatSpecifierStart(), PyHighlighter.PY_FSTRING_FRAGMENT_COLON),
        new AttributesDescriptor(PySyntaxLocalize.pythonColorsStringFStringSpecSpecial(), PyHighlighter.PY_FSTRING_FORMAT_SPEC_SPECIAL_CHAR),
        new AttributesDescriptor(PySyntaxLocalize.pythonColorsStringFStringSpecNumber(), PyHighlighter.PY_FSTRING_FORMAT_SPEC_NUMBER),

        new AttributesDescriptor(PySyntaxLocalize.pythonColorsDocstringText(), PyHighlighter.PY_DOC_COMMENT),
        new AttributesDescriptor(PySyntaxLocalize.pythonColorsDocstringTag(), PyHighlighter.PY_DOC_COMMENT_TAG),

        new AttributesDescriptor(PySyntaxLocalize.pythonColorsBracesAndOperatorsOperationSign(), PyHighlighter.PY_OPERATION_SIGN),
        new AttributesDescriptor(PySyntaxLocalize.pythonColorsBracesAndOperatorsParentheses(), PyHighlighter.PY_PARENTHS),
        new AttributesDescriptor(PySyntaxLocalize.pythonColorsBracesAndOperatorsBrackets(), PyHighlighter.PY_BRACKETS),
        new AttributesDescriptor(PySyntaxLocalize.pythonColorsBracesAndOperatorsBraces(), PyHighlighter.PY_BRACES),
        new AttributesDescriptor(PySyntaxLocalize.pythonColorsBracesAndOperatorsComma(), PyHighlighter.PY_COMMA),
        new AttributesDescriptor(PySyntaxLocalize.pythonColorsBracesAndOperatorsDot(), PyHighlighter.PY_DOT),

        new AttributesDescriptor(PySyntaxLocalize.pythonColorsFunctionsFunctionDefinition(), PyHighlighter.PY_FUNC_DEFINITION),
        new AttributesDescriptor(PySyntaxLocalize.pythonColorsFunctionsNestedFunctionDefinition(), PyHighlighter.PY_NESTED_FUNC_DEFINITION),
        new AttributesDescriptor(PySyntaxLocalize.pythonColorsFunctionsFunctionCall(), PyHighlighter.PY_FUNCTION_CALL),
        new AttributesDescriptor(PySyntaxLocalize.pythonColorsFunctionsMethodCall(), PyHighlighter.PY_METHOD_CALL),

        new AttributesDescriptor(PySyntaxLocalize.pythonColorsParametersParameter(), PyHighlighter.PY_PARAMETER),
        new AttributesDescriptor(PySyntaxLocalize.pythonColorsParametersSelfParameter(), PyHighlighter.PY_SELF_PARAMETER),

        new AttributesDescriptor(PySyntaxLocalize.pythonColorsKeywordArgument(), PyHighlighter.PY_KEYWORD_ARGUMENT),

        new AttributesDescriptor(PySyntaxLocalize.pythonColorsSpecialNamesDefinition(), PyHighlighter.PY_PREDEFINED_DEFINITION),
        new AttributesDescriptor(PySyntaxLocalize.pythonColorsSpecialNamesUsage(), PyHighlighter.PY_PREDEFINED_USAGE),

        new AttributesDescriptor(PySyntaxLocalize.pythonColorsBuiltInName(), PyHighlighter.PY_BUILTIN_NAME),
        new AttributesDescriptor(PySyntaxLocalize.pythonColorsDecorator(), PyHighlighter.PY_DECORATOR),
        new AttributesDescriptor(PySyntaxLocalize.pythonColorsClassDefinition(), PyHighlighter.PY_CLASS_DEFINITION),
        new AttributesDescriptor(PySyntaxLocalize.pythonColorsTypeAnnotation(), PyHighlighter.PY_ANNOTATION),
        new AttributesDescriptor(PySyntaxLocalize.pythonColorsLocalVariables(), PyHighlighter.PY_LOCAL_VARIABLE),
        new AttributesDescriptor(PySyntaxLocalize.pythonColorsTypeParameters(), PyHighlighter.PY_TYPE_PARAMETER),
    };

    private static final Map<String, TextAttributesKey> ourTagToDescriptorMap = ImmutableMap.<String, TextAttributesKey>builder()
        .put("docComment", PyHighlighter.PY_DOC_COMMENT)
        .put("docCommentTag", PyHighlighter.PY_DOC_COMMENT_TAG)
        .put("decorator", PyHighlighter.PY_DECORATOR)
        .put("predefined", PyHighlighter.PY_PREDEFINED_DEFINITION)
        .put("predefinedUsage", PyHighlighter.PY_PREDEFINED_USAGE)
        .put("funcDef", PyHighlighter.PY_FUNC_DEFINITION)
        .put("nestedFuncDef", PyHighlighter.PY_NESTED_FUNC_DEFINITION)
        .put("classDef", PyHighlighter.PY_CLASS_DEFINITION)
        .put("builtin", PyHighlighter.PY_BUILTIN_NAME)
        .put("self", PyHighlighter.PY_SELF_PARAMETER)
        .put("param", PyHighlighter.PY_PARAMETER)
        .put("kwarg", PyHighlighter.PY_KEYWORD_ARGUMENT)
        .put("call", PyHighlighter.PY_FUNCTION_CALL)
        .put("mcall", PyHighlighter.PY_METHOD_CALL)
        .put("annotation", PyHighlighter.PY_ANNOTATION)
        .put("localVar", PyHighlighter.PY_LOCAL_VARIABLE)
        .put("typeParam", PyHighlighter.PY_TYPE_PARAMETER)
        .put("fspecSpecial", PyHighlighter.PY_FSTRING_FORMAT_SPEC_SPECIAL_CHAR)
        .put("fspecNumber", PyHighlighter.PY_FSTRING_FORMAT_SPEC_NUMBER)
        .putAll(RainbowHighlighter.createRainbowHLM())
        .build();

    @Override
    public LocalizeValue getDisplayName() {
        return PySyntaxLocalize.pythonColorsPython();
    }

    @Override
    public AttributesDescriptor[] getAttributeDescriptors() {
        return ATTRS;
    }

    @Override
    public ColorDescriptor[] getColorDescriptors() {
        return ColorDescriptor.EMPTY_ARRAY;
    }

    @Override
    public SyntaxHighlighter getHighlighter() {
        SyntaxHighlighterFactory factory = Application.get().getExtensionPoint(SyntaxHighlighterFactory.class)
            .findFirstSafe(it -> it.getLanguage() == PythonLanguage.getInstance());
        if (factory instanceof PySyntaxHighlighterFactoryBase) {
            return ((PySyntaxHighlighterFactoryBase) factory).getSyntaxHighlighterForLanguageLevel(LanguageLevel.getLatest());
        }
        return SyntaxHighlighterFactory.getSyntaxHighlighter(PythonLanguage.getInstance(), null, null);
    }

    @Override
    public String getDemoText() {
        //noinspection NonAsciiCharacters
        return """
             @<decorator>decorator</decorator>(<kwarg>param</kwarg>=1)def f(<param>x</param>):
                 <docComment>\"""
                             Syntax Highlighting Demo
                             <docCommentTag>@param</docCommentTag> x Parameter
                         """ + RainbowHighlighter.generatePaletteExample() /* TODO consulo: RainbowHighlighter#generatePaletteExample(String) missing */ + """
             
                 \"""</docComment>

                             def <nestedFuncDef>nested_func</nestedFuncDef>(<param>y</param>):
                                 <call>print</call>(<param>y</param> + 1)

                             <localVar>s</localVar> = ("Test", 2+3, {'a': 'b'}, f'{<param>x</param>!s:{"^10"}}', f"{1:<fspecSpecial>0</fspecSpecial><fspecNumber>1</fspecNumber><fspecSpecial>d</fspecSpecial>}")   # Comment
                             <call>f</call>(<localVar>s</localVar>[0].<mcall>lower</mcall>())
                             <call>nested_func</call>(42)

                         class <classDef>Foo</classDef>:
                             tags: <annotation>List[<builtin>str</builtin>]</annotation>

                             def <predefined>__init__</predefined>(<self>self</self>: <annotation>Foo</annotation>):
                                 <localVar>byte_string</localVar>: <annotation><builtin>bytes</builtin></annotation> = b'newline:\\n also newline:\\x0a'
                                 <localVar>text_string</localVar> = u"Cyrillic Я is \\u042f. Oops: \\u042g"
                                 <self>self</self>.<mcall>make_sense</mcall>(<kwarg>whatever</kwarg>=1)

                             def <funcDef>make_sense</funcDef>[<typeParam>T</typeParam>](<self>self</self>, <param>whatever:</param> <annotation>T</annotation>):
                                 <self>self</self>.sense = <param>whatever</param>

                         x = <builtin>len</builtin>('abc')
                         type my_int = <builtin>int</builtin>
                         print(f.<predefinedUsage>__doc__</predefinedUsage>)""";
    }

    @Override
    public Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
        return ourTagToDescriptorMap;
    }

    @Override
    public boolean isRainbowType(TextAttributesKey type) {
        return PyHighlighter.PY_PARAMETER.equals(type) || PyHighlighter.PY_LOCAL_VARIABLE.equals(type);
    }

    @Override
    public @Nullable Language getLanguage() {
        return PythonLanguage.INSTANCE;
    }
}
