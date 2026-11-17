// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.debugger;

import com.jetbrains.python.debugger.PyDebugValue;
import com.jetbrains.python.debugger.PyDebugValueDescriptor;
import com.jetbrains.python.debugger.PyDebugValueGroupsKt;
import com.jetbrains.python.debugger.PyDebuggerException;
import com.jetbrains.python.debugger.PyFrameAccessor;
import com.jetbrains.python.debugger.PyStackFrameInfo;

import com.google.common.collect.Maps;
import consulo.application.Application;
import consulo.application.ReadAction;
import consulo.logging.Logger;
import consulo.document.Document;
import consulo.document.FileDocumentManager;
import consulo.project.Project;
import consulo.module.content.ProjectRootManager;
import consulo.virtualFileSystem.VirtualFile;
import consulo.ui.ex.ColoredTextContainer;
import consulo.ui.ex.SimpleTextAttributes;
import consulo.execution.debug.XSourcePosition;
import consulo.execution.debug.evaluation.XDebuggerEvaluator;
import consulo.execution.debug.frame.XCompositeNode;
import consulo.execution.debug.icon.ExecutionDebugIconGroup;
import consulo.execution.debug.frame.XStackFrame;
import consulo.execution.debug.frame.XValue;
import consulo.execution.debug.frame.XValueChildrenList;
import consulo.python.impl.icon.PythonImplIconGroup;
import consulo.python.impl.localize.PyLocalize;
import com.jetbrains.python.debugger.pydev.ProcessDebugger;
import com.jetbrains.python.debugger.pydev.ProtocolParser;
import com.jetbrains.python.impl.debugger.settings.PyDebuggerSettings;
import com.jetbrains.python.impl.psi.PyUtil;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Set;

import static com.jetbrains.python.debugger.PyDebugValueGroupsKt.addGroupValues;

public class PyStackFrame extends XStackFrame {
    protected static final Logger LOG = Logger.getInstance(PyStackFrame.class);

    private static final Object STACK_FRAME_EQUALITY_OBJECT = new Object();
    public static final Set<String> COMPREHENSION_NAMES = Set.of("<genexpr>", "<listcomp>", "<dictcomp>",
                                                                                  "<setcomp>");
    private final Project myProject;
    protected final PyFrameAccessor myDebugProcess;
    private final PyStackFrameInfo myFrameInfo;
    private final XSourcePosition myPosition;
    private volatile boolean isExternal = true;

    private @Nullable Map<String, PyDebugValueDescriptor> myChildrenDescriptors;

    public PyStackFrame(Project project,
                        PyFrameAccessor debugProcess,
                        PyStackFrameInfo frameInfo, XSourcePosition position) {
        myProject = project;
        myDebugProcess = debugProcess;
        myFrameInfo = frameInfo;
        myPosition = position;
        computeIsExternal();
    }

    private void computeIsExternal() {
        if (myPosition != null) {
            VirtualFile file = myPosition.getFile();
            PyUtil.runWithProgress(myProject, PyLocalize.debuggerProgressTitleStackframeProcessing().get(), false, true, indicator -> {
                isExternal = ReadAction.compute(() -> {
                                                    Document document = FileDocumentManager.getInstance().getDocument(file);
                                                    if (document != null && myProject != null) {
                                                        return !ProjectRootManager.getInstance(myProject).getFileIndex().isInContent(file);
                                                    }
                                                    return true;
                                                });
            });
        }
    }

    @Override
    public Object getEqualityObject() {
        return STACK_FRAME_EQUALITY_OBJECT;
    }

    @Override
    public XSourcePosition getSourcePosition() {
        return myPosition;
    }

    @Override
    public XDebuggerEvaluator getEvaluator() {
        return new PyDebuggerEvaluator(myProject, myDebugProcess);
    }

    @Override
    public void customizePresentation(ColoredTextContainer component) {
        component.setIcon(ExecutionDebugIconGroup.nodeFrame());

        if (myPosition == null) {
            component.append(PyLocalize.debuggerStackFrameFrameNotAvailable().get(), SimpleTextAttributes.GRAY_ATTRIBUTES);
            return;
        }

        component.append(myFrameInfo.getName(), gray(isExternal));
        component.append(", ", gray(isExternal));
        component.append(myPosition.getFile().getName(), gray(isExternal));
        component.append(":", gray(isExternal));
        component.append(Integer.toString(myPosition.getLine() + 1), gray(isExternal));
    }

    protected static SimpleTextAttributes gray(boolean gray) {
        return (gray) ? SimpleTextAttributes.GRAYED_ATTRIBUTES : SimpleTextAttributes.REGULAR_ATTRIBUTES;
    }

