/**
 * @author VISTALL
 * @since 18/06/2023
 */
module consulo.python.rest {
  requires consulo.application.api;
  requires consulo.application.content.api;
  requires consulo.code.editor.api;
  requires consulo.color.scheme.api;
  requires consulo.configurable.api;
  requires consulo.document.api;
  requires consulo.execution.api;
  requires consulo.file.chooser.api;
  requires consulo.file.editor.api;
  requires consulo.language.api;
  requires consulo.language.editor.api;
  requires consulo.language.impl;
  requires consulo.localize.api;
  requires consulo.module.api;
  requires consulo.platform.api;
  requires consulo.process.api;
  requires consulo.project.api;
  requires consulo.ui.api;
  requires consulo.ui.ex.api;
  requires consulo.ui.ex.awt.api;
  requires consulo.util.io;
  requires consulo.util.lang;
  requires consulo.util.xml.serializer;
  requires consulo.virtual.file.system.api;

  requires consulo.python.language.api;
  requires consulo.python.impl;

  requires com.google.common;

  requires org.jetbrains.plugins.rest;

  requires forms.rt;
  requires java.desktop;
  requires org.jdom;

  // TODO remove in future - for PathMappingSettings
  requires consulo.ide.impl;
  requires consulo.repository.ui.api;
}
