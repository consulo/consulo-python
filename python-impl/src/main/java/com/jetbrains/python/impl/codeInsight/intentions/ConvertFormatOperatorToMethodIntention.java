// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.intentions;

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.impl.PyStringFormatParser;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import com.jetbrains.python.impl.psi.types.PyTupleType;
import com.jetbrains.python.impl.psi.types.PyTypeChecker;
import com.jetbrains.python.impl.psi.types.PyTypeUtil;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyParenthesizedExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PyStringLiteralUtil;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.document.util.TextRange;
import consulo.language.ast.ASTNode;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.jetbrains.python.impl.psi.PyUtil.sure;

/**
 * Replaces expressions like {@code "%s" % values} with likes of {@code "{0:s}".format(values)}.
 * <br/>
 */
@ExtensionImpl
@IntentionMetaData(ignoreId = "python.ConvertFormatOperatorToMethodIntention", categories = "Python", fileExtensions = "py")
public final class ConvertFormatOperatorToMethodIntention extends PyBaseIntentionAction {

    private static final Pattern FORMAT_PATTERN =
            Pattern.compile("%(?:\\((\\w+)\\))?([-#0+ ]*)((?:\\*|\\d+)?(?:\\.(?:\\*|\\d+))?)?[hlL]?([diouxXeEfFgGcrs%])");
    // groups: %:ignored,     1:key      2:mods    3:width-and---preci.sion            x:len  4: conversion-type

    private static final Pattern BRACE_PATTERN = Pattern.compile("(\\{|\\})");

    /**
     * copy source to target, doubling every brace.
     */
    private static void appendDoublingBraces(CharSequence source, StringBuilder target) {
        int index = 0;
        Matcher scanner = BRACE_PATTERN.matcher(source);
        boolean skipClosingBrace = false;
        while (scanner.find(index)) {
            if (scanner.start() > 1) {
                // handle escaping sequences PY-977
                if ("{".equals(scanner.group(0)) && "\\N".equals(source.subSequence(scanner.start() - 2, scanner.start()).toString())) {
                    skipClosingBrace = true;
                    target.append(source.subSequence(index, scanner.end()));
                    index = scanner.end();
                    continue;
                }
            }
            if (skipClosingBrace && "}".equals(scanner.group(0))) {
                skipClosingBrace = false;
                target.append(source.subSequence(index, scanner.end()));
                index = scanner.end();
                continue;
            }

            target.append(source.subSequence(index, scanner.start()));
            if ("{".equals(scanner.group(0))) {
                target.append("{{");
            }
            else {
                target.append("}}");
            }
            index = scanner.end();
        }
        target.append(source.subSequence(index, source.length()));
    }

