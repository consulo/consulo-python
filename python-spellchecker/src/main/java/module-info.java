/**
 * @author VISTALL
 * @since 2023-06-24
 */
open module consulo.python.spellchecker {
    requires consulo.ide.api;
    requires com.intellij.spellchecker;
    requires consulo.python.language.api;
    requires consulo.python.impl;
}