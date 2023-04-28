package com.jetbrains.python.console.pydev;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.function.Function;

/**
 * @author traff
 */
public interface ConsoleCommunication
{
	@Nonnull
	List<PydevCompletionVariant> getCompletions(String text, String actualToken) throws Exception;

	String getDescription(String text) throws Exception;

	boolean isWaitingForInput();

	boolean isExecuting();

	boolean needsMore();

	void execInterpreter(ConsoleCodeFragment code, Function<InterpreterResponse, Object> callback);

	void interrupt();

	void addCommunicationListener(ConsoleCommunicationListener listener);

	void notifyCommandExecuted(boolean more);

	void notifyInputRequested();

	class ConsoleCodeFragment
	{
		private final String myText;
		private final boolean myIsSingleLine;

		public ConsoleCodeFragment(String text, boolean isSingleLine)
		{
			myText = text;
			myIsSingleLine = isSingleLine;
		}

		public String getText()
		{
			return myText;
		}

		public boolean isSingleLine()
		{
			return myIsSingleLine;
		}
	}
}
