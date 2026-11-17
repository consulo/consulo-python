package com.jetbrains.python.ast;

public interface PyAstIfPartElif extends PyAstIfPart {
    @Override
    default boolean isElif() {
        return true;
    }
}
