/**
 * @author VISTALL
 * @since 25/05/2023
 */
module consulo.python.debugger
{
	requires consulo.annotation;
	requires consulo.application.api;
	requires consulo.base.icon.library;
	requires consulo.component.api;
	requires consulo.disposer.api;
	requires consulo.execution.api;
	requires consulo.execution.debug.api;
	requires consulo.localize.api;
	requires consulo.logging.api;
	requires consulo.process.api;
	requires consulo.project.api;
	requires consulo.ui.api;
	requires consulo.ui.ex.api;
	requires consulo.util.collection;
	requires consulo.util.concurrent;
	requires consulo.util.io;
	requires consulo.util.lang;
	requires consulo.virtual.file.system.api;

	requires com.google.common;
	requires xmlrpc.common;
	requires xmlrpc.client;
	requires xstream;
	requires xpp3;

	requires java.desktop;
	requires java.xml;

	exports com.jetbrains.python.console.pydev;
	exports com.jetbrains.python.debugger;
	exports com.jetbrains.python.debugger.pydev;
	exports com.jetbrains.python.debugger.pydev.dataviewer;
	exports com.jetbrains.python.debugger.pydev.tables;
	exports com.jetbrains.python.debugger.pydev.transport;
	exports com.jetbrains.python.debugger.render;
	exports com.jetbrains.python.debugger.values;
	exports com.jetbrains.python.tables;
	exports consulo.python.debugger.localize;
}
