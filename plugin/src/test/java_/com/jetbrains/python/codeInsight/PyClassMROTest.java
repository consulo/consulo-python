package com.jetbrains.python.codeInsight;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jakarta.annotation.Nonnull;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.TypeEvalContext;

/**
 * @author vlan
 */
public abstract class PyClassMROTest extends PyTestCase {
  public void testSimpleDiamond() {
    assertMRO(getClass("C"), "B1", "B2", "object");
  }

  // TypeError in Python
  public void testMROConflict() {
    assertMRO(getClass("C"));
  }

  public void testCircularInheritance() {
    final String testName = getTestName(false);
    myFixture.configureByFiles(getPath(testName), getPath(testName + "2"));
    final PyClass cls = myFixture.findElementByText("Foo", PyClass.class);
    assertNotNull(cls);
    assertMRO(cls);
  }

  public void testExampleFromDoc1() {
    assertMRO(getClass("A"), "B", "C", "D", "E", "F", "object");
  }

  public void testExampleFromDoc2() {
    assertMRO(getClass("A"), "B", "E", "C", "D", "F", "object");
  }

  public void testExampleFromDoc3() {
    assertMRO(getClass("G"));
  }

  public void testExampleFromDoc4() {
    assertMRO(getClass("G"), "E", "F", "object");
  }

  public void testSixWithMetaclass() {
    assertMRO(getClass("C"), "B", "object");
  }

  // PY-4183
  public void testComplicatedDiamond() {
    assertMRO(getClass("H"), "E", "F", "B", "G", "C", "D", "A", "object");
  }

  public void assertMRO(@Nonnull PyClass cls, @Nonnull String... mro) {
    final List<PyClassLikeType> types = cls.getAncestorTypes(TypeEvalContext.codeInsightFallback(myFixture.getProject()));
    final List<String> classNames = new ArrayList<String>();
    for (PyClassLikeType type : types) {
      if (type != null) {
        final String name = type.getName();
        if (name != null) {
          classNames.add(name);
          continue;
        }
      }
      classNames.add("unknown");
    }
    assertOrderedEquals(classNames, Arrays.asList(mro));
  }

  @Nonnull
  public PyClass getClass(@Nonnull String name) {
    myFixture.configureByFile(getPath(getTestName(false)));
    final PyClass cls = myFixture.findElementByText(name, PyClass.class);
    assertNotNull(cls);
    return cls;
  }

  private static String getPath(String name) {
    return "codeInsight/classMRO/" + name + ".py";
  }
}
