/**
 * @author VISTALL
 * @since 25/05/2023
 */
module consulo.python.impl
{
  requires consulo.ide.api;

  requires consulo.python.language.api;
  requires consulo.python.debugger;

  requires com.google.common;
  requires com.google.gson;

  requires com.intellij.regexp;
  requires com.intellij.xml;

  requires xmlrpc.common;

  // TODO move to own module - without hard dep to spellchecker
  requires com.intellij.spellchecker;

  // TODO remove in future
  requires consulo.ide.impl;
  requires java.desktop;
  requires forms.rt;
}