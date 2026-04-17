/**
 * @author VISTALL
 * @since 18/06/2023
 */
module consulo.iron.python {
  requires consulo.application.api;
  requires consulo.application.content.api;
  requires consulo.disposer.api;
  requires consulo.language.api;
  requires consulo.language.editor.api;
  requires consulo.module.api;
  requires consulo.module.content.api;
  requires consulo.module.ui.api;
  requires consulo.process.api;
  requires consulo.project.api;
  requires consulo.project.content.api;
  requires consulo.ui.api;
  requires consulo.util.collection;
  requires consulo.util.lang;

  requires consulo.python.impl;
  requires consulo.python.language.api;

  requires consulo.dotnet.psi.api;

  exports consulo.ironPython.module.extension;
  exports consulo.ironPython.psi.impl;
  exports consulo.ironPython.sdk.flavors;
}
