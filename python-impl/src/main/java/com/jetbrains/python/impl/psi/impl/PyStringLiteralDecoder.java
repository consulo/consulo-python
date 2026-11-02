// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * Copyright 2013-2026 consulo.io
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
package com.jetbrains.python.impl.psi.impl;

import com.jetbrains.python.impl.psi.PyStringLiteralUtil;
import com.jetbrains.python.psi.FutureFeature;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyFile;
import consulo.annotation.access.RequiredReadAction;
import consulo.document.util.TextRange;
import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiFile;
import consulo.util.lang.Pair;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PyStringLiteralDecoder {
    public static final Pattern PATTERN_ESCAPE = Pattern.compile("(\\{\\{|}})|" +                // 1
        "\\\\(" +
        "\n|\\\\|'|\"|a|b|f|n|r|t|v|" + // 2
        "([0-7]{1,3})|" +               // 3
        "x([0-9a-fA-F]{1,2})|" +        // 4
        "N(\\{.*?})|" +                 // 5
        "u([0-9a-fA-F]{4})|" +          // 6
        "U([0-9a-fA-F]{8})" +           // 7
        ")");

    private enum EscapeRegexGroup {
        WHOLE_MATCH,
        FSTRING_BRACE,
        ESCAPED_SUBSTRING,
        OCTAL,
        HEXADECIMAL,
        UNICODE_NAMED,
        UNICODE_16BIT,
        UNICODE_32BIT,
    }

    private static final Map<String, String> escapeMap = initializeEscapeMap();

    private static Map<String, String> initializeEscapeMap() {
        Map<String, String> map = new HashMap<>();
        map.put("\n", "\n");
        map.put("\\", "\\");
        map.put("'", "'");
        map.put("\"", "\"");
        map.put("a", "\001");
        map.put("b", "\b");
        map.put("f", "\f");
        map.put("n", "\n");
        map.put("r", "\r");
        map.put("t", "\t");
        map.put("v", "\013");
        return map;
    }

    private final ASTNode myNode;

    public PyStringLiteralDecoder(ASTNode node) {
        myNode = node;
    }

    @RequiredReadAction
    public List<Pair<TextRange, String>> decodeContent() {
        return decodeRange(PyStringLiteralExpressionImpl.getNodeTextRange(myNode.getText()));
    }

    @RequiredReadAction
    public List<Pair<TextRange, String>> decodeRange(TextRange range) {
        return decodeFragment(range.substring(myNode.getText()), range.getStartOffset());
    }

    @RequiredReadAction
    private List<Pair<TextRange, String>> decodeFragment(String encoded, int offset) {
        String prefix = PyStringLiteralUtil.getPrefix(myNode.getText());
        boolean raw = PyStringLiteralUtil.isRawPrefix(prefix);
        boolean unicode = PyStringLiteralUtil.isUnicodePrefix(prefix) || isUnicodeByDefault();
        boolean formatted = PyStringLiteralUtil.isFormattedPrefix(prefix);

        List<Pair<TextRange, String>> result = new ArrayList<>();
        Matcher escMatcher = PATTERN_ESCAPE.matcher(encoded);
        int index = 0;
        while (escMatcher.find(index)) {
            if (index < escMatcher.start()) {
                TextRange range = TextRange.create(index, escMatcher.start());
                TextRange offsetRange = range.shiftRight(offset);
                result.add(Pair.create(offsetRange, range.substring(encoded)));
            }

            String fStringBrace = escapeRegexGroup(escMatcher, EscapeRegexGroup.FSTRING_BRACE);
            String octal = escapeRegexGroup(escMatcher, EscapeRegexGroup.OCTAL);
            String hex = escapeRegexGroup(escMatcher, EscapeRegexGroup.HEXADECIMAL);
            // TODO: Implement unicode character name escapes: EscapeRegexGroup.UNICODE_NAMED
            String unicode16 = escapeRegexGroup(escMatcher, EscapeRegexGroup.UNICODE_16BIT);
            String unicode32 = escapeRegexGroup(escMatcher, EscapeRegexGroup.UNICODE_32BIT);
            String wholeMatch = escapeRegexGroup(escMatcher, EscapeRegexGroup.WHOLE_MATCH);
            assert wholeMatch != null;

            boolean escapedUnicode = !raw || unicode;

            String str;
            if (fStringBrace != null) {
                str = formatted ? wholeMatch.substring(0, 1) : wholeMatch;
            }
            else if (!raw && octal != null) {
                str = new String(new char[]{(char) Integer.parseInt(octal, 8)});
            }
            else if (!raw && hex != null) {
                str = new String(new char[]{(char) Integer.parseInt(hex, 16)});
            }
            else if (escapedUnicode && unicode16 != null) {
                str = unicode ? new String(new char[]{(char) Integer.parseInt(unicode16, 16)}) : wholeMatch;
            }
            else if (escapedUnicode && unicode32 != null) {
                String s = wholeMatch;
                if (unicode) {
                    try {
                        s = new String(Character.toChars((int) Long.parseLong(unicode32, 16)));
                    }
                    catch (IllegalArgumentException ignored) {
                    }
                }
                str = s;
            }
            else if (raw) {
                str = wholeMatch;
            }
            else {
                String toReplace = escapeRegexGroup(escMatcher, EscapeRegexGroup.ESCAPED_SUBSTRING);
                str = escapeMap.get(toReplace);
            }

            if (str != null) {
                TextRange wholeMatchRange = TextRange.create(escMatcher.start(), escMatcher.end());
                result.add(Pair.create(wholeMatchRange.shiftRight(offset), str));
            }

            index = escMatcher.end();
        }
        TextRange range = TextRange.create(index, encoded.length());
        TextRange offRange = range.shiftRight(offset);
        result.add(Pair.create(offRange, range.substring(encoded)));
        return result;
    }

    private static @Nullable String escapeRegexGroup(Matcher matcher, EscapeRegexGroup group) {
        return matcher.group(group.ordinal());
    }

    @RequiredReadAction
    private boolean isUnicodeByDefault() {
        if (!LanguageLevel.forElement(myNode.getPsi()).isPython2()) {
            return true;
        }
        PsiFile file = myNode.getPsi().getContainingFile();
        if (file instanceof PyFile pyFile) {
            return pyFile.hasImportFromFuture(FutureFeature.UNICODE_LITERALS);
        }
        return false;
    }
}
