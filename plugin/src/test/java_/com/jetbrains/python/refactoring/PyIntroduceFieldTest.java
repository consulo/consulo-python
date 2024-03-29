package com.jetbrains.python.refactoring;

import com.intellij.testFramework.TestDataPath;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.impl.refactoring.introduce.IntroduceHandler;
import com.jetbrains.python.impl.refactoring.introduce.IntroduceOperation;
import com.jetbrains.python.impl.refactoring.introduce.field.PyIntroduceFieldHandler;
import consulo.ide.impl.idea.util.Consumer;

/**
 * @author yole
 */
@TestDataPath("$CONTENT_ROOT/../testData/refactoring/introduceField/")
public abstract class PyIntroduceFieldTest extends PyIntroduceTestCase {
  public void testMetaClass() {  // PY-1580
    doTest(new IntroduceOperationCustomizer(IntroduceHandler.InitPlace.SAME_METHOD, false));
  }

  public void testInConstructor() {  // PY-1983
    doTest(new IntroduceOperationCustomizer(IntroduceHandler.InitPlace.CONSTRUCTOR, false));
  }

  public void testVariableToField() {
    doTest(new IntroduceOperationCustomizer(IntroduceHandler.InitPlace.CONSTRUCTOR, false));
  }
  
  public void testUniqueName() {  // PY-4409
    doTestSuggestions(PyExpression.class, "s1");
  }

  public void testPy4453() {
    doTestInplace(null);
  }

  public void testPy4414() {
    doTestInplace(null);
  }

  public void testPy4437() {
    doTestInplace(new IntroduceOperationCustomizer(IntroduceHandler.InitPlace.CONSTRUCTOR, true));
  }

  @Override
  protected IntroduceHandler createHandler() {
    return new PyIntroduceFieldHandler();
  }

  @Override
  protected String getTestDataPath() {
    return super.getTestDataPath() + "/refactoring/introduceField/";
  }

  private static class IntroduceOperationCustomizer implements Consumer<IntroduceOperation>
  {
    private final IntroduceHandler.InitPlace myPlace;
    private final boolean myInplace;

    private IntroduceOperationCustomizer(IntroduceHandler.InitPlace place, boolean inplace) {
      myPlace = place;
      myInplace = inplace;
    }

    @Override
    public void consume(IntroduceOperation operation) {
      if (myInplace) {
        operation.setInplaceInitPlace(myPlace);
      }
      else {
        operation.setInitPlace(myPlace);
      }
    }
  }
}
