/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.console;

import java.awt.Font;

import org.jetbrains.annotations.NotNull;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.execution.console.LanguageConsoleView;
import com.intellij.execution.console.ProcessBackedConsoleExecuteActionHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.console.pydev.ConsoleCommunication;
import com.jetbrains.python.console.pydev.ConsoleCommunicationListener;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyStatementList;

/**
 * @author traff
 */
public class PydevConsoleExecuteActionHandler extends ProcessBackedConsoleExecuteActionHandler implements ConsoleCommunicationListener
{
	private final LanguageConsoleView myConsoleView;

	private final ConsoleCommunication consoleCommunication;
	private PyConsoleEnterHandler myEnterHandler = new PyConsoleEnterHandler();
	private boolean myEnabled = false;

	private int myIpythonInputPromptCount = 0;

	public PydevConsoleExecuteActionHandler(LanguageConsoleView consoleView, ProcessHandler processHandler, ConsoleCommunication consoleCommunication)
	{
		super(processHandler, false);
		myConsoleView = consoleView;
		this.consoleCommunication = consoleCommunication;
		this.consoleCommunication.addCommunicationListener(this);
	}

	public boolean isIpythonEnabled()
	{
		return PyConsoleUtil.getOrCreateIPythonData(myConsoleView.getVirtualFile()).isIPythonEnabled();
	}

	@Override
	public void processLine(@NotNull final String text)
	{
		executeMultiLine(text);
	}

	private void executeMultiLine(String text)
	{
		String commandText = !StringUtil.endsWithChar(text, '\n') ? text + "\n" : text;

		sendLineToConsole(new ConsoleCommunication.ConsoleCodeFragment(commandText, checkSingleLine(text)));
	}

	private boolean checkSingleLine(String text)
	{
		PyFile pyFile = (PyFile) PyElementGenerator.getInstance(getProject()).createDummyFile(myConsoleView.getVirtualFile().getUserData(LanguageLevel.KEY), text);
		return PsiTreeUtil.findChildOfAnyType(pyFile, PyStatementList.class) == null && pyFile.getStatements().size() < 2;
	}

	private void sendLineToConsole(@NotNull final ConsoleCommunication.ConsoleCodeFragment code)
	{
		ConsoleCommunication consoleComm = this.consoleCommunication;
		if(!consoleCommunication.isWaitingForInput())
		{
			executingPrompt();
		}
		if(isIpythonEnabled() && !consoleComm.isWaitingForInput() && code.getText().length() != 0)
		{
			++myIpythonInputPromptCount;
		}

		consoleComm.execInterpreter(code, interpreterResponse -> null);
	}

	public void inputReceived()
	{
		if(consoleCommunication instanceof PythonDebugConsoleCommunication)
		{
			if(consoleCommunication.isWaitingForInput())
			{
				((PythonDebugConsoleCommunication) consoleCommunication).waitingForInput = false;
				LanguageConsoleView console = myConsoleView;
				if(PyConsoleUtil.INPUT_PROMPT.equals(console.getPrompt()) || PyConsoleUtil.HELP_PROMPT.equals(console.getPrompt()))
				{
					console.setPrompt(PyConsoleUtil.ORDINARY_PROMPT);
				}
			}
		}
	}

	private void inPrompt()
	{
		if(ipythonEnabled())
		{
			ipythonInPrompt();
		}
		else
		{
			ordinaryPrompt();
		}
	}

	private void ordinaryPrompt()
	{
		if(!PyConsoleUtil.ORDINARY_PROMPT.equals(myConsoleView.getPrompt()))
		{
			myConsoleView.setPrompt(PyConsoleUtil.ORDINARY_PROMPT);
			PyConsoleUtil.scrollDown(myConsoleView.getCurrentEditor());
		}
	}

	private boolean ipythonEnabled()
	{
		return PyConsoleUtil.getOrCreateIPythonData(myConsoleView.getVirtualFile()).isIPythonEnabled();
	}

	private void ipythonInPrompt()
	{
		myConsoleView.setPromptAttributes(new ConsoleViewContentType("", ConsoleViewContentType.USER_INPUT_KEY)
		{
			@Override
			public TextAttributes getAttributes()
			{
				TextAttributes attrs = super.getAttributes();
				attrs.setFontType(Font.PLAIN);
				return attrs;
			}
		});
		myConsoleView.setPrompt("In[" + myIpythonInputPromptCount + "]:");
		PyConsoleUtil.scrollDown(myConsoleView.getCurrentEditor());
	}

	private void executingPrompt()
	{
		myConsoleView.setPrompt(PyConsoleUtil.EXECUTING_PROMPT);
	}

