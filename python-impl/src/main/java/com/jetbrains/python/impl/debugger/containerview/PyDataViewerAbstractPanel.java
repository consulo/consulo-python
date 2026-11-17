// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.debugger.containerview;

import com.jetbrains.python.debugger.ArrayChunk;
import com.jetbrains.python.debugger.PyDebugValue;
import com.jetbrains.python.debugger.PyDebuggerException;
import com.jetbrains.python.debugger.PyFrameAccessor;
import consulo.application.Application;
import consulo.disposer.Disposable;
import consulo.language.editor.ui.awt.EditorTextField;
import consulo.logging.Logger;
import consulo.python.impl.localize.PyLocalize;
import org.jspecify.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

public abstract class PyDataViewerAbstractPanel extends JPanel implements Disposable {
    private static final Logger LOG = Logger.getInstance(PyDataViewerAbstractPanel.class);

    private static final String MODIFIED_VARIABLE_FORMAT = "%s*";

    private final PyDataViewerModel myDataViewerModel;
    private final boolean myIsPanelFromFactory;

    protected final CopyOnWriteArrayList<OnNameChangedListener> listeners = new CopyOnWriteArrayList<>();

    public PyDataViewerAbstractPanel(PyDataViewerModel dataViewerModel, boolean isPanelFromFactory) {
        super(new BorderLayout());
        myDataViewerModel = dataViewerModel;
        myIsPanelFromFactory = isPanelFromFactory;
    }

    public PyDataViewerAbstractPanel(PyDataViewerModel dataViewerModel) {
        this(dataViewerModel, false);
    }

    public PyDataViewerModel getDataViewerModel() {
        return myDataViewerModel;
    }

    public boolean isPanelFromFactory() {
        return myIsPanelFromFactory;
    }

    /**
     * Represents a formatting string used for specifying format.
     */
    public abstract String getFormatValueFromUI();

    public abstract void setFormatValueFromUI(String value);

    /**
     * Represents a slicing string used for slicing data in a viewer panel.
     * For example, np_array[0] or df['column_1'].
     */
    protected abstract String getSlicingValueFromUI();

    protected abstract void setSlicingValueFromUI(String value);

    public abstract boolean isColoredValueFromUI();

    public abstract void setColoredValueFromUI(boolean value);

    public abstract EditorTextField getSlicingTextField();

    public abstract @Nullable JPanel getTopToolbar();

    public abstract void setTopToolbar(@Nullable JPanel topToolbar);

    protected abstract void setError(String text, boolean modifier);

    protected abstract void setupDataProvider();

    protected abstract void updateUI(ArrayChunk chunk, PyDebugValue originalDebugValue, DataViewStrategy strategy, boolean modifier);

    public abstract JComponent createTable(@Nullable PyDebugValue originalDebugValue, @Nullable ArrayChunk chunk);

    public JComponent createTable() {
        return createTable(null, null);
    }

    public abstract void recreateTable();

    @Override
    public void dispose() {
    }

    protected void onEnterPressed(DataViewerCommandSource commandSource) {
        apply(commandSource);
    }

    public void apply(DataViewerCommandSource commandSource) {
        myDataViewerModel.setFormat(getFormatValueFromUI());
        myDataViewerModel.setSlicing(getSlicingValueFromUI());
        myDataViewerModel.setColored(isColoredValueFromUI());

        apply(myDataViewerModel.getSlicing(), false, commandSource);
    }

    public void apply(@Nullable String name, boolean modifier) {
        apply(name, modifier, null);
    }

    public void apply(@Nullable String name, boolean modifier, @Nullable DataViewerCommandSource commandSource) {
        Application.get().executeOnPooledThread(() -> {
            PyDebugValue debugValue = getDebugValue(name, modifier);
            Application.get().invokeLater(() -> {
                if (debugValue != null) {
                    apply(debugValue, modifier, commandSource);
                }
            });
        });
    }

    public void apply(PyDebugValue debugValue, boolean modifier) {
        apply(debugValue, modifier, null);
    }

    public void apply(PyDebugValue debugValue, boolean modifier, @Nullable DataViewerCommandSource commandSource) {
        List<Integer> dimensions = getValueDimensions(debugValue);
        int dimensionsSize = dimensions != null ? dimensions.size() : 0;
        // FUS statistics (PyDataViewerCollector: logDataSlicingApplied/logDataFormattingApplied/logDataReloadApplied/logDataOpened)
        // are not ported to Consulo

        String type = debugValue.getType();
        DataViewStrategy strategy = DataViewStrategy.getStrategy(type);
        if (strategy == null) {
            setError(PyLocalize.debuggerDataViewTypeIsNotSupported(type).get(), modifier);
            return;
        }

        // For 3D, 4D, we should pass the "%" as the format parameter to correctly reduce tables' dimensions to 2.
        String format = dimensionsSize > 2 ? "%" : getFormatValueFromUI();
        Application.get().executeOnPooledThread(() -> {
            try {
                if (strategy.getTypeName().equals("Tensor") && !isNumpyAvailable(debugValue.getFrameAccessor())) {
                    Application.get().invokeLater(() -> setError(PyLocalize.debuggerDataViewNumpyIsNotAvailable().get(), modifier));
                    return;
                }
                doStrategyInitExecution(debugValue.getFrameAccessor(), strategy);
                ArrayChunk arrayChunk = debugValue.getFrameAccessor().getArrayItems(debugValue, 0, 0, 0, 0, format);
                Application.get().invokeLater(() -> {
                    updateUI(arrayChunk, debugValue, strategy, modifier);
                    myDataViewerModel.setModified(modifier);
                    myDataViewerModel.setDebugValue(debugValue);
                });
            }
            catch (IllegalArgumentException e) {
                Application.get().invokeLater(() -> setError(e.getLocalizedMessage(), modifier)); //NON-NLS
            }
            catch (PyDebuggerException e) {
                LOG.error(e);
            }
            catch (Exception e) {
                String message = e.getMessage();
                if (message != null && message.contains("Numpy is not available")) {
                    setError(PyLocalize.debuggerDataViewNumpyIsNotAvailable().get(), modifier);
                }
                LOG.error("PyDataViewer.apply: Numpy is not available", e);
            }
        });
    }

