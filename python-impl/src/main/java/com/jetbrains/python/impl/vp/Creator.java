// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.vp;



/**
 * Creates view and presenter allowing them to have links to each other.
 * Implement it and pass to {@link ViewPresenterUtils#linkViewWithPresenterAndLaunch(Class, Class, Creator)}
 *
 * @param <V> view interface
 * @param <P> presenter interface
 * @author Ilya.Kazakevich
 */
public interface Creator<V, P extends Presenter> {

    /**
     * Create presenter
     *
     * @param view for that presenter
     * @return presenter
     */
    P createPresenter(V view);

    /**
     * Creates view
     *
     * @param presenter for this view
     * @return view
     */
    V createView(P presenter);
}