	private void more()
	{
		if(!PyConsoleUtil.INDENT_PROMPT.equals(myConsoleView.getPrompt()))
		{
			myConsoleView.setPrompt(PyConsoleUtil.INDENT_PROMPT);
			PyConsoleUtil.scrollDown(myConsoleView.getCurrentEditor());
		}
	}

	public static String getPrevCommandRunningMessage()
	{
		return "Previous command is still running. Please wait or press Ctrl+C in console to interrupt.";
	}

	@Override
	public void commandExecuted(boolean more)
	{
		if(!more && !ipythonEnabled() && !consoleCommunication.isWaitingForInput())
		{
			ordinaryPrompt();
		}
	}

	@Override
	public void inputRequested()
	{
		updateConsoleState();
	}

	public int getPythonIndent()
	{
		return CodeStyleSettingsManager.getSettings(getProject()).getIndentSize(PythonFileType.INSTANCE);
	}

	private Project getProject()
	{
		return myConsoleView.getProject();
	}

	public String getCantExecuteMessage()
	{
		if(!isEnabled())
		{
			return getConsoleIsNotEnabledMessage();
		}
		else if(!canExecuteNow())
		{
			return getPrevCommandRunningMessage();
		}
		else
		{
			return "Can't execute the command";
		}
	}

	@Override
	public void runExecuteAction(@NotNull LanguageConsoleView console)
	{
		if(isEnabled())
		{
			if(!canExecuteNow())
			{
				HintManager.getInstance().showErrorHint(console.getConsoleEditor(), getPrevCommandRunningMessage());
			}
			else
			{
				doRunExecuteAction(console);
			}
		}
		else
		{
			HintManager.getInstance().showErrorHint(console.getConsoleEditor(), getConsoleIsNotEnabledMessage());
		}
	}

	private void doRunExecuteAction(LanguageConsoleView console)
	{
		Document doc = myConsoleView.getEditorDocument();
		RangeMarker endMarker = doc.createRangeMarker(doc.getTextLength(), doc.getTextLength());
		endMarker.setGreedyToLeft(false);
		endMarker.setGreedyToRight(true);
		boolean isComplete = myEnterHandler.handleEnterPressed(console.getConsoleEditor());
		if(isComplete || consoleCommunication.isWaitingForInput())
		{

			if(endMarker.getEndOffset() - endMarker.getStartOffset() > 0)
			{
				ApplicationManager.getApplication().runWriteAction(() -> {
					CommandProcessor.getInstance().runUndoTransparentAction(() -> {
						doc.deleteString(endMarker.getStartOffset(), endMarker.getEndOffset());
					});
				});
			}
			if(shouldCopyToHistory(console))
			{
				copyToHistoryAndExecute(console);
			}
			else
			{
				processLine(myConsoleView.getConsoleEditor().getDocument().getText());
			}
		}
	}

	private void copyToHistoryAndExecute(LanguageConsoleView console)
	{
		super.runExecuteAction(console);
	}

	private static boolean shouldCopyToHistory(@NotNull LanguageConsoleView console)
	{
		return !PyConsoleUtil.isPagingPrompt(console.getPrompt());
	}

	public boolean canExecuteNow()
	{
		return !consoleCommunication.isExecuting() || consoleCommunication.isWaitingForInput();
	}

	protected String getConsoleIsNotEnabledMessage()
	{
		return "Console is not enabled.";
	}

	protected void setEnabled(boolean flag)
	{
		myEnabled = flag;
		updateConsoleState();
	}

	private void updateConsoleState()
	{
		if(!isEnabled())
		{
			executingPrompt();
		}
		else if(consoleCommunication.isWaitingForInput())
		{
			waitingForInputPrompt();
		}
		else if(canExecuteNow())
		{
			if(consoleCommunication.needsMore())
			{
				more();
			}
			else
			{
				inPrompt();
			}
		}
		else
		{
			executingPrompt();
		}
	}

	private void waitingForInputPrompt()
	{
		if(!PyConsoleUtil.INPUT_PROMPT.equals(myConsoleView.getPrompt()) && !PyConsoleUtil.HELP_PROMPT.equals(myConsoleView.getPrompt()))
		{
			myConsoleView.setPrompt(PyConsoleUtil.INPUT_PROMPT);
			PyConsoleUtil.scrollDown(myConsoleView.getCurrentEditor());
		}
	}

	public boolean isEnabled()
	{
		return myEnabled;
	}

	public ConsoleCommunication getConsoleCommunication()
	{
		return consoleCommunication;
	}
}