    /**
     * PyDebugValue shape in case of arrays could be like (10, 14, 23),
     * and we can extract these dimensions for analysis and logging.
     */
    private static @Nullable List<Integer> getValueDimensions(PyDebugValue debugValue) {
        String shape = debugValue.getShape();
        if (shape == null || !(shape.startsWith("(") && shape.endsWith(")"))) {
            return null;
        }
        List<Integer> dimensions = new ArrayList<>();
        for (String part : shape.substring(1, shape.length() - 1).split(",")) {
            if (part.isEmpty()) {
                continue;
            }
            try {
                dimensions.add(Integer.parseInt(part.trim()));
            }
            catch (NumberFormatException ignored) {
            }
        }
        long commas = shape.chars().filter(ch -> ch == ',').count();
        return dimensions.size() == commas + 1 ? dimensions : null;
    }

    private static boolean isNumpyAvailable(PyFrameAccessor frameAccessor) {
        try {
            return "True".equals(frameAccessor.evaluate("__import__('torch').zeros(0).numpy() is not None", false, false).getValue());
        }
        catch (PyDebuggerException e) {
            return false;
        }
    }

    protected void doStrategyInitExecution(PyFrameAccessor frameAccessor, DataViewStrategy strategy) throws PyDebuggerException {
        String execString = strategy.getInitExecuteString();
        if (execString == null) {
            return;
        }
        frameAccessor.evaluate(execString, true, false);
    }

    protected void updateTabNameSlicingFieldAndFormatField(@Nullable ArrayChunk chunk, PyDebugValue originalDebugValue, boolean modifier) {
        // Debugger generates a temporary name for every slice evaluation, so we should select a correct name for it
        PyDebugValue debugValue = chunk != null ? chunk.getValue() : null;
        String realName = debugValue == null || debugValue.getName().equals(originalDebugValue.getTempName())
            ? originalDebugValue.getName()
            : chunk.getSlicePresentation();
        String shownName = realName;
        if (modifier && !Objects.equals(myDataViewerModel.getOriginalVarName(), shownName)) {
            // We do not want here any localization, this is variable name format.
            shownName = String.format(MODIFIED_VARIABLE_FORMAT, myDataViewerModel.getOriginalVarName());
        }
        else {
            myDataViewerModel.setOriginalVarName(realName);
        }
        String originalVarName = myDataViewerModel.getOriginalVarName();
        if (originalVarName != null) {
            setSlicingValueFromUI(originalVarName);
        }

        // Modifier flag means that variable changes are temporary
        myDataViewerModel.setModifiedVarName(realName);

        for (OnNameChangedListener listener : listeners) {
            listener.onNameChanged(shownName);
        }

        if (chunk != null) {
            setFormatValueFromUI(chunk.getFormat());
        }
    }

    protected @Nullable PyDebugValue getDebugValue(@Nullable String expression, boolean modifier) {
        try {
            PyDebugValue debugValue = myDataViewerModel.getFrameAccessor().evaluate(expression, false, true);
            if (debugValue == null || debugValue.isErrorOnEval()) {
                Application.get().invokeLater(() -> {
                    String debugValueExpression = debugValue != null ? debugValue.getValue() : null;
                    String errorText;
                    if (debugValue != null && debugValueExpression != null) {
                        errorText = debugValueExpression;
                    }
                    else {
                        errorText = PyLocalize.debuggerDataViewFailedToEvaluateExpression(expression).get();
                    }

                    setError(errorText, modifier);
                });
                return null;
            }
            else {
                return debugValue;
            }
        }
        catch (PyDebuggerException e) {
            Application.get().invokeLater(() -> setError(e.getTracebackError(), modifier)); //NON-NLS
            return null;
        }
    }

    public void addNameChangedListener(OnNameChangedListener listener) {
        listeners.add(listener);
    }

    protected String composeErrorMessage(String text, boolean modifier) {
        return modifier ? PyLocalize.debuggerDataviewerModifierError(text).get() : text;
    }

    @FunctionalInterface
    public interface OnNameChangedListener {
        void onNameChanged(String name);
    }
}
