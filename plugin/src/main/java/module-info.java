/**
 * @author VISTALL
 * @since 18/06/2023
 */
module consulo.python {
  requires consulo.ide.api;
  requires consulo.python.language.api;
  requires consulo.python.impl;

  // TODO remove in future
  requires consulo.ide.impl;
}