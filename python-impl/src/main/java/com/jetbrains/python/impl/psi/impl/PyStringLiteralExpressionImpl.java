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
import consulo.application.AllIcons;
import consulo.document.util.TextRange;
import consulo.language.Language;
import consulo.language.ast.ASTNode;
import consulo.language.ast.IElementType;
import consulo.language.inject.InjectedLanguageManager;
import consulo.language.psi.*;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.navigation.ItemPresentation;
import consulo.ui.image.Image;
import consulo.util.lang.Pair;
import org.intellij.lang.regexp.DefaultRegExpPropertiesProvider;
import org.intellij.lang.regexp.RegExpLanguageHost;
import org.intellij.lang.regexp.psi.RegExpChar;
import org.intellij.lang.regexp.psi.RegExpGroup;
import org.intellij.lang.regexp.psi.RegExpNamedGroupRef;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
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

  @Override
  @Nonnull
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

  public static TextRange getNodeTextRange(final String text) {
    int startOffset = getPrefixLength(text);
    int delimiterLength = 1;
    final String afterPrefix = text.substring(startOffset);
    if (afterPrefix.startsWith("\"\"\"") || afterPrefix.startsWith("'''")) {
      delimiterLength = 3;
    }
    final String delimiter = text.substring(startOffset, startOffset + delimiterLength);
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

  private boolean isUnicodeByDefault() {
    if (LanguageLevel.forElement(this).isAtLeast(LanguageLevel.PYTHON30)) {
      return true;
    }
    final PsiFile file = getContainingFile();
    if (file instanceof PyFile) {
      final PyFile pyFile = (PyFile)file;
      return pyFile.hasImportFromFuture(FutureFeature.UNICODE_LITERALS);
    }
    return false;
  }

  @Override
  @Nonnull
  public List<Pair<TextRange, String>> getDecodedFragments() {
    if (myDecodedFragments == null) {
      final List<Pair<TextRange, String>> result = new ArrayList<>();
      final int elementStart = getTextRange().getStartOffset();
      final boolean unicodeByDefault = isUnicodeByDefault();
      for (ASTNode node : getStringNodes()) {
        final String text = node.getText();
        final TextRange textRange = getNodeTextRange(text);
        final int offset = node.getTextRange().getStartOffset() - elementStart + textRange.getStartOffset();
        final String encoded = textRange.substring(text);
        final boolean hasRawPrefix = PyStringLiteralUtil.isRawPrefix(PyStringLiteralUtil.getPrefix(text));
        final boolean hasUnicodePrefix = PyStringLiteralUtil.isUnicodePrefix(PyStringLiteralUtil.getPrefix(text));
        result.addAll(getDecodedFragments(encoded, offset, hasRawPrefix, unicodeByDefault || hasUnicodePrefix));
      }
      myDecodedFragments = result;
    }
    return myDecodedFragments;
  }

  @Override
  public boolean isDocString() {
    final List<ASTNode> stringNodes = getStringNodes();
    return stringNodes.size() == 1 && stringNodes.get(0).getElementType() == PyTokenTypes.DOCSTRING;
  }

  @Nonnull
  private static List<Pair<TextRange, String>> getDecodedFragments(@Nonnull String encoded, int offset, boolean raw, boolean unicode) {
    final List<Pair<TextRange, String>> result = new ArrayList<>();
    final Matcher escMatcher = PATTERN_ESCAPE.matcher(encoded);
    int index = 0;
    while (escMatcher.find(index)) {
      if (index < escMatcher.start()) {
        final TextRange range = TextRange.create(index, escMatcher.start());
        final TextRange offsetRange = range.shiftRight(offset);
        result.add(Pair.create(offsetRange, range.substring(encoded)));
      }

      final String octal = escapeRegexGroup(escMatcher, EscapeRegexGroup.OCTAL);
      final String hex = escapeRegexGroup(escMatcher, EscapeRegexGroup.HEXADECIMAL);
      // TODO: Implement unicode character name escapes: EscapeRegexGroup.UNICODE_NAMED
      final String unicode16 = escapeRegexGroup(escMatcher, EscapeRegexGroup.UNICODE_16BIT);
      final String unicode32 = escapeRegexGroup(escMatcher, EscapeRegexGroup.UNICODE_32BIT);
      final String wholeMatch = escapeRegexGroup(escMatcher, EscapeRegexGroup.WHOLE_MATCH);

      final boolean escapedUnicode = raw && unicode || !raw;

      final String str;
      if (!raw && octal != null) {
        str = new String(new char[]{(char)Integer.parseInt(octal, 8)});
      }
      else if (!raw && hex != null) {
        str = new String(new char[]{(char)Integer.parseInt(hex, 16)});
      }
      else if (escapedUnicode && unicode16 != null) {
        str = unicode ? new String(new char[]{(char)Integer.parseInt(unicode16, 16)}) : wholeMatch;
      }
      else if (escapedUnicode && unicode32 != null) {
        String s = wholeMatch;
        if (unicode) {
          try {
            s = new String(Character.toChars((int)Long.parseLong(unicode32, 16)));
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
        final String toReplace = escapeRegexGroup(escMatcher, EscapeRegexGroup.ESCAPED_SUBSTRING);
        str = escapeMap.get(toReplace);
      }

      if (str != null) {
        final TextRange wholeMatchRange = TextRange.create(escMatcher.start(), escMatcher.end());
        result.add(Pair.create(wholeMatchRange.shiftRight(offset), str));
      }

      index = escMatcher.end();
    }
    final TextRange range = TextRange.create(index, encoded.length());
    final TextRange offRange = range.shiftRight(offset);
    result.add(Pair.create(offRange, range.substring(encoded)));
    return result;
  }

  @Nullable
  private static String escapeRegexGroup(@Nonnull Matcher matcher, EscapeRegexGroup group) {
    return matcher.group(group.ordinal());
  }

  @Override
  @Nonnull
  public List<ASTNode> getStringNodes() {
    return Arrays.asList(getNode().getChildren(PyTokenTypes.STRING_NODES));
  }

  @Override
  public String getStringValue() {
    //ASTNode child = getNode().getFirstChildNode();
    //assert child != null;
    if (stringValue == null) {
      final StringBuilder out = new StringBuilder();
      for (Pair<TextRange, String> fragment : getDecodedFragments()) {
        out.append(fragment.getSecond());
      }
      stringValue = out.toString();
    }
    return stringValue;
  }

  @Override
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
  public String toString() {
    return super.toString() + ": " + getStringValue();
  }

  @Override
  public boolean isValidHost() {
    return true;
  }

  @Override
  public PyType getType(@Nonnull TypeEvalContext context, @Nonnull TypeEvalContext.Key key) {
    final List<ASTNode> nodes = getStringNodes();
    if (nodes.size() > 0) {
      String text = getStringNodes().get(0).getText();

      PyFile file = PsiTreeUtil.getParentOfType(this, PyFile.class);
      if (file != null) {
        IElementType type = PythonHighlightingLexer.convertStringType(getStringNodes().get(0).getElementType(),
                                                                      text,
                                                                      LanguageLevel.forElement(this),
                                                                      file.hasImportFromFuture(FutureFeature
                                                                                                 .UNICODE_LITERALS));
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
      public String getPresentableText() {
        return getStringValue();
      }

      @Nullable
      @Override
      public String getLocationString() {
        return "(" + PyElementPresentation.getPackageForFile(getContainingFile()) + ")";
      }

      @Nullable
      @Override
      public Image getIcon() {
        return AllIcons.Nodes.Variable;
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
    public boolean decode(@Nonnull final TextRange rangeInsideHost, @Nonnull final StringBuilder outChars) {
      for (Pair<TextRange, String> fragment : myHost.getDecodedFragments()) {
        final TextRange encodedTextRange = fragment.getFirst();
        final TextRange intersection = encodedTextRange.intersection(rangeInsideHost);
        if (intersection != null && !intersection.isEmpty()) {
          final String value = fragment.getSecond();
          final String intersectedValue;
          if (value.length() == 1 || value.length() == intersection.getLength()) {
            intersectedValue = value;
          }
          else {
            final int start = Math.max(0, rangeInsideHost.getStartOffset() - encodedTextRange.getStartOffset());
            final int end = Math.min(value.length(), start + intersection.getLength());
            intersectedValue = value.substring(start, end);
          }
          outChars.append(intersectedValue);
        }
      }
      return true;
    }

    @Override
    public int getOffsetInHost(final int offsetInDecoded, @Nonnull final TextRange rangeInsideHost) {
      int offset = 0;
      int endOffset = -1;
      for (Pair<TextRange, String> fragment : myHost.getDecodedFragments()) {
        final TextRange encodedTextRange = fragment.getFirst();
        final TextRange intersection = encodedTextRange.intersection(rangeInsideHost);
        if (intersection != null && !intersection.isEmpty()) {
          final String value = fragment.getSecond();
          final int valueLength = value.length();
          final int intersectionLength = intersection.getLength();
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
              final int delta = offsetInDecoded - offset;
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
  public int valueOffsetToTextOffset(int valueOffset) {
    return createLiteralTextEscaper().getOffsetInHost(valueOffset, getStringValueTextRange());
  }

  @Nonnull
  @Override
  public Class getHostClass() {
    return getClass();
  }

  @Override
  public boolean characterNeedsEscaping(char c) {
    if (c == '#') {
      return isVerboseInjection();
    }
    return c == ']' || c == '}' || c == '\"' || c == '\'';
  }

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
