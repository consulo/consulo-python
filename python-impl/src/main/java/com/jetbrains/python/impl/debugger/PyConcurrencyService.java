package com.jetbrains.python.impl.debugger;

import com.jetbrains.python.debugger.PyConcurrencyEvent;
import consulo.execution.debug.XDebugSession;
import consulo.project.Project;

/**
 * @author traff
 */
public abstract class PyConcurrencyService
{
	private static final PyConcurrencyService ourDummy = new PyConcurrencyService()
	{
		@Override
		public void recordEvent(XDebugSession session, PyConcurrencyEvent event, boolean isAsyncIo)
		{

		}

		@Override
		public void initSession(XDebugSession session)
		{

		}

		@Override
		public void removeSession(XDebugSession session)
		{

		}
	};

	public static PyConcurrencyService getInstance(Project project)
	{
		return ourDummy;
	}

	public abstract void recordEvent(XDebugSession session, PyConcurrencyEvent event, boolean isAsyncIo);

	public abstract void initSession(XDebugSession session);

	public abstract void removeSession(XDebugSession session);
}
