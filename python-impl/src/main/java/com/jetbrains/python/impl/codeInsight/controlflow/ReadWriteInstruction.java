// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.controlflow;

import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.types.PyAnyType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.language.controlFlow.ControlFlowBuilder;
import consulo.language.controlFlow.base.InstructionImpl;
import consulo.language.psi.PsiElement;
import consulo.util.lang.ref.Ref;
import org.jspecify.annotations.Nullable;

public class ReadWriteInstruction extends InstructionImpl {
    public enum ACCESS {
        READ(true, false, false, false),
        WRITE(false, true, false, false),
        ASSERTTYPE(false, false, true, false),
        READWRITE(true, true, false, false),
        DELETE(false, false, false, true);

        private final boolean myIsReadAccess;
        private final boolean myIsWriteAccess;
        private final boolean myIsAssertTypeAccess;
        private final boolean myIsDeleteAccess;

        ACCESS(boolean isReadAccess, boolean isWriteAccess, boolean isAssertTypeAccess, boolean isDeleteAccess) {
            myIsReadAccess = isReadAccess;
            myIsWriteAccess = isWriteAccess;
            myIsAssertTypeAccess = isAssertTypeAccess;
            myIsDeleteAccess = isDeleteAccess;
        }

        public boolean isReadAccess() {
            return myIsReadAccess;
        }

        public boolean isWriteAccess() {
            return myIsWriteAccess;
        }

        public boolean isAssertTypeAccess() {
            return myIsAssertTypeAccess;
        }

        public boolean isDeleteAccess() {
            return myIsDeleteAccess;
        }
    }

    private final @Nullable String myName;
    private final ACCESS myAccess;
    private final InstructionTypeCallback myGetType;

    private ReadWriteInstruction(ControlFlowBuilder builder,
                                 @Nullable PsiElement element,
                                 @Nullable String name,
                                 ACCESS access) {
        this(builder, element, name, access, null);
    }

    private ReadWriteInstruction(ControlFlowBuilder builder,
                                 @Nullable PsiElement element,
                                 @Nullable String name,
                                 ACCESS access,
                                 @Nullable InstructionTypeCallback getType) {
        super(builder, element);
        myName = name;
        myAccess = access;
        myGetType = getType != null ? getType : instructionTypeCallback(element);
    }

    public @Nullable String getName() {
        return myName;
    }

    public ACCESS getAccess() {
        return myAccess;
    }

    public @Nullable Ref<PyType> getType(TypeEvalContext context, @Nullable PsiElement anchor) {
        return myGetType.getType(context);
    }

    @Override
    public String getElementPresentation() {
        return myAccess.toString() + " ACCESS: " + myName;
    }

    private static InstructionTypeCallback instructionTypeCallback(@Nullable PsiElement element) {
        if (element instanceof PyExpression expression) {
            return it -> Ref.create(it.getType(expression));
        }
        else {
            return it -> Ref.create(PyAnyType.getUnknown());
        }
    }

    public static ReadWriteInstruction read(ControlFlowBuilder builder, @Nullable PyElement element, @Nullable String name) {
        return new ReadWriteInstruction(builder, element, name, ACCESS.READ);
    }

    public static ReadWriteInstruction write(ControlFlowBuilder builder, @Nullable PyElement element, @Nullable String name) {
        return new ReadWriteInstruction(builder, element, name, ACCESS.WRITE);
    }

    public static ReadWriteInstruction newInstruction(ControlFlowBuilder builder,
                                                      @Nullable PsiElement element,
                                                      @Nullable String name,
                                                      ACCESS access) {
        return new ReadWriteInstruction(builder, element, name, access);
    }

    public static ReadWriteInstruction assertType(ControlFlowBuilder builder,
                                                  @Nullable PsiElement element,
                                                  @Nullable String name,
                                                  @Nullable InstructionTypeCallback getType) {
        return new ReadWriteInstruction(builder, element, name, ACCESS.ASSERTTYPE, getType);
    }

    public static ReadWriteInstruction readWrite(ControlFlowBuilder builder,
                                                 @Nullable PsiElement element,
                                                 @Nullable String name,
                                                 @Nullable InstructionTypeCallback getType) {
        return new ReadWriteInstruction(builder, element, name, ACCESS.READWRITE, getType);
    }
}
