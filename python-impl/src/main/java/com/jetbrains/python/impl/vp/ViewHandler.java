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
package com.jetbrains.python.impl.vp;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import com.google.common.base.Preconditions;
import consulo.application.ApplicationManager;

/**
 * Wrapper for view.
 *
 * @param <C> view class
 * @author Ilya.Kazakevich
 */
class ViewHandler<C> implements InvocationHandler
{
	/**
	 * Real view, created by user using {@link Creator#createView(Presenter)}
	 */
	private C realView;

	public void setRealView(@Nonnull C realView)
	{
		this.realView = realView;
	}

	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable
	{
		Preconditions.checkState(realView != null, "Real view not set");
		Invoker invoker = new Invoker(realView, method, args);
		ApplicationManager.getApplication().invokeAndWait(invoker);
		if(invoker.exception != null)
		{
			throw invoker.exception;
		}
		return invoker.result;
	}

	/**
	 * Class that invokes view methods in appropriate thread
	 */
	private static class Invoker implements Runnable
	{
		@Nonnull
		private final Method method;
		@Nullable
		private final Object[] args;
		@Nonnull
		private final Object target;

		private InvocationTargetException exception;
		private Object result;

		private Invoker(@Nonnull Object target, @Nonnull Method method, @Nullable Object[] args)
		{
			this.target = target;
			this.method = method;
			this.args = args;
		}

		@Override
		public void run()
		{
			try
			{
				result = method.invoke(target, args);
			}
			catch(IllegalAccessException e)
			{
				throw new IllegalStateException("Method is unaccessible: " + method, e);
			}
			catch(InvocationTargetException e)
			{
				exception = e;
			}
		}
	}
}
