// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.vp;


import com.google.common.base.Preconditions;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

/**
 * Entry point to package. Use {@link #linkViewWithPresenterAndLaunch(Class, Class, Creator)}
 * @author Ilya.Kazakevich
 */
public final class ViewPresenterUtils {
    private ViewPresenterUtils() {
    }

    /**
     * TODO: Write about do not call anything in constructor
     * Creates link between view and presenter and launches them using {@link Presenter#launch()}. Be sure to read package info first.
     *
     * @param presenterInterface presenter interface
     * @param viewInterface      view interface
     * @param creator            class that handles presenter and view instances actual creation
     * @param <V>                view interface
     * @param <P>                presenter interface
     */
    public static <V, P extends Presenter> void linkViewWithPresenterAndLaunch(Class<P> presenterInterface,
                                                                                                                                                         Class<V> viewInterface,
                                                                                                                                                         Creator<V, P> creator) {
        Preconditions.checkArgument(presenterInterface.isInterface(), "Presenter is not interface");
        Preconditions.checkArgument(viewInterface.isInterface(), "View is not interface");

        //TODO: Use cglib?
        PresenterHandler<P> presenterHandler = new PresenterHandler<>();
        ViewHandler<V> viewHandler = new ViewHandler<>();
        V viewProxy = createProxy(viewInterface, viewHandler);
        P presenterProxy = createProxy(presenterInterface, presenterHandler);

        V realView = creator.createView(presenterProxy);
        viewHandler.setRealView(realView);
        P realPresenter = creator.createPresenter(viewProxy);
        presenterHandler.setRealPresenter(realPresenter);
        realPresenter.launch();
    }


    @SuppressWarnings("unchecked")
    private static <C> C createProxy(Class<C> clazz, InvocationHandler handler) {
        assert clazz != null;
        assert handler != null;
        ClassLoader classLoader = clazz.getClassLoader(); // clazz's class loader allows [java.lang.reflect.Proxy#ensureVisible]
        // TODO consulo: ReflectionUtil.proxy(ClassLoader, Class, InvocationHandler) missing
        return (C) Proxy.newProxyInstance(classLoader, new Class[]{clazz}, handler);
    }
}