    /**
     * Converts format expressions inside a string
     *
     * @return a pair of string builder with resulting string expression and a flag which is true if formats inside use mapping by name.
     */
    private static Pair<StringBuilder, Boolean> convertFormat(PyStringLiteralExpression stringLiteralExpression, String prefix) {
        // python string may be made of several literals, all different
        List<StringBuilder> constants = new ArrayList<>();
        boolean usesNamedFormat = false;
        List<ASTNode> stringNodes = stringLiteralExpression.getStringNodes();
        sure(stringNodes);
        sure(!stringNodes.isEmpty());
        for (ASTNode stringNode : stringNodes) {
            // preserve prefixes and quote form
            CharSequence text = stringNode.getChars();
            int openPos = 0;
            boolean hasPrefix = false;
            int prefixLength = PyStringLiteralUtil.getPrefixLength(String.valueOf(text));
            if (prefixLength != 0) {
                hasPrefix = true;
            }
            openPos += prefixLength;

            char quote = text.charAt(openPos);
            sure("\"'".indexOf(quote) >= 0);
            if (text.length() - openPos >= 6) {
                // triple-quoted?
                if (text.charAt(openPos + 1) == quote && text.charAt(openPos + 2) == quote) {
                    openPos += 2;
                }
            }
            int index = openPos + 1; // from quote to first in-string char
            StringBuilder out = new StringBuilder(text.subSequence(0, openPos + 1));
            if (!hasPrefix) {
                out.insert(0, prefix);
            }
            Matcher scanner = FORMAT_PATTERN.matcher(text);
            while (scanner.find(index)) {
                // store previous non-format part
                appendDoublingBraces(text.subSequence(index, scanner.start()), out);
                //out.append(text.subSequence(index, scanner.start()));
                // unpack format
                String f_key = scanner.group(1);
                String f_modifier = scanner.group(2);
                String f_width = scanner.group(3);
                String fConversion = scanner.group(4);
                // convert to format()'s
                if ("%%".equals(scanner.group(0))) {
                    // shortcut to put a literal %
                    out.append("%");
                }
                else {
                    sure(fConversion);
                    sure(!"%".equals(fConversion)); // a padded percent literal; can't bother to autoconvert, and in 3k % is different.
                            out.append("{");
                    if (f_key != null) {
                        out.append(f_key);
                        usesNamedFormat = true;
                    }
                    if ("r".equals(fConversion)) {
                    // don't convert %s -> !s, for %s is the normal way to output the default representation
                        out.append("!r");
                    }
                    out.append(":");
                    if (f_modifier != null) {
                        out.append(convertFormatSpec(f_modifier, f_width, fConversion));
                    }
                    if (f_width != null) {
                        out.append(f_width);
                    }
                    if ("i".equals(fConversion) || "u".equals(fConversion)) {
                        out.append("d");
                    }
                    else if (!"s".equals(fConversion) && !"r".equals(fConversion)) out.append(fConversion);

                    int lastIndexOf = out.lastIndexOf(":");
                    if (lastIndexOf == out.length() - 1) {
                        out.deleteCharAt(lastIndexOf);
                    }
                    out.append("}");
                }
                index = scanner.end();
            }
            // store non-format final part
            //out.append(text.subSequence(index, text.length()-1));
            appendDoublingBraces(text.subSequence(index, text.length()), out);
            constants.add(out);
        }
        // form the entire literal filling possible gaps between constants.
        // we assume that a string literal begins with its first constant, without a gap.
        TextRange full_range = stringLiteralExpression.getTextRange();
        int full_start = full_range.getStartOffset();
        CharSequence full_text = stringLiteralExpression.getNode().getChars();
        TextRange prev_range = stringNodes.get(0).getTextRange();
        int fragment_no = 1; // look at second and further fragments
        while (fragment_no < stringNodes.size()) {
            TextRange next_range = stringNodes.get(fragment_no).getTextRange();
            int left = prev_range.getEndOffset() - full_start;
            int right = next_range.getStartOffset() - full_start;
            if (left < right) {
                constants.get(fragment_no - 1).append(full_text.subSequence(left, right));
            }
            fragment_no += 1;
            prev_range = next_range;
        }
        int left = prev_range.getEndOffset() - full_start;
        int right = full_range.getEndOffset() - full_start;
        if (left < right) {
            // the barely possible case of last dangling "\"
            constants.get(constants.size() - 1).append(full_text.subSequence(left, right));
        }

        // join everything
        StringBuilder result = new StringBuilder();
        for (StringBuilder one : constants) {
            result.append(one);
        }
        return new Pair<>(result, usesNamedFormat);
    }

    public static String convertFormatSpec(String modifier,
            @Nullable String widthAndPrecision,
                                                                                                    @Nullable String conversionChar) {
        StringBuilder result = new StringBuilder();
        // in strict order
        if (has(modifier, '-')) {
            result.append("<"); // left align
        }
        else if ("s".equals(conversionChar) && !StringUtil.isEmptyOrSpaces(widthAndPrecision)) {
            // "%20s" aligns right, "{0:20s}" aligns left; to preserve align, make it explicit
            result.append(">");
        }
        if (has(modifier, '+')) {
            result.append("+"); // signed
        }
        else if (has(modifier, ' ')) result.append(" "); // default-signed
        if (has(modifier, '#')) { // alt numbers
            result.append("#");
        }
        if (has(modifier, '0')) { // padding
            result.append("0");
        }
        // anything else can't be here
        return result.toString();
    }

    private static boolean has(String where, char what) {
        return where.indexOf(what) >= 0;
    }

    public ConvertFormatOperatorToMethodIntention() {
        setText(PyLocalize.intnFormatOperatorToMethod());
    }

    @Override
    public boolean isAvailable(Project project, Editor editor, PsiFile psiFile) {
        if (!(psiFile instanceof PyFile)) {
            return false;
        }

        PyBinaryExpression binaryExpression =
                PsiTreeUtil.getParentOfType(psiFile.findElementAt(editor.getCaretModel().getOffset()), PyBinaryExpression.class, false);
        if (binaryExpression == null) {
            return false;
        }
        if (binaryExpression.getLeftExpression() instanceof PyStringLiteralExpression str &&
                binaryExpression.getOperator() == PyTokenTypes.PERC) {
            if ((!str.getText().isEmpty() && Character.toUpperCase(str.getText().charAt(0)) == 'B')) {
                return false;
            }

            List<PyStringFormatParser.SubstitutionChunk> chunks =
                    PyStringFormatParser.filterSubstitutions(PyStringFormatParser.parsePercentFormat(binaryExpression.getLeftExpression().getText()));

            for (PyStringFormatParser.SubstitutionChunk chunk : chunks) {
                if ("*".equals(chunk.getWidth()) || "*".equals(chunk.getPrecision())) {
                    return false;
                }
            }

            setText(PyLocalize.intnReplaceWithMethod());
            return true;
        }
        return false;
    }

