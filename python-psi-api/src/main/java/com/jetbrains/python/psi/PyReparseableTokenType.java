package com.jetbrains.python.psi;

import consulo.language.ast.ASTNode;
import consulo.language.ast.IReparseableLeafElementType;

public abstract class PyReparseableTokenType extends PyElementType implements IReparseableLeafElementType<ASTNode> {

  public PyReparseableTokenType(String debugName) {
    super(debugName);
  }
}
