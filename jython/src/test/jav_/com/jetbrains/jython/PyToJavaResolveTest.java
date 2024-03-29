package com.jetbrains.jython;

import com.intellij.java.language.psi.CommonClassNames;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiMethod;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiPackage;
import consulo.language.psi.PsiReference;
import junit.framework.Assert;

/**
 * @author yole
 */
@TestDataPath("$CONTENT_ROOT/../testData/resolve/pyToJava/")
public abstract class PyToJavaResolveTest extends UsefulTestCase
{
  private PsiElement resolve() throws Exception {
    PsiReference ref = null;//configureByFile(getTestName(false) + ".py");
    return ref.resolve();
  }

  public void testSimple() throws Exception {
    PsiElement target = resolve();
    Assert.assertTrue(target instanceof PsiClass);
    Assert.assertEquals("java.util.ArrayList", ((PsiClass) target).getQualifiedName());
  }

  public void testMethod() throws Exception {
    PsiElement target = resolve();
    Assert.assertTrue(target instanceof PsiMethod);
    Assert.assertEquals("java.util.ArrayList", ((PsiMethod) target).getContainingClass().getQualifiedName());
  }

  public void testField() throws Exception {
    PsiElement target = resolve();
    Assert.assertTrue(target instanceof PsiField);
    Assert.assertEquals("java.lang.System", ((PsiField) target).getContainingClass().getQualifiedName());
  }

  public void testReturnValue() throws Exception {
    PsiElement target = resolve();
    Assert.assertTrue(target instanceof PsiMethod);
    Assert.assertEquals(CommonClassNames.JAVA_UTIL_LIST, ((PsiMethod) target).getContainingClass().getQualifiedName());
  }

  public void testPackageType() throws Exception {
    PsiElement target = resolve();
    Assert.assertTrue(target instanceof PsiClass);
    Assert.assertEquals("java.util.ArrayList", ((PsiClass) target).getQualifiedName());
  }

  public void testJavaPackage() throws Exception {
    PsiElement target = resolve();
    Assert.assertTrue(target instanceof PsiPackage);
    Assert.assertEquals("java", ((PsiPackage) target).getQualifiedName());
  }

  public void testJavaLangPackage() throws Exception {
    PsiElement target = resolve();
    Assert.assertTrue(target instanceof PsiPackage);
    Assert.assertEquals("java.lang", ((PsiPackage) target).getQualifiedName());
  }

  public void testSuperMethod() throws Exception {
    PsiElement target = resolve();
    Assert.assertTrue(target instanceof PsiMethod);
    Assert.assertEquals("size", ((PsiMethod) target).getName());
  }

  public void testFieldType() throws Exception {
    PsiElement target = resolve();
    Assert.assertTrue(target instanceof PsiMethod);
    Assert.assertEquals("println", ((PsiMethod) target).getName());
  }


  protected String getTestDataPath() {
    return "/resolve/pyToJava/";
  }
}
