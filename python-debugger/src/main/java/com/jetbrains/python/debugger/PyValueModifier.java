package com.jetbrains.python.debugger;


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
  public void setValue(final String expression, final XModificationCallback callback) {
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
