// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import com.jetbrains.python.PythonFileType;
import consulo.language.ast.ASTNode;
import consulo.language.ast.IElementType;
import consulo.language.psi.PsiElement;
import org.jspecify.annotations.Nullable;

import java.util.function.Function;

public class PyElementType extends IElementType {
  private final Function<? super ASTNode, ? extends PsiElement> myPsiCreator;

  private final @Nullable String mySpecialMethodName;

  public PyElementType(String debugName) {
    super(debugName, PythonFileType.INSTANCE.getLanguage());
    myPsiCreator = node -> { throw new IllegalStateException("Cannot create an element for " + node.getElementType() + " without element class");};
    mySpecialMethodName = null;
  }

  public PyElementType(String debugName, Function<? super ASTNode, ? extends PsiElement> creator) {
    super(debugName, PythonFileType.INSTANCE.getLanguage());
    myPsiCreator = creator;
    mySpecialMethodName = null;
  }

  public PyElementType(String debugName, String specialMethodName) {
    super(debugName, PythonFileType.INSTANCE.getLanguage());
    myPsiCreator = node -> { throw new IllegalStateException("Cannot create an element for " + node.getElementType() + " without element class");};
    mySpecialMethodName = specialMethodName;
  }

  public PsiElement createElement(ASTNode node) {
    return myPsiCreator.apply(node);
  }

  /**
   * @return name of special method for operation marked by this token; e.g. "__add__" for "+".
   */
  public @Nullable String getSpecialMethodName() {
    return mySpecialMethodName;
  }

  @Override
  public String toString() {
    return "Py:" + super.toString();
  }
}
