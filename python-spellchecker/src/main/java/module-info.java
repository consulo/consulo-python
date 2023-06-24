/**
 * @author VISTALL
 * @since 24/06/2023
 */
open module consulo.python.spellchecker
{
	requires consulo.ide.api;
	requires com.intellij.spellchecker;
	requires consulo.python.language.api;
	requires consulo.python.impl;
}