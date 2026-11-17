// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.debugger.containerview;

import com.jetbrains.python.debugger.PyDebugValue;
import com.jetbrains.python.debugger.PyDebuggerException;
import com.jetbrains.python.debugger.PyFrameAccessor;
import com.jetbrains.python.debugger.values.DataFrameDebugValue;
import consulo.execution.debug.frame.XValueChildrenList;
import consulo.language.editor.completion.CompletionResultSet;
import consulo.language.editor.completion.lookup.LookupElementBuilder;
import consulo.language.editor.completion.lookup.PrioritizedLookupElement;
import consulo.language.editor.ui.awt.TextFieldCompletionProvider;
import consulo.logging.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class PyDataViewerCompletionProvider extends TextFieldCompletionProvider {
    private static final Logger LOG = Logger.getInstance(PyDataViewerCompletionProvider.class);

    private final PyFrameAccessor myFrameAccessor;

    public PyDataViewerCompletionProvider(PyFrameAccessor frameAccessor) {
        myFrameAccessor = frameAccessor;
    }

    @Override
    public void addCompletionVariants(String text, int offset, String prefix, CompletionResultSet result) {
        List<PyDebugValue> values = new ArrayList<>(getDataFrameValues());
        values.sort(Comparator.comparing(PyDebugValue::getName));
        for (int i = 0; i < values.size(); i++) {
            PyDebugValue value = values.get(i);
            LookupElementBuilder element = LookupElementBuilder.create(value.getName()).withTypeText(value.getType(), true);
            result.addElement(PrioritizedLookupElement.withPriority(element, -i));
        }
    }

    private List<PyDebugValue> getDataFrameValues() {
        try {
            XValueChildrenList pythonVariables = myFrameAccessor.loadFrame(null);
            if (pythonVariables == null) {
                return List.of();
            }
            List<PyDebugValue> result = new ArrayList<>();
            for (int i = 0; i < pythonVariables.size(); i++) {
                if (!(pythonVariables.getValue(i) instanceof DataFrameDebugValue dataFrameValue)) {
                    continue;
                }
                result.add(dataFrameValue);
            }
            return result;
        }
        catch (PyDebuggerException e) {
            LOG.error("Error loading frame variables for completion provider: " + e.getMessage());
            return List.of();
        }
    }
}
