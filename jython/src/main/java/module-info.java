/**
 * @author VISTALL
 * @since 18/06/2023
 */
module consulo.jython {
  requires consulo.ide.api;
  requires consulo.python.impl;

  requires consulo.java.language.api;
  requires consulo.java.language.impl;

  requires consulo.python.language.api;

  exports com.jetbrains.python.jython.psi.impl;
  exports com.jetbrains.python.jython.sdk.flavors;
  exports consulo.jython.icon;
  exports consulo.jython.module.extension;
}