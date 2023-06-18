/**
 * @author VISTALL
 * @since 25/05/2023
 */
module consulo.python.language.api
{
	requires transitive consulo.ide.api;

	requires com.google.common;

	// TODO remove in future
	requires consulo.ide.impl;

	exports com.jetbrains.python;
	exports com.jetbrains.python.codeInsight;
	exports com.jetbrains.python.codeInsight.controlflow;
	exports com.jetbrains.python.documentation;
	exports com.jetbrains.python.inspections;
	exports com.jetbrains.python.nameResolver;
	exports com.jetbrains.python.newProject;
	exports com.jetbrains.python.packaging;
	exports com.jetbrains.python.packaging.requirement;
	exports com.jetbrains.python.psi;
	exports com.jetbrains.python.psi.impl;
	exports com.jetbrains.python.psi.impl.stubs;
	exports com.jetbrains.python.psi.resolve;
	exports com.jetbrains.python.psi.stubs;
	exports com.jetbrains.python.psi.types;
	exports com.jetbrains.python.run;
	exports com.jetbrains.python.templateLanguages;
	exports com.jetbrains.python.templateLanguages.psi;
	exports com.jetbrains.python.toolbox;
	exports consulo.python.psi.icon;
}