    @Override
    public void computeChildren(XCompositeNode node) {
        if (node.isObsolete()) {
            return;
        }
        myDebugProcess.setCurrentRootNode(node);
        Application.get().executeOnPooledThread(() -> {
            try {
                boolean cached = myDebugProcess.isFrameCached(this);
                XValueChildrenList values = myDebugProcess.loadFrame(this);
                if (!node.isObsolete()) {
                    addChildren(node, values);
                }
                if (values != null && !cached) {
                    PyDebugValue.getAsyncValues(this, myDebugProcess, values);
                }
            }
            catch (PyDebuggerException e) {
                if (!node.isObsolete()) {
                    node.setErrorMessage(PyLocalize.debuggerStackFrameUnableToDisplayFrameVariables().get());
                }
                LOG.warn(e);
            }
        });
    }

    protected void addChildren(XCompositeNode node, @Nullable XValueChildrenList children) {
        if (children == null) {
            node.addChildren(XValueChildrenList.EMPTY, true);
            return;
        }
        PyDebuggerSettings debuggerSettings = PyDebuggerSettings.getInstance();
        XValueChildrenList filteredChildren = new XValueChildrenList();
        boolean isReturnEmpty = true;
        boolean isSpecialEmpty = true;

        for (int i = 0; i < children.size(); i++) {
            XValue value = children.getValue(i);
            String name = children.getName(i);
            if (value instanceof PyDebugValue pyValue) {

                restoreValueDescriptor(pyValue);

                if (name.equals(ProtocolParser.DUMMY_RET_VAL) && debuggerSettings.isWatchReturnValues()) {
                    isReturnEmpty = false;
                }
                else if (!debuggerSettings.isSimplifiedView()) {
                    if (ProtocolParser.HIDDEN_TYPES.contains(name)) {
                        continue;
                    }
                    filteredChildren.add(name, value);
                }
                else {
                    if (name.equals(ProtocolParser.DUMMY_SPECIAL_VAR) || name.equals(ProtocolParser.DUMMY_IPYTHON_HIDDEN)) {
                        isSpecialEmpty = false;
                    }
                    else {
                        filteredChildren.add(name, value);
                    }
                }
            }
        }
        node.addChildren(filteredChildren, isReturnEmpty && isSpecialEmpty);
        if (!isReturnEmpty) {
            addGroupValues(PyLocalize.debuggerStackFrameReturnValues(),
                           ExecutionDebugIconGroup.nodeMethodlevelwatch(), node, null, myDebugProcess, ProcessDebugger.GROUP_TYPE.RETURN, "()");
        }
        if (!isSpecialEmpty) {
            addGroupValues(PyLocalize.debuggerStackFrameSpecialVariables(),
                           PythonImplIconGroup.pythonDebugSpecialvar(), node, null, myDebugProcess, ProcessDebugger.GROUP_TYPE.SPECIAL, null);
        }
    }

    public String getThreadId() {
        return myFrameInfo.getThreadId();
    }

    public String getFrameId() {
        return myFrameInfo.getId();
    }

    public String getThreadFrameId() {
        return myFrameInfo.getThreadId() + ":" + myFrameInfo.getId();
    }

    protected XSourcePosition getPosition() {
        return myPosition;
    }

    public String getName() {
        return myFrameInfo.getName();
    }

    public boolean isComprehension() {
        return COMPREHENSION_NAMES.contains(getName());
    }

    public void setChildrenDescriptors(@Nullable Map<String, PyDebugValueDescriptor> childrenDescriptors) {
        myChildrenDescriptors = childrenDescriptors;
    }

    public void restoreChildrenDescriptors(Map<String, Map<String, PyDebugValueDescriptor>> descriptorsCache) {
        String threadFrameId = getThreadFrameId();
        Map<String, PyDebugValueDescriptor> childrenDescriptors = descriptorsCache.getOrDefault(threadFrameId, Maps.newHashMap());
        setChildrenDescriptors(childrenDescriptors);
        descriptorsCache.put(threadFrameId, childrenDescriptors);
    }

    protected void restoreValueDescriptor(PyDebugValue value) {
        if (myChildrenDescriptors != null) {
            PyDebugValueDescriptor descriptor = myChildrenDescriptors.getOrDefault(value.getName(), null);
            if (descriptor == null) {
                descriptor = new PyDebugValueDescriptor();
                myChildrenDescriptors.put(value.getName(), descriptor);
            }
            value.setDescriptor(descriptor);
        }
    }
}
