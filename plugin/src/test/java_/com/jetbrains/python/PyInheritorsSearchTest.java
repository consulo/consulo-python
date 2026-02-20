package com.jetbrains.python;

import consulo.project.Project;
import consulo.language.psi.PsiDocumentManager;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.impl.psi.search.PyClassInheritorsSearch;
import com.jetbrains.python.impl.psi.stubs.PyClassNameIndex;

import java.util.Collection;

/**
 * @author yole
 */
public abstract class PyInheritorsSearchTest extends PyTestCase {
  public void testSimple() throws Exception {
    setupProject();
    PyClass pyClass = findClass("A");
    Collection<PyClass> inheritors = PyClassInheritorsSearch.search(pyClass, false).findAll();
    assertEquals(2, inheritors.size());
  }

  public void testDeep() throws Exception {
    setupProject();
    PyClass pyClass = findClass("A");
    Collection<PyClass> inheritors = PyClassInheritorsSearch.search(pyClass, true).findAll();
    assertEquals(2, inheritors.size());
  }

  public void testDotted() throws Exception {
    setupProject();
    PyClass pyClass = findClass("A");
    Collection<PyClass> inheritors = PyClassInheritorsSearch.search(pyClass, true).findAll();
    assertEquals(1, inheritors.size());
  }

  private void setupProject() throws Exception {
    String testName = getTestName(true);
    myFixture.copyDirectoryToProject(testName, "");
    PsiDocumentManager.getInstance(myFixture.getProject()).commitAllDocuments();
  }

  private PyClass findClass(String name) {
    Project project = myFixture.getProject();
    Collection<PyClass> classes = PyClassNameIndex.find(name, project, false);
    assertEquals(1, classes.size());
    return classes.iterator().next();
  }

  @Override
  protected String getTestDataPath() {
    return PythonTestUtil.getTestDataPath() + "/inheritors/";
  }
}
