/**
 * @author VISTALL
 * @since 18/06/2023
 */
module consulo.python.rest {
  requires consulo.ide.api;
  requires consulo.python.language.api;
  requires consulo.python.impl;

  requires org.jetbrains.plugins.rest;
  
  // TODO remove in future
  requires forms.rt;
  requires java.desktop;
  requires consulo.ide.impl;
}