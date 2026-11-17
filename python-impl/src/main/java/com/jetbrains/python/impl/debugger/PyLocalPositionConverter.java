// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
/*
 * Copyright 2013-2026 consulo.io
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
package com.jetbrains.python.impl.debugger;

import com.jetbrains.python.debugger.PyPositionConverter;
import com.jetbrains.python.debugger.PySignature;
import com.jetbrains.python.debugger.PySourcePosition;

import consulo.application.ReadAction;
import consulo.document.Document;
import consulo.document.FileDocumentManager;
import consulo.application.util.SystemInfo;
import consulo.util.lang.StringUtil;
import com.jetbrains.python.impl.sdk.PySdkUtil;
import consulo.platform.Platform;
import consulo.virtualFileSystem.StandardFileSystems;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.VirtualFileManager;
import consulo.virtualFileSystem.VirtualFileSystem;
import consulo.virtualFileSystem.archive.ArchiveVfsUtil;
import consulo.execution.debug.XDebuggerUtil;
import consulo.execution.debug.XSourcePosition;
import org.jspecify.annotations.Nullable;

import java.io.File;


public class PyLocalPositionConverter implements PyPositionConverter {
    private static final String[] EGG_EXTENSIONS = new String[]{".egg", ".zip"};

    // Consulo-specific: the machine the debugged process runs on - its paths name the files
    private final Platform myPlatform;

    public PyLocalPositionConverter() {
        this(Platform.current());
    }

    // Consulo-specific
    public PyLocalPositionConverter(Platform platform) {
        myPlatform = platform;
    }

    protected static class PyLocalSourcePosition extends PySourcePosition {
        public PyLocalSourcePosition(String file, int line) {
            super(file, line);
        }

        @Override
        protected String normalize(@Nullable String file) {
            if (file == null) {
                return null;
            }
            if (SystemInfo.isWindows) {
                file = winNormCase(file);
            }
            return super.normalize(file);
        }
    }

    protected static class PyRemoteSourcePosition extends PySourcePosition {
        public PyRemoteSourcePosition(String file, int line) {
            super(file, line);
        }

        @Override
        protected String normalize(@Nullable String file) {
            if (file == null) {
                return null;
            }
            if (SystemInfo.isWindows && isWindowsPath(file)) {
                file = winNormCase(file);
            }
            return super.normalize(file);
        }
    }

    @Override
    public PySourcePosition create(String file, int line) {
        return convertPythonToFrame(file, line);
    }

    @Override
    public PySourcePosition convertPythonToFrame(String filePath, int line) {
        File file = new File(filePath);

        if (file.exists()) {
            return new PyLocalSourcePosition(file.getPath(), line);
        }
        else {
            return new PyRemoteSourcePosition(filePath, line);
        }
    }

    @Override
    public PySourcePosition convertFrameToPython(PySourcePosition position) {
        return position; // frame and Python positions are the same for Python files
    }

    @Override
    public PySourcePosition convertToPython(XSourcePosition position) {
        // Consulo-specific: the path of the file on the machine of the debugged process
        return convertToPython(convertFilePath(PySdkUtil.toTargetPath(position.getFile())), convertLocalLineToRemote(position.getFile(), position.getLine()));
    }

    protected PySourcePosition convertToPython(String filePath, int line) {
        return new PyLocalSourcePosition(filePath, line);
    }

    protected static int convertLocalLineToRemote(VirtualFile file, int l) {
        return ReadAction.compute(() -> {
            int line = l;
            Document document = FileDocumentManager.getInstance().getDocument(file);
            if (document != null) {
                while (PyDebugSupportUtils.isContinuationLine(document, line)) {
                    line++;
                }
            }
            return line + 1;
        });
    }

    @Override
    public @Nullable XSourcePosition convertFromPython(PySourcePosition position, String frameName) {
        return createXSourcePosition(getVirtualFile(position.getFile()), position.getLine());
    }

    @Override
    public PySignature convertSignature(PySignature signature) {
        return signature;
    }

    public VirtualFile getVirtualFile(String path) {
        // Consulo-specific: the files of another machine are found through its file system
        VirtualFile vFile = myPlatform.equals(Platform.current())
            ? getLocalFileSystem().findFileByPath(path)
            : VirtualFileManager.getInstance().findFileByNioPath(myPlatform.fs().getPath(path));

        if (vFile == null) {
            vFile = findEggEntry(getLocalFileSystem(), path);
        }
        return vFile;
    }

    protected VirtualFileSystem getLocalFileSystem() {
        return StandardFileSystems.local();
    }

    public static @Nullable VirtualFile findEggEntry(VirtualFileSystem virtualFileSystem, String file) {
        int ind = -1;
        for (String ext : EGG_EXTENSIONS) {
            ind = file.indexOf(ext);
            if (ind != -1) {
                break;
            }
        }
        if (ind != -1) {
            String jarPath = file.substring(0, ind + 4);
            VirtualFile jarFile = virtualFileSystem.findFileByPath(jarPath);
            if (jarFile != null) {
                String innerPath = file.substring(ind + 4);
                VirtualFile jarRoot = ArchiveVfsUtil.getArchiveRootForLocalFile(jarFile);
                if (jarRoot != null) {
                    return jarRoot.findFileByRelativePath(innerPath);
                }
            }
        }
        return null;
    }

    private static String convertFilePath(String file) {
        int ind = -1;
        for (String ext : EGG_EXTENSIONS) {
            ind = file.indexOf(ext + "!");
            if (ind != -1) {
                break;
            }
        }
        if (ind != -1) {
            return file.substring(0, ind + 4) + file.substring(ind + 5);
        }
        else {
            return file;
        }
    }

    private static String winNormCase(String file) {
        int ind = -1;
        for (String ext : EGG_EXTENSIONS) {
            ind = file.indexOf(ext);
            if (ind != -1) {
                break;
            }
        }
        if (ind != -1) {
            return StringUtil.toLowerCase(file.substring(0, ind + 4)) + file.substring(ind + 4);
        }
        else {
            return StringUtil.toLowerCase(file);
        }
    }

    public static @Nullable XSourcePosition createXSourcePosition(@Nullable VirtualFile vFile, int line) {
        if (vFile != null) {
            return XDebuggerUtil.getInstance().createPosition(vFile, convertRemoteLineToLocal(vFile, line));
        }
        else {
            return null;
        }
    }

    private static int convertRemoteLineToLocal(VirtualFile vFile, int line) {
        Document document =
          ReadAction.compute(() -> FileDocumentManager.getInstance().getDocument(vFile));

        line--;
        if (document != null) {
            while (PyDebugSupportUtils.isContinuationLine(document, line - 1)) {
                line--;
            }
        }
        return line;
    }
}
