package com.jetbrains.python;

import consulo.util.lang.Pair;
import consulo.language.psi.PsiElement;
import com.jetbrains.python.fixtures.LightMarkedTestCase;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PySubscriptionExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import junit.framework.Assert;

import java.util.List;
import java.util.Map;

/**
 * Tests assignment mapping.
 * User: dcheryasov
 * Date: Dec 11, 2009 2:13:51 AM
 */
public abstract class PyAssignmentMappingTest extends LightMarkedTestCase {

  @Override
  public String getTestDataPath() {
    return PythonTestUtil.getTestDataPath() + "/psi/assignment/";
  }


  public void testSimple() throws Exception {
    Map<String, PsiElement> marks = loadTest();
    Assert.assertEquals(2, marks.size());
    PsiElement src = marks.get("<src>").getParent(); // const -> expr;
    PsiElement dst = marks.get("<dst>").getParent(); // ident -> target expr
    Assert.assertTrue(dst instanceof PyTargetExpression);
    PyAssignmentStatement stmt = (PyAssignmentStatement)dst.getParent();
    List<Pair<PyExpression, PyExpression>> mapping = stmt.getTargetsToValuesMapping();
    Assert.assertEquals(1, mapping.size());
    Pair<PyExpression, PyExpression> pair = mapping.get(0);
    Assert.assertEquals(dst, pair.getFirst());
    Assert.assertEquals(src, pair.getSecond());
  }

  public void testSubscribedSource() throws Exception {
    Map<String, PsiElement> marks = loadTest();
    Assert.assertEquals(2, marks.size());
    PsiElement src = marks.get("<src>").getParent().getParent(); // const -> ref foo -> subscr expr;
    PsiElement dst = marks.get("<dst>").getParent(); // ident -> target expr
    Assert.assertTrue(dst instanceof PyTargetExpression);
    PyAssignmentStatement stmt = (PyAssignmentStatement)dst.getParent();
    List<Pair<PyExpression, PyExpression>> mapping = stmt.getTargetsToValuesMapping();
    Assert.assertEquals(1, mapping.size());
    Pair<PyExpression, PyExpression> pair = mapping.get(0);
    Assert.assertEquals(dst, pair.getFirst());
    Assert.assertEquals(src, pair.getSecond());
  }

  public void testSubscribedTarget() throws Exception {
    Map<String, PsiElement> marks = loadTest();
    Assert.assertEquals(2, marks.size());
    PsiElement src = marks.get("<src>").getParent(); // const -> expr;
    PsiElement dst = marks.get("<dst>").getParent().getParent(); // ident -> target expr
    Assert.assertTrue(dst instanceof PySubscriptionExpression);
    PyAssignmentStatement stmt = (PyAssignmentStatement)src.getParent();
    List<Pair<PyExpression, PyExpression>> mapping = stmt.getTargetsToValuesMapping();
    Assert.assertEquals(1, mapping.size());
    Pair<PyExpression, PyExpression> pair = mapping.get(0);
    Assert.assertEquals(dst, pair.getFirst());
    Assert.assertEquals(src, pair.getSecond());
  }


  public void testMultiple() throws Exception {
    Map<String, PsiElement> marks = loadTest();
    final int TARGET_NUM = 3;
    Assert.assertEquals(TARGET_NUM + 1, marks.size());
    PsiElement src = marks.get("<src>").getParent(); // const -> expr;
    PsiElement[] dsts = new PsiElement[TARGET_NUM];
    for (int i=0; i<TARGET_NUM; i+=1) {
      PsiElement dst = marks.get("<dst" + String.valueOf(i+1) +">").getParent(); // ident -> target expr
      Assert.assertTrue(dst instanceof PyTargetExpression);
      dsts[i] = dst;
    }
    PyAssignmentStatement stmt = (PyAssignmentStatement)src.getParent();
    List<Pair<PyExpression, PyExpression>> mapping = stmt.getTargetsToValuesMapping();
    Assert.assertEquals(TARGET_NUM, mapping.size());
    for (int i=0; i<TARGET_NUM; i+=1) {
      Pair<PyExpression, PyExpression> pair = mapping.get(i);
      Assert.assertEquals(dsts[i], pair.getFirst());
      Assert.assertEquals(src, pair.getSecond());
    }
  }

