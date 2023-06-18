/**
 * @author VISTALL
 * @since 25/05/2023
 */
module consulo.python.debugger
{
	requires consulo.ide.api;

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