package com.jetbrains.python.debugger;

import jakarta.annotation.Nonnull;

import consulo.application.ApplicationManager;
import consulo.execution.debug.frame.XValueModifier;


public class PyValueModifier extends XValueModifier {

  private final PyFrameAccessor myDebugProcess;
  private final PyDebugValue myVariable;

  public PyValueModifier(PyFrameAccessor debugProcess, PyDebugValue variable) {
    myDebugProcess = debugProcess;
    myVariable = variable;
  }

  @Override
  public void setValue(@Nonnull final String expression, @Nonnull final XModificationCallback callback) {
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      public void run() {
        try {
          myDebugProcess.changeVariable(myVariable, expression);
          callback.valueModified();
        }
        catch (PyDebuggerException e) {
          callback.errorOccurred(e.getTracebackError());
        }
      }
    });
  }

  @Override
  public String getInitialValueEditorText() {
    return PyTypeHandler.format(myVariable);
  }

}
