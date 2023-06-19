/**
 * @author VISTALL
 * @since 18/06/2023
 */
module consulo.iron.python {
  requires consulo.ide.api;
  requires consulo.python.impl;

  requires consulo.dotnet.psi.api;

  requires consulo.python.language.api;

  exports consulo.ironPython.module.extension;
  exports consulo.ironPython.psi.impl;
  exports consulo.ironPython.sdk.flavors;
}