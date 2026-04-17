/**
 * @author VISTALL
 * @since 2023-06-24
 */
open module consulo.python.spellchecker {
    requires consulo.application.content.api;
    requires consulo.document.api;
    requires consulo.language.api;
    requires consulo.language.spellchecker.api;
    requires consulo.localize.api;
    requires consulo.module.api;
    requires consulo.module.content.api;
    requires consulo.project.api;
    requires consulo.ui.api;
    requires consulo.ui.ex.api;
    requires consulo.virtual.file.system.api;

    requires com.intellij.spellchecker;
    requires consulo.python.language.api;
    requires consulo.python.impl;
}
