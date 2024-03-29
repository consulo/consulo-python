package com.jetbrains.python;

import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.impl.psi.impl.PythonLanguageLevelPusher;
import com.jetbrains.python.toolbox.Maybe;

public abstract class PyDecoratedPropertyTest extends PyTestCase {
  protected PyClass myClass;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), LanguageLevel.PYTHON26);
    final PyFile file = (PyFile)myFixture.configureByFile("property/Decorated.py");
    myClass = file.getTopLevelClasses().get(0);
  }

  public void testW1() throws Exception {
    Property p;
    Maybe<PyCallable> accessor;
    final String name = "w1";
    p = myClass.findProperty(name, true, null);
    assertNotNull(p);
    assertNull(p.getDoc());
    assertNull(p.getDefinitionSite());

    accessor = p.getGetter();
    assertTrue(accessor.isDefined());
    assertNotNull(accessor.value());
    assertEquals(name, accessor.value().getName());

    accessor = p.getSetter();
    assertTrue(accessor.isDefined());
    assertNotNull(accessor.value());
    assertEquals(name, accessor.value().getName());

    accessor = p.getDeleter();
    assertTrue(accessor.isDefined());
    assertNotNull(accessor.value());
    assertEquals(name, accessor.value().getName());
  }

  public void testW2() throws Exception {
    Property p;
    Maybe<PyCallable> accessor;
    final String name = "w2";
    p = myClass.findProperty(name, true, null);
    assertNotNull(p);
    assertNull(p.getDoc());
    assertNull(p.getDefinitionSite());

    accessor = p.getGetter();
    assertTrue(accessor.isDefined());
    final PyCallable callable = accessor.value();
    assertNotNull(callable);
    assertEquals("w2", callable.getName());
    assertInstanceOf(callable, PyFunction.class);
    assertEquals("doc of " + name, ((PyFunction)callable).getDocStringExpression().getStringValue());

    accessor = p.getSetter();
    assertTrue(accessor.isDefined());
    assertNull(accessor.value());

    accessor = p.getDeleter();
    assertTrue(accessor.isDefined());
    assertNull(accessor.value());
  }

}
