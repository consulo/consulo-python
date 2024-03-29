package com.jetbrains.python;

import consulo.colorScheme.EditorColorsManager;
import consulo.colorScheme.EditorColorsScheme;
import consulo.colorScheme.TextAttributesKey;
import consulo.colorScheme.EffectType;
import consulo.colorScheme.TextAttributes;
import com.jetbrains.python.impl.documentation.PyDocumentationSettings;
import com.jetbrains.python.impl.documentation.docstrings.DocStringFormat;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.impl.psi.impl.PythonLanguageLevelPusher;
import consulo.ui.style.StandardColors;

import java.awt.*;

/**
 * Test highlighting added by annotators.
 *
 * @author yole
 */
public abstract class PythonHighlightingTest extends PyTestCase {
  private static final String TEST_PATH = "/highlighting/";

  public void testBuiltins() {
    EditorColorsManager manager = EditorColorsManager.getInstance();
    EditorColorsScheme scheme = (EditorColorsScheme)manager.getGlobalScheme().clone();
    manager.addColorsScheme(scheme);
    EditorColorsManager.getInstance().setGlobalScheme(scheme);

    TextAttributesKey xKey;
    TextAttributes xAttributes;
    
    xKey = TextAttributesKey.find("PY.BUILTIN_NAME");
    xAttributes = new TextAttributes(StandardColors.GREEN, StandardColors.BLACK, StandardColors.WHITE, EffectType.BOXED, Font.BOLD);
    scheme.setAttributes(xKey, xAttributes);

    xKey = TextAttributesKey.find("PY.PREDEFINED_USAGE");
    xAttributes = new TextAttributes(StandardColors.YELLOW, StandardColors.BLACK, StandardColors.WHITE, EffectType.BOXED, Font.BOLD);
    scheme.setAttributes(xKey, xAttributes);

    doTest();
  }

  public void testDeclarations() {
    EditorColorsManager manager = EditorColorsManager.getInstance();
    EditorColorsScheme scheme = (EditorColorsScheme)manager.getGlobalScheme().clone();
    manager.addColorsScheme(scheme);
    EditorColorsManager.getInstance().setGlobalScheme(scheme);

    TextAttributesKey xKey = TextAttributesKey.find("PY.CLASS_DEFINITION");
    TextAttributes xAttributes = new TextAttributes(StandardColors.BLUE, StandardColors.BLACK, StandardColors.WHITE, EffectType.BOXED, Font.BOLD);
    scheme.setAttributes(xKey, xAttributes);

    xKey = TextAttributesKey.find("PY.FUNC_DEFINITION");
    xAttributes = new TextAttributes(StandardColors.RED, StandardColors.BLACK, StandardColors.WHITE, EffectType.BOXED, Font.BOLD);
    scheme.setAttributes(xKey, xAttributes);

    xKey = TextAttributesKey.find("PY.PREDEFINED_DEFINITION");
    xAttributes = new TextAttributes(StandardColors.GREEN, StandardColors.BLACK, StandardColors.WHITE, EffectType.BOXED, Font.BOLD);
    scheme.setAttributes(xKey, xAttributes);

    doTest();
  }

  public void testAssignmentTargets() {
    setLanguageLevel(LanguageLevel.PYTHON26);
    doTest(true, false);
  }

  public void testAssignmentTargetWith() {  // PY-7529
    setLanguageLevel(LanguageLevel.PYTHON27);
    doTest(true, false);
  }

  public void testAssignmentTargets3K() {
    doTest(LanguageLevel.PYTHON30, true, false);    
  }
  
  public void testBreakOutsideOfLoop() {
    doTest(true, false);
  }

  public void testReturnOutsideOfFunction() {
    doTest();
  }

  public void testContinueInFinallyBlock() {
    doTest(false, false);
  }

  public void testReturnWithArgumentsInGenerator() {
    doTest();
  }

  public void testYieldOutsideOfFunction() {
    doTest();
  }
  
  public void testImportStarAtTopLevel() {
    doTest(true, false);
  }

  public void testMalformedStringUnterminated() {
    doTest();
  }

  public void testMalformedStringEscaped() {
    doTest(false, false);
  }

  /*
  public void testStringEscapedOK() {
    doTest();
  }
  */

  public void testStringMixedSeparatorsOK() {   // PY-299
    doTest();
  }

  public void testStringBytesLiteralOK() {
    doTest(LanguageLevel.PYTHON26, true, true);
  }

  public void testArgumentList() {
    doTest(true, false);
  }

  public void testRegularAfterVarArgs() {
    doTest(LanguageLevel.PYTHON30, true, false);
  }

  public void testKeywordOnlyArguments() {
    doTest(LanguageLevel.PYTHON30, true, false);
  }

  public void testMalformedStringTripleQuoteUnterminated() {
    doTest();
  }

  public void testMixedTripleQuotes() {   // PY-2806
    doTest();
  }

  public void testOddNumberOfQuotes() {  // PY-2802
    doTest(true, false);
  }

  public void testEscapedBackslash() {  // PY-2994
    doTest(true, false);
  }

  public void testUnsupportedFeaturesInPython3() {
    doTest(LanguageLevel.PYTHON30, true, false);
  }

  // PY-6703
  public void testUnicode33() {
    doTest(LanguageLevel.PYTHON33, true, false);
  }

  // PY-6702
  public void testYieldFromBefore33() {
    doTest(LanguageLevel.PYTHON32, true, false);
  }

  public void testParenthesizedGenerator() {
    doTest(false, false);
  }

  public void testStarArgs() {  // PY-6456
    doTest(LanguageLevel.PYTHON32, true, false);
  }

  public void testDocstring() {  // PY-8025
    PyDocumentationSettings documentationSettings = PyDocumentationSettings.getInstance(myFixture.getModule());
    documentationSettings.setFormat(DocStringFormat.REST);
    try {
      doTest(false, true);
    }
    finally {
      documentationSettings.setFormat(DocStringFormat.PLAIN);
    }
  }

  public void testYieldInNestedFunction() {
    // highlight func declaration first, lest we get an "Extra fragment highlighted" error.
    EditorColorsManager manager = EditorColorsManager.getInstance();
    EditorColorsScheme scheme = (EditorColorsScheme)manager.getGlobalScheme().clone();
    manager.addColorsScheme(scheme);
    EditorColorsManager.getInstance().setGlobalScheme(scheme);

    TextAttributesKey xKey = TextAttributesKey.find("PY.FUNC_DEFINITION");
    TextAttributes xAttributes = new TextAttributes(StandardColors.RED, StandardColors.BLACK, StandardColors.WHITE, EffectType.BOXED, Font.BOLD);
    scheme.setAttributes(xKey, xAttributes);

    doTest();
  }

  // ---
  private void doTest(final LanguageLevel languageLevel, final boolean checkWarnings, final boolean checkInfos) {
    PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), languageLevel);
    try {
      doTest(checkWarnings, checkInfos);
    }
    finally {
      PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), null);
    }
  }

  private void doTest() {
    final String TEST_PATH = "/highlighting/";
    myFixture.testHighlighting(true, true, false, TEST_PATH + getTestName(true) + PyNames.DOT_PY);
  }

  private void doTest(boolean checkWarnings, boolean checkInfos) {
    myFixture.testHighlighting(checkWarnings, checkInfos, false, TEST_PATH + getTestName(true) + PyNames.DOT_PY);
  }

}
