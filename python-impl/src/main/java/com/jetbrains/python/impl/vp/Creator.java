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


import jakarta.annotation.Nonnull;

/**
 * Creates view and presenter allowing them to have links to each other.
 * Implement it and pass to {@link ViewPresenterUtils#linkViewWithPresenterAndLaunch(Class, Class, Creator)}
 *
 * @param <V> view interface
 * @param <P> presenter interface
 * @author Ilya.Kazakevich
 */
public interface Creator<V, P extends Presenter>
{

	/**
	 * Create presenter
	 *
	 * @param view for that presenter
	 * @return presenter
	 */
	@Nonnull
	P createPresenter(@Nonnull V view);

	/**
	 * Creates view
	 *
	 * @param presenter for this view
	 * @return view
	 */
	@Nonnull
	V createView(@Nonnull P presenter);

}
