package com.jetbrains.python;

import consulo.document.util.TextRange;
import junit.framework.TestCase;

import java.util.List;

import static com.jetbrains.python.impl.inspections.PyStringFormatParser.*;

/**
 * @author yole
 */
public abstract class PyStringFormatParserTest extends TestCase {
  public void testSimple() {
    List<FormatStringChunk> chunks = parsePercentFormat("abc");
    assertEquals(1, chunks.size());
    assertConstant(chunks.get(0), 0, 3);
  }

  private static void assertConstant(FormatStringChunk aChunk, final int start, final int end) {
    ConstantChunk chunk = (ConstantChunk) aChunk;
    assertEquals(start, chunk.getStartIndex());
    assertEquals(end, chunk.getEndIndex());
  }

  public void testDoublePercent() {
    List<FormatStringChunk> chunks = parsePercentFormat("abc%%def");
    assertEquals(1, chunks.size());
    assertConstant(chunks.get(0), 0, 8);
  }

  public void testFormat() {
    List<FormatStringChunk> chunks = parsePercentFormat("%s");
    assertEquals(1, chunks.size());
    SubstitutionChunk chunk = (SubstitutionChunk) chunks.get(0);
    assertEquals(0, chunk.getStartIndex());
    assertEquals(2, chunk.getEndIndex());
    assertEquals('s', chunk.getConversionType());
  }

  public void testSubstitutionAfterFormat() {
    List<FormatStringChunk> chunks = parsePercentFormat("Hello, %s");
    assertEquals(2, chunks.size());
    assertConstant(chunks.get(0), 0, 7);
  }

  public void testMappingKey() {
    List<FormatStringChunk> chunks = parsePercentFormat("%(language)s");
    assertEquals(1, chunks.size());
    SubstitutionChunk chunk = (SubstitutionChunk) chunks.get(0);
    assertEquals("language", chunk.getMappingKey());
    assertEquals('s', chunk.getConversionType());
  }

  public void testConversionFlags() {
    List<FormatStringChunk> chunks = parsePercentFormat("%#0d");
    assertEquals(1, chunks.size());
    SubstitutionChunk chunk = (SubstitutionChunk) chunks.get(0);
    assertEquals("#0", chunk.getConversionFlags());
  }

  public void testWidth() {
    List<FormatStringChunk> chunks = parsePercentFormat("%345d");
    assertEquals(1, chunks.size());
    SubstitutionChunk chunk = (SubstitutionChunk) chunks.get(0);
    assertEquals("345", chunk.getWidth());
  }

  public void testPrecision() {
    List<FormatStringChunk> chunks = parsePercentFormat("%.2d");
    assertEquals(1, chunks.size());
    SubstitutionChunk chunk = (SubstitutionChunk) chunks.get(0);
    assertEquals("2", chunk.getPrecision());
  }

  public void testLengthModifier() {
    List<FormatStringChunk> chunks = parsePercentFormat("%ld");
    assertEquals(1, chunks.size());
    SubstitutionChunk chunk = (SubstitutionChunk) chunks.get(0);
    assertEquals('l', chunk.getLengthModifier());
  }

  public void testDoubleAsterisk() {
    List<FormatStringChunk> chunks = parsePercentFormat("%**d");
    assertEquals(2, chunks.size());
    SubstitutionChunk chunk = (SubstitutionChunk) chunks.get(0);
    assertEquals(2, chunk.getEndIndex());
    assertEquals('\0', chunk.getConversionType());
  }

  public void testUnclosedMapping() {
    List<FormatStringChunk> chunks = parsePercentFormat("%(name1s");
    SubstitutionChunk chunk = (SubstitutionChunk) chunks.get(0);
    assertEquals("name1s", chunk.getMappingKey());
    assertTrue(chunk.isUnclosedMapping());
  }

  // PY-8372
  public void testNewStyleAutomaticNumbering() {
    final List<SubstitutionChunk> chunks = filterSubstitutions(parseNewStyleFormat("{}, {}"));
    assertEquals(2, chunks.size());
    assertEquals(TextRange.create(0, 2), chunks.get(0).getTextRange());
    assertEquals(TextRange.create(4, 6), chunks.get(1).getTextRange());
  }

  // PY-8372
  public void testNewStylePositionalArgs() {
    final List<SubstitutionChunk> chunks = filterSubstitutions(parseNewStyleFormat("{1}, {0}"));
    assertEquals(2, chunks.size());
    assertEquals(TextRange.create(0, 3), chunks.get(0).getTextRange());
    assertEquals(TextRange.create(5, 8), chunks.get(1).getTextRange());
  }

  // PY-8372
  public void testNewStyleKeywordArgs() {
    final List<SubstitutionChunk> chunks = filterSubstitutions(parseNewStyleFormat("a{foo}{bar}bc"));
    assertEquals(2, chunks.size());
    assertEquals(TextRange.create(1, 6), chunks.get(0).getTextRange());
    assertEquals(TextRange.create(6, 11), chunks.get(1).getTextRange());
  }

  // PY-8372
  public void testBracesEscaping() {
    final List<SubstitutionChunk> chunks = filterSubstitutions(parseNewStyleFormat("\\{\\}, {{}}"));
    assertEquals(1, chunks.size());
    assertEquals(TextRange.create(1, 4), chunks.get(0).getTextRange());
  }

  public void testNewStyleConstant() {
    List<FormatStringChunk> chunks = parseNewStyleFormat("a");
    assertEquals(1, chunks.size());
    assertConstant(chunks.get(0), 0, 1);
  }
}
