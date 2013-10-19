package com.jetbrains.python.psi;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.jetbrains.python.PythonFileType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public abstract class PyStubElementType<StubT extends StubElement, PsiT extends PyElement> extends IStubElementType<StubT, PsiT> {
  public PyStubElementType(@NonNls String debugName) {
    super(debugName, PythonFileType.INSTANCE.getLanguage());
  }

  @Override
  public String toString() {
    return "Py:" + super.toString();
  }

  public abstract PsiElement createElement(@NotNull final ASTNode node);

  public void indexStub(@NotNull final StubT stub, @NotNull final IndexSink sink) {
  }

  @NotNull
  public String getExternalId() {
    return "py." + super.toString();
  }
}