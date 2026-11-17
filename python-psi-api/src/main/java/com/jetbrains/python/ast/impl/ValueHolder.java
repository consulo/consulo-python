package com.jetbrains.python.ast.impl;

public class ValueHolder<T> {
    public final T value;

    public ValueHolder(T value) {
        this.value = value;
    }
}