  public void testTupleMapped() throws Exception {
    Map<String, PsiElement> marks = loadTest();
    final int PAIR_NUM = 2;
    Assert.assertEquals(PAIR_NUM * 2, marks.size());
    PsiElement[] srcs = new PsiElement[PAIR_NUM];
    PsiElement[] dsts = new PsiElement[PAIR_NUM];
    for (int i=0; i<PAIR_NUM; i+=1) {
      PsiElement dst = marks.get("<dst" + String.valueOf(i+1) +">").getParent(); // ident -> target expr
      Assert.assertTrue(dst instanceof PyTargetExpression);
      dsts[i] = dst;
      PsiElement src = marks.get("<src" + String.valueOf(i+1) +">").getParent(); // ident -> target expr
      Assert.assertTrue(src instanceof PyExpression);
      srcs[i] = src;
    }
    PyAssignmentStatement stmt = (PyAssignmentStatement)srcs[0].getParent().getParent(); // tuple expr -> assignment
    List<Pair<PyExpression, PyExpression>> mapping = stmt.getTargetsToValuesMapping();
    Assert.assertEquals(PAIR_NUM, mapping.size());
    for (int i=0; i<PAIR_NUM; i+=1) {
      Pair<PyExpression, PyExpression> pair = mapping.get(i);
      Assert.assertEquals(dsts[i], pair.getFirst());
      Assert.assertEquals(srcs[i], pair.getSecond());
    }
  }

  public void testParenthesizedTuple() throws Exception { //PY-2648
    Map<String, PsiElement> marks = loadTest();
    final int PAIR_NUM = 2;
    Assert.assertEquals(PAIR_NUM * 2, marks.size());
    PsiElement[] srcs = new PsiElement[PAIR_NUM];
    PsiElement[] dsts = new PsiElement[PAIR_NUM];
    for (int i=0; i<PAIR_NUM; i+=1) {
      PsiElement dst = marks.get("<dst" + String.valueOf(i + 1) + ">").getParent(); // ident -> target expr
      Assert.assertTrue(dst instanceof PyTargetExpression);
      dsts[i] = dst;
      PsiElement src = marks.get("<src" + String.valueOf(i + 1) + ">").getParent(); // ident -> target expr
      Assert.assertTrue(src instanceof PyExpression);
      srcs[i] = src;
    }
    PyAssignmentStatement stmt = (PyAssignmentStatement)srcs[0].getParent().getParent().getParent(); // tuple expr -> assignment
    List<Pair<PyExpression, PyExpression>> mapping = stmt.getTargetsToValuesMapping();
    Assert.assertEquals(PAIR_NUM, mapping.size());
    for (int i=0; i<PAIR_NUM; i+=1) {
      Pair<PyExpression, PyExpression> pair = mapping.get(i);
      Assert.assertEquals(dsts[i], pair.getFirst());
      Assert.assertEquals(srcs[i], pair.getSecond());
    }
  }

  public void testTuplePack() throws Exception {
    Map<String, PsiElement> marks = loadTest();
    final int SRC_NUM = 2;
    Assert.assertEquals(SRC_NUM + 1, marks.size());
    PsiElement[] srcs = new PsiElement[SRC_NUM];
    for (int i=0; i<SRC_NUM; i+=1) {
      PsiElement src = marks.get("<src" + String.valueOf(i+1) +">").getParent(); // ident -> target expr
      Assert.assertTrue(src instanceof PyExpression);
      srcs[i] = src;
    }
    PsiElement dst = marks.get("<dst>").getParent(); // ident -> target expr
    PyAssignmentStatement stmt = (PyAssignmentStatement)dst.getParent();
    List<Pair<PyExpression, PyExpression>> mapping = stmt.getTargetsToValuesMapping();
    Assert.assertEquals(1, mapping.size());
    Pair<PyExpression, PyExpression> pair = mapping.get(0);
    Assert.assertEquals(dst, pair.getFirst());
    for (PsiElement src : srcs) {
      Assert.assertEquals(src.getParent(), pair.getSecond()); // numeric expr -> tuple
    }
  }


  public void testTupleUnpack() throws Exception {
    Map<String, PsiElement> marks = loadTest();
    final int DST_NUM = 2;
    Assert.assertEquals(DST_NUM + 3, marks.size());
    PsiElement[] dsts = new PsiElement[DST_NUM];
    for (int i=0; i<DST_NUM; i+=1) {
      PsiElement dst = marks.get("<dst" + String.valueOf(i+1) +">").getParent(); // ident -> target expr
      Assert.assertTrue(dst instanceof PyTargetExpression);
      dsts[i] = dst;
    }
    PsiElement[] srcs = new PsiElement[DST_NUM];
    for (int i=0; i<DST_NUM; i+=1) {
      PsiElement src = marks.get("<src" + String.valueOf(i+1) +">").getParent().getParent().getParent(); // ident -> target expr
      Assert.assertTrue(src instanceof PyExpression);
      srcs[i] = src;
    }

    PsiElement src = marks.get("<src>").getParent(); // ident -> target expr
    PyAssignmentStatement stmt = (PyAssignmentStatement)src.getParent().getParent();
    List<Pair<PyExpression, PyExpression>> mapping = stmt.getTargetsToValuesMapping();
    Assert.assertEquals(DST_NUM, mapping.size());
    for (int i=0; i<DST_NUM; i+=1) {
      Pair<PyExpression, PyExpression> pair = mapping.get(i);
      Assert.assertEquals(dsts[i], pair.getFirst());
      Assert.assertEquals(srcs[i].getText(), pair.getSecond().getText());
    }
  }
}
