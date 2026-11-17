package com.jetbrains.python.ast;

public interface PyAstIfPartIf extends PyAstIfPart {
    @Override
    default boolean isElif() {
        return false;
    }
}
