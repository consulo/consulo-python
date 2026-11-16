package com.jetbrains.python.psi;


public class PyReparseableEndOfLineCommentType extends PyReparseableTokenTypeWithSimpleCheck {

  public PyReparseableEndOfLineCommentType(String debugName) {
    super(debugName);
  }

  @Override
  public boolean isReparseable(String newText) {
    return newText.startsWith("#");
  }
}
