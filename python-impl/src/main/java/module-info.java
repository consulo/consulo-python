/**
 * @author VISTALL
 * @since 25/05/2023
 */
open module consulo.python.impl
{
  requires consulo.ide.api;

  requires consulo.python.language.api;
  requires consulo.python.debugger;

  requires com.google.common;
  requires com.google.gson;

  requires com.intellij.regexp;

  requires xmlrpc.common;

  // TODO remove in future
  requires consulo.ide.impl;

  requires java.desktop;
  requires forms.rt;
  exports com.jetbrains.numpy.codeInsight;
  exports com.jetbrains.numpy.documentation;
  exports com.jetbrains.pyqt;
  exports com.jetbrains.python.impl;
  exports com.jetbrains.python.impl.actions;
  exports com.jetbrains.python.impl.buildout;
  exports com.jetbrains.python.impl.buildout.config;
  exports com.jetbrains.python.impl.buildout.config.inspection;
  exports com.jetbrains.python.impl.buildout.config.lexer;
  exports com.jetbrains.python.impl.buildout.config.psi;
  exports com.jetbrains.python.impl.buildout.config.psi.impl;
  exports com.jetbrains.python.impl.buildout.config.ref;
  exports com.jetbrains.python.impl.codeInsight;
  exports com.jetbrains.python.impl.codeInsight.codeFragment;
  exports com.jetbrains.python.impl.codeInsight.completion;
  exports com.jetbrains.python.impl.codeInsight.controlflow;
  exports com.jetbrains.python.impl.codeInsight.dataflow;
  exports com.jetbrains.python.impl.codeInsight.dataflow.scope;
  exports com.jetbrains.python.impl.codeInsight.dataflow.scope.impl;
  exports com.jetbrains.python.impl.codeInsight.editorActions.moveUpDown;
  exports com.jetbrains.python.impl.codeInsight.editorActions.smartEnter;
  exports com.jetbrains.python.impl.codeInsight.editorActions.smartEnter.enterProcessors;
  exports com.jetbrains.python.impl.codeInsight.editorActions.smartEnter.fixers;
  exports com.jetbrains.python.impl.codeInsight.highlighting;
  exports com.jetbrains.python.impl.codeInsight.imports;
  exports com.jetbrains.python.impl.codeInsight.intentions;
  exports com.jetbrains.python.impl.codeInsight.liveTemplates;
  exports com.jetbrains.python.impl.codeInsight.override;
  exports com.jetbrains.python.impl.codeInsight.regexp;
  exports com.jetbrains.python.impl.codeInsight.stdlib;
  exports com.jetbrains.python.impl.codeInsight.testIntegration;
  exports com.jetbrains.python.impl.codeInsight.userSkeletons;
  exports com.jetbrains.python.impl.configuration;
  exports com.jetbrains.python.impl.console;
  exports com.jetbrains.python.impl.console.actions;
  exports com.jetbrains.python.impl.console.completion;
  exports com.jetbrains.python.impl.console.parsing;
  exports com.jetbrains.python.impl.debugger;
  exports com.jetbrains.python.impl.debugger.array;
  exports com.jetbrains.python.impl.debugger.containerview;
  exports com.jetbrains.python.impl.debugger.dataframe;
  exports com.jetbrains.python.impl.debugger.settings;
  exports com.jetbrains.python.impl.documentation;
  exports com.jetbrains.python.impl.documentation.docstrings;
  exports com.jetbrains.python.impl.documentation.doctest;
  exports com.jetbrains.python.impl.editor;
  exports com.jetbrains.python.impl.editor.selectWord;
  exports com.jetbrains.python.impl.facet;
  exports com.jetbrains.python.impl.findUsages;
  exports com.jetbrains.python.impl.formatter;
  exports com.jetbrains.python.impl.hierarchy;
  exports com.jetbrains.python.impl.hierarchy.treestructures;
  exports com.jetbrains.python.impl.highlighting;
  exports com.jetbrains.python.impl.inspections;
  exports com.jetbrains.python.impl.inspections.quickfix;
  exports com.jetbrains.python.impl.inspections.unresolvedReference;
  exports com.jetbrains.python.impl.lexer;
  exports com.jetbrains.python.impl.magicLiteral;
  exports com.jetbrains.python.impl.packaging;
  exports com.jetbrains.python.impl.packaging.setupPy;
  exports com.jetbrains.python.impl.packaging.ui;
  exports com.jetbrains.python.impl.parsing;
  exports com.jetbrains.python.impl.patterns;
  exports com.jetbrains.python.impl.projectView;
  exports com.jetbrains.python.impl.psi;
  exports com.jetbrains.python.impl.psi.impl;
  exports com.jetbrains.python.impl.psi.impl.references;
  exports com.jetbrains.python.impl.psi.impl.stubs;
  exports com.jetbrains.python.impl.psi.resolve;
  exports com.jetbrains.python.impl.psi.search;
  exports com.jetbrains.python.impl.psi.stubs;
  exports com.jetbrains.python.impl.psi.types;
  exports com.jetbrains.python.impl.psi.types.functionalParser;
  exports com.jetbrains.python.impl.refactoring;
  exports com.jetbrains.python.impl.refactoring.changeSignature;
  exports com.jetbrains.python.impl.refactoring.classes;
  exports com.jetbrains.python.impl.refactoring.classes.extractSuperclass;
  exports com.jetbrains.python.impl.refactoring.classes.membersManager;
  exports com.jetbrains.python.impl.refactoring.classes.membersManager.vp;
  exports com.jetbrains.python.impl.refactoring.classes.pullUp;
  exports com.jetbrains.python.impl.refactoring.classes.pushDown;
  exports com.jetbrains.python.impl.refactoring.classes.ui;
  exports com.jetbrains.python.impl.refactoring.extractmethod;
  exports com.jetbrains.python.impl.refactoring.inline;
  exports com.jetbrains.python.impl.refactoring.introduce;
  exports com.jetbrains.python.impl.refactoring.introduce.constant;
  exports com.jetbrains.python.impl.refactoring.introduce.field;
  exports com.jetbrains.python.impl.refactoring.introduce.parameter;
  exports com.jetbrains.python.impl.refactoring.introduce.variable;
  exports com.jetbrains.python.impl.refactoring.invertBoolean;
  exports com.jetbrains.python.impl.refactoring.move;
  exports com.jetbrains.python.impl.refactoring.move.moduleMembers;
  exports com.jetbrains.python.impl.refactoring.rename;
  exports com.jetbrains.python.impl.refactoring.surround;
  exports com.jetbrains.python.impl.refactoring.surround.surrounders.expressions;
  exports com.jetbrains.python.impl.refactoring.surround.surrounders.statements;
  exports com.jetbrains.python.impl.refactoring.unwrap;
  exports com.jetbrains.python.impl.remote;
  exports com.jetbrains.python.impl.run;
  exports com.jetbrains.python.impl.sdk;
  exports com.jetbrains.python.impl.sdk.flavors;
  exports com.jetbrains.python.impl.sdk.skeletons;
  exports com.jetbrains.python.impl.spellchecker;
  exports com.jetbrains.python.impl.statistics;
  exports com.jetbrains.python.impl.structureView;
  exports com.jetbrains.python.impl.testing;
  exports com.jetbrains.python.impl.testing.attest;
  exports com.jetbrains.python.impl.testing.doctest;
  exports com.jetbrains.python.impl.testing.nosetest;
  exports com.jetbrains.python.impl.testing.pytest;
  exports com.jetbrains.python.impl.testing.unittest;
  exports com.jetbrains.python.impl.toolbox;
  exports com.jetbrains.python.impl.traceBackParsers;
  exports com.jetbrains.python.impl.ui;
  exports com.jetbrains.python.impl.validation;
  exports com.jetbrains.python.impl.vp;
  exports consulo.python.buildout.module.extension;
  exports consulo.python.debugger;
  exports consulo.python.impl;
  exports consulo.python.impl.icon;
  exports consulo.python.module.extension;
}