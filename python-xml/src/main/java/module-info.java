/**
 * @author VISTALL
 * @since 24/06/2023
 */
module consulo.python.xml
{
	requires consulo.virtual.file.system.api;
	requires com.intellij.xml.api;
	requires com.intellij.xml;

	exports com.jetbrains.python.impl.codeInsight.template;
	exports consulo.python.xml.impl;
}
