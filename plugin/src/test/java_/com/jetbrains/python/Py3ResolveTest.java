package com.jetbrains.python;

import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import com.intellij.testFramework.TestModuleDescriptor;
import com.jetbrains.python.fixtures.PyResolveTestCase;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.impl.psi.impl.PythonLanguageLevelPusher;

/**
 * @author yole
 */
public abstract class Py3ResolveTest extends PyResolveTestCase {
  @Override
  protected TestModuleDescriptor getProjectDescriptor() {
    return PyTestCase.ourPy3Descriptor;
  }

  @Override
  protected PsiElement doResolve() {
    myFixture.configureByFile("resolve/" + getTestName(false) + ".py");
    final PsiReference ref = PyResolveTestCase.findReferenceByMarker(myFixture.getFile());
    return ref.resolve();
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), LanguageLevel.PYTHON32);
  }

  @Override
  protected void tearDown() throws Exception {
    PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), null);
    super.tearDown();
  }

  public void testObjectMethods() {  // PY-1494
    assertResolvesTo(PyFunction.class, "__repr__");
  }

  // PY-5499
  public void testTrueDiv() {
    assertResolvesTo(PyFunction.class, "__truediv__");
  }
}
