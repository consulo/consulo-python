/**
 * @author VISTALL
 * @since 25/05/2023
 */
module consulo.python.language.api {
  requires transitive consulo.ide.api;

  requires consulo.application.api;
  requires consulo.application.content.api;
  requires consulo.base.icon.library;
  requires consulo.code.editor.api;
  requires consulo.color.scheme.api;
  requires consulo.component.api;
  requires consulo.disposer.api;
  requires consulo.document.api;
  requires consulo.execution.api;
  requires consulo.file.editor.api;
  requires consulo.language.api;
  requires consulo.language.code.style.api;
  requires consulo.language.editor.api;
  requires consulo.language.impl;
  requires consulo.localize.api;
  requires consulo.logging.api;
  requires consulo.module.api;
  requires consulo.navigation.api;
  requires consulo.platform.api;
  requires consulo.process.api;
  requires consulo.project.api;
  requires consulo.repository.ui.api;
  requires consulo.ui.api;
  requires consulo.util.collection;
  requires consulo.util.dataholder;
  requires consulo.util.io;
  requires consulo.util.lang;
  requires consulo.util.xml.serializer;
  requires consulo.virtual.file.system.api;

  requires com.google.common;
  requires it.unimi.dsi.fastutil;


  exports com.jetbrains.python;
  exports com.jetbrains.python.ast;
  exports com.jetbrains.python.ast.controlFlow;
  exports com.jetbrains.python.ast.docstring;
  exports com.jetbrains.python.ast.impl;
  exports com.jetbrains.python.codeInsight;
  exports com.jetbrains.python.codeInsight.controlflow;
  exports com.jetbrains.python.codeInsight.dataflow.scope;
  exports com.jetbrains.python.defaultProjectAwareService;
  exports com.jetbrains.python.documentation;
  exports com.jetbrains.python.documentation.docstrings;
  exports com.jetbrains.python.editor;
  exports com.jetbrains.python.formatter;
  exports com.jetbrains.python.highlighting;
  exports com.jetbrains.python.inspections;
  exports com.jetbrains.python.lexer;
  exports com.jetbrains.python.nameResolver;
  exports com.jetbrains.python.newProject;
  exports com.jetbrains.python.packaging;
  exports com.jetbrains.python.packaging.requirement;
  exports com.jetbrains.python.parsing;
  exports com.jetbrains.python.parsing.console;
  exports com.jetbrains.python.psi;
  exports com.jetbrains.python.psi.impl;
  exports com.jetbrains.python.psi.impl.stubs;
  exports com.jetbrains.python.psi.resolve;
  exports com.jetbrains.python.psi.stubs;
  exports com.jetbrains.python.psi.types;
  exports com.jetbrains.python.psi.types.engine;
  exports com.jetbrains.python.pyi;
  exports com.jetbrains.python.run;
  exports com.jetbrains.python.templateLanguages;
  exports com.jetbrains.python.templateLanguages.psi;
  exports com.jetbrains.python.toolbox;
  exports com.jetbrains.python.validation;
  exports consulo.python.psi.icon;
  exports consulo.python.psi.localize;

  opens com.jetbrains.python.codeInsight to consulo.util.xml.serializer;
  opens com.jetbrains.python.documentation to consulo.util.xml.serializer;
  opens com.jetbrains.python.formatter to consulo.util.xml.serializer;
}
