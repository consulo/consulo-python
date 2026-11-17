/**
 * @author VISTALL
 * @since 18/06/2023
 */
module consulo.python {
  requires consulo.document.api;
  requires consulo.language.api;
  requires consulo.language.inject.advanced.api;
  requires consulo.project.api;
  requires consulo.ui.api;
  requires consulo.ui.ex.api;

  requires consulo.python.language.api;
  requires consulo.python.impl;

  requires org.jdom;
}
