package com.jetbrains.python;

import java.util.Collections;

import com.jetbrains.python.impl.codeInsight.override.PyMethodMember;
import com.jetbrains.python.impl.codeInsight.override.PyOverrideImplementUtil;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.impl.psi.impl.PythonLanguageLevelPusher;
import com.jetbrains.python.impl.psi.stubs.PyClassNameIndex;

/**
 * @author yole
 */
public abstract class PyOverrideTest extends PyTestCase {
  private void doTest() {
    myFixture.configureByFile("override/" + getTestName(true) + ".py");
    PyFunction toOverride = getTopLevelClass(0).getMethods() [0];
    PyOverrideImplementUtil.overrideMethods(myFixture.getEditor(), getTopLevelClass(1),
                                            Collections.singletonList(new PyMethodMember(toOverride)), false);
    myFixture.checkResultByFile("override/" + getTestName(true) + "_after.py", true);
  }

  private void doTest3k() {
    PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), LanguageLevel.PYTHON32);
    try {
      doTest();
    }
    finally {
      PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), null);
    }
  }

  private PyClass getTopLevelClass(int index) {
    PyFile file = (PyFile) myFixture.getFile();
    return file.getTopLevelClasses().get(index);
  }

  public void testSimple() {
    doTest();
  }

  public void testClassmethod() {
    doTest();
  }

  public void testNewStyle() {
    doTest();
  }

  public void testReturnValue() {  // PY-1537
    doTest();
  }

  public void testClassmethodNewStyle() {  // PY-1811
    doTest();
  }

  public void testIndent() {  // PY-1796
    doTest();
  }

  public void testInnerClass() {  // PY-10976
    myFixture.configureByFile("override/" + getTestName(true) + ".py");
    PyFunction toOverride = getTopLevelClass(0).getMethods()[0];
    PyClass pyClass = getTopLevelClass(1).getNestedClasses()[0];
    PyOverrideImplementUtil.overrideMethods(myFixture.getEditor(), pyClass,
                                            Collections.singletonList(new PyMethodMember(toOverride)), false);
    myFixture.checkResultByFile("override/" + getTestName(true) + "_after.py", true);
  }

  public void testQualified() {  // PY-2171
    myFixture.configureByFile("override/" + getTestName(true) + ".py");
    PyClass dateClass = PyClassNameIndex.findClass("datetime.date", myFixture.getProject());
    assertNotNull(dateClass);
    PyFunction initMethod = dateClass.findMethodByName(PyNames.INIT, false, null);
    assertNotNull(initMethod);
    PyOverrideImplementUtil.overrideMethods(myFixture.getEditor(), getTopLevelClass(0),
                                            Collections.singletonList(new PyMethodMember(initMethod)), false);
    myFixture.checkResultByFile("override/" + getTestName(true) + "_after.py", true);
  }

  public void testImplement() {
    myFixture.configureByFile("override/" + getTestName(true) + ".py");
    PyFunction toImplement = getTopLevelClass(0).getMethods()[1];
    PyOverrideImplementUtil.overrideMethods(myFixture.getEditor(), getTopLevelClass(1),
                                            Collections.singletonList(new PyMethodMember(toImplement)), true);
    myFixture.checkResultByFile("override/" + getTestName(true) + "_after.py", true);
  }

  public void testPy3k() {
    doTest3k();
  }

  public void testTypeAnnotations() {  // PY-2547
    doTest3k();
  }

  public void testReturnAnnotation() {  // PY-2690
    doTest3k();
  }

  public void testSingleStar() {  // PY-6455
    doTest3k();
  }

  public void testStarArgs() {  // PY-6455
    doTest3k();
  }

  public void testKwargs() {  // PY-7401
    doTest3k();
  }

  // PY-10229
  public void testInstanceCheck() {
    myFixture.configureByFile("override/" + getTestName(true) + ".py");
    final PyClass cls = getTopLevelClass(0);
    final PyFunction method = cls.findMethodByName("__instancecheck__", true, null);
    PyOverrideImplementUtil.overrideMethods(myFixture.getEditor(), cls, Collections.singletonList(new PyMethodMember(method)), false);
    myFixture.checkResultByFile("override/" + getTestName(true) + "_after.py", true);
  }
}
