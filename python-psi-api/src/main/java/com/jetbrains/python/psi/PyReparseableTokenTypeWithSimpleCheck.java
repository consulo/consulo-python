package com.jetbrains.python.psi;

import consulo.application.util.registry.Registry;
import consulo.language.ast.ASTNode;
import consulo.language.impl.ast.ASTFactory;
import consulo.logging.Logger;
import org.jspecify.annotations.Nullable;

public abstract class PyReparseableTokenTypeWithSimpleCheck extends PyReparseableTokenType {

  @SuppressWarnings("LoggerInitializedWithForeignClass")
  private static final Logger LOG = Logger.getInstance(PyReparseableTokenType.class);

  public PyReparseableTokenTypeWithSimpleCheck(String debugName) {
    super(debugName);
  }

  @Override
  public @Nullable ASTNode reparseLeaf(ASTNode leaf, CharSequence newText) {
    if (!Registry.is("python.ast.leaves.incremental.reparse", false)) {
      return null;
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug("Attempting to reparse leaf element of type" + this
                + "\nold text: \n" + leaf.getText()
                + "\n\nnew text: \n" + newText);
    }

    if (isReparseable(newText.toString())) {
      LOG.debug("Reparse is successful");
      return ASTFactory.leaf(this, newText);
    }
    LOG.debug("Reparse is declined");
    return null;
  }

  public abstract boolean isReparseable(String newText);
}
