/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.impl.codeInsight.regexp.PythonVerboseRegexpLanguage;
import com.jetbrains.python.impl.lexer.PythonHighlightingLexer;
import com.jetbrains.python.impl.psi.PyStringLiteralUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.access.RequiredReadAction;
import consulo.document.util.TextRange;
import consulo.language.Language;
import consulo.language.ast.ASTNode;
import consulo.language.ast.IElementType;
import consulo.language.inject.InjectedLanguageManager;
import consulo.language.psi.*;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.navigation.ItemPresentation;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.ui.image.Image;
import consulo.util.lang.Pair;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.intellij.lang.regexp.DefaultRegExpPropertiesProvider;
import org.intellij.lang.regexp.RegExpLanguageHost;
import org.intellij.lang.regexp.psi.RegExpChar;
import org.intellij.lang.regexp.psi.RegExpGroup;
import org.intellij.lang.regexp.psi.RegExpNamedGroupRef;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PyStringLiteralExpressionImpl extends PyElementImpl implements PyStringLiteralExpression, RegExpLanguageHost {
    public static final Pattern PATTERN_ESCAPE =
        Pattern.compile("\\\\(\n|\\\\|'|\"|a|b|f|n|r|t|v|([0-7]{1,3})|x([0-9a-fA-F]{1,2})" + "|N(\\{.*?\\})|u([0-9a-fA-F]{4})|U([0-9a-fA-F]{8}))");
    //        -> 1                        ->   2      <-->     3          <-     ->   4     <-->    5      <-   ->  6           <-<-

    private enum EscapeRegexGroup {
        WHOLE_MATCH,
        ESCAPED_SUBSTRING,
        OCTAL,
        HEXADECIMAL,
        UNICODE_NAMED,
        UNICODE_16BIT,
        UNICODE_32BIT
    }

    private static final Map<String, String> escapeMap = initializeEscapeMap();
    private String stringValue;
    private List<TextRange> valueTextRanges;
    @Nullable
    private List<Pair<TextRange, String>> myDecodedFragments;
    private final DefaultRegExpPropertiesProvider myPropertiesProvider;

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

    public PyStringLiteralExpressionImpl(ASTNode astNode) {
        super(astNode);
        myPropertiesProvider = DefaultRegExpPropertiesProvider.getInstance();
    }

    @Override
    protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
        pyVisitor.visitPyStringLiteralExpression(this);
    }

    @Override
    public void subtreeChanged() {
        super.subtreeChanged();
        stringValue = null;
        valueTextRanges = null;
        myDecodedFragments = null;
    }

    @Nonnull
    @Override
    @RequiredReadAction
    public List<TextRange> getStringValueTextRanges() {
        if (valueTextRanges == null) {
            int elStart = getTextRange().getStartOffset();
            List<TextRange> ranges = new ArrayList<>();
            for (ASTNode node : getStringNodes()) {
                TextRange range = getNodeTextRange(node.getText());
                int nodeOffset = node.getStartOffset() - elStart;
                ranges.add(TextRange.from(nodeOffset + range.getStartOffset(), range.getLength()));
            }
            valueTextRanges = Collections.unmodifiableList(ranges);
        }
        return valueTextRanges;
    }

    public static TextRange getNodeTextRange(String text) {
        int startOffset = getPrefixLength(text);
        int delimiterLength = 1;
        String afterPrefix = text.substring(startOffset);
        if (afterPrefix.startsWith("\"\"\"") || afterPrefix.startsWith("'''")) {
            delimiterLength = 3;
        }
        String delimiter = text.substring(startOffset, startOffset + delimiterLength);
        startOffset += delimiterLength;
        int endOffset = text.length();
        if (text.substring(startOffset).endsWith(delimiter)) {
            endOffset -= delimiterLength;
        }
        return new TextRange(startOffset, endOffset);
    }

    public static int getPrefixLength(String text) {
        return PyStringLiteralUtil.getPrefixEndOffset(text, 0);
    }

    @SuppressWarnings("SimplifiableIfStatement")
    private boolean isUnicodeByDefault() {
        if (LanguageLevel.forElement(this).isAtLeast(LanguageLevel.PYTHON30)) {
            return true;
        }
        return getContainingFile() instanceof PyFile pyFile && pyFile.hasImportFromFuture(FutureFeature.UNICODE_LITERALS);
    }

    @Nonnull
    @Override
    @RequiredReadAction
    public List<Pair<TextRange, String>> getDecodedFragments() {
        if (myDecodedFragments == null) {
            List<Pair<TextRange, String>> result = new ArrayList<>();
            int elementStart = getTextRange().getStartOffset();
            boolean unicodeByDefault = isUnicodeByDefault();
            for (ASTNode node : getStringNodes()) {
                String text = node.getText();
                TextRange textRange = getNodeTextRange(text);
                int offset = node.getTextRange().getStartOffset() - elementStart + textRange.getStartOffset();
                String encoded = textRange.substring(text);
                boolean hasRawPrefix = PyStringLiteralUtil.isRawPrefix(PyStringLiteralUtil.getPrefix(text));
                boolean hasUnicodePrefix = PyStringLiteralUtil.isUnicodePrefix(PyStringLiteralUtil.getPrefix(text));
                result.addAll(getDecodedFragments(encoded, offset, hasRawPrefix, unicodeByDefault || hasUnicodePrefix));
            }
            myDecodedFragments = result;
        }
        return myDecodedFragments;
    }

    @Override
    @RequiredReadAction
    public boolean isDocString() {
        List<ASTNode> stringNodes = getStringNodes();
        return stringNodes.size() == 1 && stringNodes.get(0).getElementType() == PyTokenTypes.DOCSTRING;
    }

    @Nonnull
    private static List<Pair<TextRange, String>> getDecodedFragments(@Nonnull String encoded, int offset, boolean raw, boolean unicode) {
        List<Pair<TextRange, String>> result = new ArrayList<>();
        Matcher escMatcher = PATTERN_ESCAPE.matcher(encoded);
        int index = 0;
        while (escMatcher.find(index)) {
            if (index < escMatcher.start()) {
                TextRange range = TextRange.create(index, escMatcher.start());
                TextRange offsetRange = range.shiftRight(offset);
                result.add(Pair.create(offsetRange, range.substring(encoded)));
            }

            String octal = escapeRegexGroup(escMatcher, EscapeRegexGroup.OCTAL);
            String hex = escapeRegexGroup(escMatcher, EscapeRegexGroup.HEXADECIMAL);
            // TODO: Implement unicode character name escapes: EscapeRegexGroup.UNICODE_NAMED
            String unicode16 = escapeRegexGroup(escMatcher, EscapeRegexGroup.UNICODE_16BIT);
            String unicode32 = escapeRegexGroup(escMatcher, EscapeRegexGroup.UNICODE_32BIT);
            String wholeMatch = escapeRegexGroup(escMatcher, EscapeRegexGroup.WHOLE_MATCH);

            boolean escapedUnicode = raw && unicode || !raw;

            String str;
            if (!raw && octal != null) {
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

    @Nullable
    private static String escapeRegexGroup(@Nonnull Matcher matcher, EscapeRegexGroup group) {
        return matcher.group(group.ordinal());
    }

    @Nonnull
    @Override
    @RequiredReadAction
    public List<ASTNode> getStringNodes() {
        return Arrays.asList(getNode().getChildren(PyTokenTypes.STRING_NODES));
    }

    @Override
    @RequiredReadAction
    public String getStringValue() {
        //ASTNode child = getNode().getFirstChildNode();
        //assert child != null;
        if (stringValue == null) {
            StringBuilder out = new StringBuilder();
            for (Pair<TextRange, String> fragment : getDecodedFragments()) {
                out.append(fragment.getSecond());
            }
            stringValue = out.toString();
        }
        return stringValue;
    }

    @Override
    @RequiredReadAction
    public TextRange getStringValueTextRange() {
        List<TextRange> allRanges = getStringValueTextRanges();
        if (allRanges.size() == 1) {
            return allRanges.get(0);
        }
        if (allRanges.size() > 1) {
            return allRanges.get(0).union(allRanges.get(allRanges.size() - 1));
        }
        return new TextRange(0, getTextLength());
    }

    @Override
    @RequiredReadAction
    public String toString() {
        return super.toString() + ": " + getStringValue();
    }

    @Override
    public boolean isValidHost() {
        return true;
    }

    @Override
    @RequiredReadAction
    public PyType getType(@Nonnull TypeEvalContext context, @Nonnull TypeEvalContext.Key key) {
        List<ASTNode> nodes = getStringNodes();
        if (nodes.size() > 0) {
            String text = getStringNodes().get(0).getText();

            PyFile file = PsiTreeUtil.getParentOfType(this, PyFile.class);
            if (file != null) {
                IElementType type = PythonHighlightingLexer.convertStringType(
                    getStringNodes().get(0).getElementType(),
                    text,
                    LanguageLevel.forElement(this),
                    file.hasImportFromFuture(FutureFeature
                        .UNICODE_LITERALS)
                );
                if (PyTokenTypes.UNICODE_NODES.contains(type)) {
                    return PyBuiltinCache.getInstance(this).getUnicodeType(LanguageLevel.forElement(this));
                }
            }
        }
        return PyBuiltinCache.getInstance(this).getBytesType(LanguageLevel.forElement(this));
    }

    @Override
    @Nonnull
    public PsiReference[] getReferences() {
        return ReferenceProvidersRegistry.getReferencesFromProviders(this, PsiReferenceService.Hints.NO_HINTS);
    }

    @Override
    public ItemPresentation getPresentation() {
        return new ItemPresentation() {
            @Nullable
            @Override
            @RequiredReadAction
            public String getPresentableText() {
                return getStringValue();
            }

            @Nonnull
            @Override
            public String getLocationString() {
                return "(" + PyElementPresentation.getPackageForFile(getContainingFile()) + ")";
            }

            @Nullable
            @Override
            public Image getIcon() {
                return PlatformIconGroup.nodesVariable();
            }
        };
    }

    @Override
    public PsiLanguageInjectionHost updateText(@Nonnull String text) {
        return ElementManipulators.handleContentChange(this, text);
    }

    @Override
    @Nonnull
    public LiteralTextEscaper<? extends PsiLanguageInjectionHost> createLiteralTextEscaper() {
        return new StringLiteralTextEscaper(this);
    }

    private static class StringLiteralTextEscaper extends LiteralTextEscaper<PyStringLiteralExpression> {
        private final PyStringLiteralExpressionImpl myHost;

        protected StringLiteralTextEscaper(@Nonnull PyStringLiteralExpressionImpl host) {
            super(host);
            myHost = host;
        }

        @Override
        @RequiredReadAction
        public boolean decode(@Nonnull TextRange rangeInsideHost, @Nonnull StringBuilder outChars) {
            for (Pair<TextRange, String> fragment : myHost.getDecodedFragments()) {
                TextRange encodedTextRange = fragment.getFirst();
                TextRange intersection = encodedTextRange.intersection(rangeInsideHost);
                if (intersection != null && !intersection.isEmpty()) {
                    String value = fragment.getSecond();
                    String intersectedValue;
                    if (value.length() == 1 || value.length() == intersection.getLength()) {
                        intersectedValue = value;
                    }
                    else {
                        int start = Math.max(0, rangeInsideHost.getStartOffset() - encodedTextRange.getStartOffset());
                        int end = Math.min(value.length(), start + intersection.getLength());
                        intersectedValue = value.substring(start, end);
                    }
                    outChars.append(intersectedValue);
                }
            }
            return true;
        }

        @Override
        @RequiredReadAction
        public int getOffsetInHost(int offsetInDecoded, @Nonnull TextRange rangeInsideHost) {
            int offset = 0;
            int endOffset = -1;
            for (Pair<TextRange, String> fragment : myHost.getDecodedFragments()) {
                TextRange encodedTextRange = fragment.getFirst();
                TextRange intersection = encodedTextRange.intersection(rangeInsideHost);
                if (intersection != null && !intersection.isEmpty()) {
                    String value = fragment.getSecond();
                    int valueLength = value.length();
                    int intersectionLength = intersection.getLength();
                    if (valueLength == 0) {
                        return -1;
                    }
                    else if (valueLength == 1) {
                        if (offset == offsetInDecoded) {
                            return intersection.getStartOffset();
                        }
                        offset++;
                    }
                    else {
                        if (offset + intersectionLength >= offsetInDecoded) {
                            int delta = offsetInDecoded - offset;
                            return intersection.getStartOffset() + delta;
                        }
                        offset += intersectionLength;
                    }
                    endOffset = intersection.getEndOffset();
                }
            }
            // XXX: According to the real use of getOffsetInHost() it should return the correct host offset for the offset in decoded at the
            // end of the range inside host, not -1
            if (offset == offsetInDecoded) {
                return endOffset;
            }
            return -1;
        }

        @Override
        public boolean isOneLine() {
            return false;
        }
    }

    @Override
    @RequiredReadAction
    public int valueOffsetToTextOffset(int valueOffset) {
        return createLiteralTextEscaper().getOffsetInHost(valueOffset, getStringValueTextRange());
    }

    @Nonnull
    @Override
    public Class getHostClass() {
        return getClass();
    }

    @Override
    @RequiredReadAction
    public boolean characterNeedsEscaping(char c) {
        if (c == '#') {
            return isVerboseInjection();
        }
        return c == ']' || c == '}' || c == '\"' || c == '\'';
    }

    @RequiredReadAction
    private boolean isVerboseInjection() {
        List<Pair<PsiElement, TextRange>> files = InjectedLanguageManager.getInstance(getProject()).getInjectedPsiFiles(this);
        if (files != null) {
            for (Pair<PsiElement, TextRange> file : files) {
                Language language = file.getFirst().getLanguage();
                if (language == PythonVerboseRegexpLanguage.INSTANCE) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean supportsPerl5EmbeddedComments() {
        return true;
    }

    @Override
    public boolean supportsPossessiveQuantifiers() {
        return false;
    }

    @Override
    public boolean supportsPythonConditionalRefs() {
        return true;
    }

    @Override
    public boolean supportsNamedGroupSyntax(RegExpGroup group) {
        return group.getType() == RegExpGroup.Type.PYTHON_NAMED_GROUP;
    }

    @Override
    public boolean supportsNamedGroupRefSyntax(RegExpNamedGroupRef ref) {
        return ref.isPythonNamedGroupRef();
    }

    @Override
    public boolean supportsExtendedHexCharacter(RegExpChar regExpChar) {
        return false;
    }

    @Override
    public boolean isValidCategory(@Nonnull String category) {
        return myPropertiesProvider.isValidCategory(category);
    }

    @Nonnull
    @Override
    public String[][] getAllKnownProperties() {
        return myPropertiesProvider.getAllKnownProperties();
    }

    @Nullable
    @Override
    public String getPropertyDescription(@Nullable String name) {
        return myPropertiesProvider.getPropertyDescription(name);
    }

    @Nonnull
    @Override
    public String[][] getKnownCharacterClasses() {
        return myPropertiesProvider.getKnownCharacterClasses();
    }
}
