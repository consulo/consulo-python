/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package com.jetbrains.python.impl;

import consulo.annotation.DeprecationInfo;
import consulo.python.impl.icon.PythonImplIconGroup;
import consulo.ui.image.Image;

@Deprecated
@DeprecationInfo("Use PythonIcons")
public class PythonIcons {
  public static class Pyqt {
    public static final Image UiForm = PythonImplIconGroup.pyqtUiform();
  }

  public static class Python {

    public static class Buildout {
      public static final Image Buildout = PythonImplIconGroup.pythonBuildoutBuildout();
    }

    public static class Debug {
      public static final Image CommandLine = PythonImplIconGroup.pythonDebugCommandline();
      public static final Image SpecialVar = PythonImplIconGroup.pythonDebugSpecialvar();
      public static final Image StepIntoMyCode = PythonImplIconGroup.pythonDebugStepintomycode();
    }

    public static final Image Dotnet = PythonImplIconGroup.pythonDotnet();

    public static class Nodes {
      public static final Image Cyan_dot = PythonImplIconGroup.pythonNodesCyan_dot();
      public static final Image Lock = PythonImplIconGroup.pythonNodesLock();
      public static final Image Red_inv_triangle = PythonImplIconGroup.pythonNodesRed_inv_triangle();

    }

    public static final Image PropertyDeleter = PythonImplIconGroup.pythonPropertydeleter();
    public static final Image PropertyGetter = PythonImplIconGroup.pythonPropertygetter();
    public static final Image PropertySetter = PythonImplIconGroup.pythonPropertysetter();
    public static final Image Pypy = PythonImplIconGroup.pythonPypy();
    public static final Image Python = PythonImplIconGroup.pythonPython();
    public static final Image Python_24 = PythonImplIconGroup.pythonPython_24();
    public static final Image PythonConsole = PythonImplIconGroup.pythonPythonconsole();
    public static final Image PythonClosed = PythonImplIconGroup.pythonPythonclosed();
    public static final Image PythonTests = PythonImplIconGroup.pythonPythontests();
    public static final Image RemoteInterpreter = PythonImplIconGroup.pythonRemoteinterpreter();
    public static final Image Virtualenv = PythonImplIconGroup.pythonVirtualenv();
  }
}
