/**
 * @author VISTALL
 * @since 25/05/2023
 */
module consulo.python.debugger
{
	requires consulo.application.api;
	requires consulo.base.icon.library;
	requires consulo.execution.api;
	requires consulo.execution.debug.api;
	requires consulo.localize.api;
	requires consulo.logging.api;
	requires consulo.process.api;
	requires consulo.project.api;
	requires consulo.ui.api;
	requires consulo.util.collection;
	requires consulo.util.io;
	requires consulo.util.lang;
	requires consulo.virtual.file.system.api;

	requires com.google.common;
	requires xmlrpc.common;
	requires xmlrpc.client;
	requires xstream;
	requires xpp3;

	exports com.jetbrains.python.console.pydev;
	exports com.jetbrains.python.debugger;
	exports com.jetbrains.python.debugger.pydev;
	exports com.jetbrains.python.debugger.pydev.transport;
}