    @Override
    public void doInvoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        PsiElement elementAt = file.findElementAt(editor.getCaretModel().getOffset());
        PyBinaryExpression element = PsiTreeUtil.getParentOfType(elementAt, PyBinaryExpression.class, false);
        if (element == null) {
            return;
        }
        PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
        PyExpression rightExpression = sure(element).getRightExpression();
        if (rightExpression == null) {
            return;
        }
        PyExpression rhs = PyPsiUtils.flattenParens(rightExpression);
        if (rhs == null) {
            return;
        }
        String paramText = sure(rhs).getText();
        TypeEvalContext context = TypeEvalContext.userInitiated(file.getProject(), file);
        PyType rhsType = context.getType(rhs);
        String prefix = "";
        LanguageLevel languageLevel = LanguageLevel.forElement(element);
        PyBuiltinCache builtinCache = PyBuiltinCache.getInstance(rhs);
        if (languageLevel.isPython2() && PyTypeChecker.match(builtinCache.getUnicodeType(languageLevel), rhsType, context) &&
                !PyTypeChecker.match(builtinCache.getBytesType(languageLevel), rhsType, context)) {
            prefix = "u";
        }
        PyStringLiteralExpression leftExpression = (PyStringLiteralExpression)element.getLeftExpression();
        Pair<StringBuilder, Boolean> converted = convertFormat(leftExpression, prefix);
        StringBuilder target = converted.getFirst();
        String separator = getSeparator(leftExpression);
        target.append(separator).append(".format");

        if (rhs instanceof PyReferenceExpression && rhsType instanceof PyTupleType) {
            target.append("(*").append(paramText).append(")");
        }
        else if (rhs instanceof PyCallExpression callExpression) { // potential dict(foo=1) -> format(foo=1)
            PyExpression callee = callExpression.getCallee();
            PyClassType classType = PyUtil.as(rhsType, PyClassType.class);
            if (classType != null && callee != null && isDictCall(callee, classType, context)) {
                target.append(sure(sure(callExpression.getArgumentList()).getNode()).getChars());
            }
            else { // just a call, reuse
                target.append("(");
                if (converted.getSecond()) { // map-by-name formatting was detected
                    target.append("**");
                }
                target.append(paramText).append(")");
            }
        }
        else if (PyTypeUtil.isDict(rhsType)) {
            target.append("(**").append(paramText).append(")");
        }
        else {
            target.append("(").append(paramText).append(")"); // tuple is ok as is
        }
        // Correctly handle multiline implicitly concatenated string literals (PY-9176)
        target.insert(0, '(').append(')');
        PyExpression parenthesized = elementGenerator.createExpressionFromText(LanguageLevel.forElement(element), target.toString());
        element.replace(sure(((PyParenthesizedExpression)parenthesized).getContainedExpression()));
    }

    private static boolean isDictCall(PyExpression callee,
            PyClassType classType,
            TypeEvalContext context) {
        PyClassType dictType = PyBuiltinCache.getInstance(callee.getContainingFile()).getDictType();
        var calleeType = PyUtil.as(context.getType(callee), PyClassType.class);
        return dictType != null &&
                calleeType != null &&
                classType.getPyClass() == dictType.getPyClass() &&
                calleeType.getPyClass() == dictType.getPyClass() &&
                calleeType.isDefinition();
    }

    private static String getSeparator(PyStringLiteralExpression leftExpression) {
        String separator = ""; // detect nontrivial whitespace around the "%"
        Pair<String, PsiElement> crop = collectWhitespace(leftExpression);
        String maybeSeparator = crop.getFirst();
        if (maybeSeparator != null && !maybeSeparator.isEmpty() && !" ".equals(maybeSeparator)) {
            separator = maybeSeparator;
        }
        else { // after "%"
            crop = collectWhitespace(crop.getSecond());
            maybeSeparator = crop.getFirst();
            if (maybeSeparator != null && !maybeSeparator.isEmpty() && !" ".equals(maybeSeparator)) {
                separator = maybeSeparator;
            }
        }
        return separator;
    }

    private static Pair<String, PsiElement> collectWhitespace(PsiElement start) {
        StringBuilder sb = new StringBuilder();
        PsiElement seeker = start;
        while (seeker != null) {
            seeker = seeker.getNextSibling();
            if (seeker instanceof PsiWhiteSpace) {
                sb.append(seeker.getText());
            }
            else {
                break;
            }
        }
        return Pair.create(sb.toString(), seeker);
    }
}

