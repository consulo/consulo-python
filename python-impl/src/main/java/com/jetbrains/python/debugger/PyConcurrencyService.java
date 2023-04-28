package com.jetbrains.python.debugger;

import consulo.execution.debug.XDebugSession;
import consulo.ide.ServiceManager;
import consulo.project.Project;

/**
 * @author traff
 */
public abstract class PyConcurrencyService
{
	public static PyConcurrencyService getInstance(Project project)
	{
		return ServiceManager.getService(project, PyConcurrencyService.class);
	}

	public abstract void recordEvent(XDebugSession session, PyConcurrencyEvent event, boolean isAsyncIo);

	public abstract void initSession(XDebugSession session);

	public abstract void removeSession(XDebugSession session);
}
