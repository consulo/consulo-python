// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
package com.jetbrains.python.impl.debugger.variablesview.usertyperenderers;

import com.jetbrains.python.impl.debugger.PyDebugProcess;
import com.jetbrains.python.impl.debugger.PyDebuggerEditorsProvider;
import com.jetbrains.python.impl.debugger.variablesview.usertyperenderers.codeinsight.PyTypeNameResolver;
import com.jetbrains.python.impl.debugger.variablesview.usertyperenderers.codeinsight.TypeNameCompletionProvider;
import com.jetbrains.python.impl.psi.impl.PyExpressionCodeFragmentImpl;
import com.jetbrains.python.impl.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.psi.PyClass;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.Application;
import consulo.configurable.ApplicationConfigurable;
import consulo.configurable.SearchableConfigurable;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.document.event.DocumentEvent;
import consulo.document.event.DocumentListener;
import consulo.execution.debug.XDebugSession;
import consulo.execution.debug.XDebuggerManager;
import consulo.execution.debug.XDebuggerUtil;
import consulo.execution.debug.XSourcePosition;
import consulo.execution.debug.breakpoint.XExpression;
import consulo.execution.debug.ui.XDebuggerExpressionEditor;
import consulo.execution.debug.ui.XDebuggerExpressionEditorFactory;
import consulo.language.editor.ui.awt.TextFieldWithCompletion;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.QualifiedName;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.project.ProjectManager;
import consulo.python.impl.localize.PyLocalize;
import consulo.ui.Component;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.ActionToolbarPosition;
import consulo.ui.ex.awt.AnActionButton;
import consulo.ui.ex.awt.AnActionButtonRunnable;
import consulo.ui.ex.awt.ComponentValidator;
import consulo.ui.ex.awt.ElementsChooser;
import consulo.ui.ex.awt.FormBuilder;
import consulo.ui.ex.awt.JBCheckBox;
import consulo.ui.ex.awt.JBLabel;
import consulo.ui.ex.awt.JBRadioButton;
import consulo.ui.ex.awt.JBTextField;
import consulo.ui.ex.awt.JBUI;
import consulo.ui.ex.awt.Splitter;
import consulo.ui.ex.awt.ToolbarDecorator;
import consulo.ui.ex.awt.ValidationInfo;
import consulo.ui.ex.awt.event.DocumentAdapter;
import consulo.ui.ex.awt.table.JBTable;
import consulo.ui.ex.awt.util.TableUtil;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Consulo-specific differences from JB:
 * <ul>
 * <li>JB registers it as an applicationConfigurable in the group "reference.idesettings.debugger.python";
 * here it is an {@link ExtensionImpl} whose parent is the Python debugger page.</li>
 * <li>JB shows a description row on top that says type renderers work only in debugpy mode, with a link to the
 * backend selector on the Python debugger page. Consulo has no debugger backend switching (pydevd supports
 * user type renderers, see PyDebugProcess.setUserTypeRenderersSettings), so the row is not ported.</li>
 * <li>The Kotlin UI DSL settings panel is built with {@link FormBuilder}, and the Swing panel is wrapped
 * with {@link TargetAWT#wrap} for {@link #createUIComponent(Disposable)}.</li>
 * </ul>
 */
@ExtensionImpl
public class PyUserTypeRenderersConfigurable implements SearchableConfigurable, ApplicationConfigurable {
    private static final String CONFIGURABLE_ID = "debugger.dataViews.python.type.renderers";

    private static final String PY_DEBUGGER_CONFIGURABLE_ID = "reference.idesettings.debugger.python";

    private static final float PANEL_SPLIT_PROPORTION = 0.3f;

    private final JPanel myMainPanel = new JPanel(new BorderLayout());
    private @Nullable PyUserNodeRenderer myCurrentRenderer;

    private final Project myProject;
    private boolean myDisposed;
    private final ElementsChooser<PyUserNodeRenderer> myRendererChooser;
    private @Nullable RendererSettings myRendererSettings;

    private @Nullable Integer myRendererIndexToSelect;
    private @Nullable PyUserNodeRenderer myNewRendererToAdd;

    public PyUserTypeRenderersConfigurable() {
        ProjectManager projectManager = ProjectManager.getInstance();
        Project[] openProjects = projectManager.getOpenProjects();
        myProject = openProjects.length > 0 ? openProjects[0] : projectManager.getDefaultProject();
        myRendererChooser = new ElementsChooser<>(true);
    }

    public void setRendererIndexToSelect(@Nullable Integer index) {
        myRendererIndexToSelect = index;
    }

    public void setNewRendererToAdd(@Nullable PyUserNodeRenderer renderer) {
        myNewRendererToAdd = renderer;
    }

    @Override
    @RequiredUIAccess
    public Component createUIComponent(Disposable uiDisposable) {
        Application.get().invokeLater(() -> {
            if (myProject.isDisposed() || myDisposed) {
                return;
            }
            RendererSettings rendererSettings = new RendererSettings();
            myRendererSettings = rendererSettings;
            setupRendererSettings();
            setupRendererChooser();
            ToolbarDecorator chooserDecorator = new RendererChooserToolbarDecorator().myDecorator;
            Splitter splitter = new Splitter(false);
            splitter.setProportion(PANEL_SPLIT_PROPORTION);
            splitter.setFirstComponent(chooserDecorator.createPanel());
            splitter.setSecondComponent(rendererSettings);
            if (myRendererChooser.getElementCount() > 0) {
                int index = myRendererIndexToSelect != null ? myRendererIndexToSelect : 0;
                PyUserNodeRenderer first = myRendererChooser.getElementAt(index);
                myRendererChooser.selectElements(List.of(first));
            }
            myMainPanel.removeAll();
            // TODO consulo: JB adds a description row here (type renderers work only in debugpy mode, with a link that
            //  opens the backend selector on the PY_DEBUGGER_CONFIGURABLE_ID page). Consulo has no debugger backend switching.

            myMainPanel.add(splitter, BorderLayout.CENTER);
        });
        return TargetAWT.wrap(myMainPanel);
    }

    @Override
    @RequiredUIAccess
    public void reset() {
        myRendererChooser.removeAllElements();
        myCurrentRenderer = null;

        PyUserTypeRenderersSettings settings = PyUserTypeRenderersSettings.getInstance();

        PyUserNodeRenderer newRendererToAdd = myNewRendererToAdd;
        if (newRendererToAdd != null) {
            PyUserNodeRenderer newRender = newRendererToAdd.clone();
            myRendererChooser.addElement(newRender, newRender.isEnabled());
        }

        for (PyUserNodeRenderer render : settings.getRenderers()) {
            PyUserNodeRenderer newRenderer = render.clone();
            myRendererChooser.addElement(newRenderer, newRenderer.isEnabled());
        }

        List<PyUserNodeRenderer> selectedRenderers = new ArrayList<>();
        Integer indexToSelect = myRendererIndexToSelect;
        if (indexToSelect != null && indexToSelect >= 0 && indexToSelect < myRendererChooser.getElementCount()) {
            PyUserNodeRenderer renderer = myRendererChooser.getElementAt(indexToSelect);
            selectedRenderers.add(renderer);
        }
        else if (myRendererChooser.getElementCount() != 0) {
            PyUserNodeRenderer firstRenderer = myRendererChooser.getElementAt(0);
            selectedRenderers.add(firstRenderer);
        }

        myRendererChooser.selectElements(selectedRenderers);
        if (myRendererSettings != null) {
            myRendererSettings.reset();
        }
    }

    @Override
    @RequiredUIAccess
    public boolean isModified() {
        if (myRendererSettings != null && myRendererSettings.isModified()) {
            return true;
        }
        PyUserTypeRenderersSettings settings = PyUserTypeRenderersSettings.getInstance();
        List<PyUserNodeRenderer> renderers = settings.getRenderers();
        if (myRendererChooser.getElementCount() != renderers.size()) {
            return true;
        }
        for (int index = 0; index < renderers.size(); index++) {
            PyUserNodeRenderer element = myRendererChooser.getElementAt(index);
            if (!element.equalTo(renderers.get(index))) {
                return true;
            }
        }
        return false;
    }

    @Override
    @RequiredUIAccess
    public void apply() {
        if (myRendererSettings != null) {
            myRendererSettings.apply();
        }
        PyUserTypeRenderersSettings settings = PyUserTypeRenderersSettings.getInstance();
        List<PyUserNodeRenderer> renderers = new ArrayList<>();
        for (int i = 0; i < myRendererChooser.getElementCount(); i++) {
            PyUserNodeRenderer element = myRendererChooser.getElementAt(i);
            renderers.add(element.clone());
        }
        settings.setRenderers(renderers);
        applyRenderersToDebugger();
    }

    private void applyRenderersToDebugger() {
        XDebugSession debugSession = XDebuggerManager.getInstance(myProject).getCurrentSession();
        if (debugSession != null && debugSession.getDebugProcess() instanceof PyDebugProcess debugProcess) {
            debugProcess.setUserTypeRenderersSettings();
            debugProcess.dropFrameCaches();
            debugProcess.getSession().rebuildViews();
        }
    }

    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.configurablePyusertyperenderersconfigurableDisplayName();
    }

    @Override
    public String getId() {
        return CONFIGURABLE_ID;
    }

    @Override
    public @Nullable String getParentId() {
        return PY_DEBUGGER_CONFIGURABLE_ID;
    }

    @Override
    public String getHelpTopic() {
        return "reference.idesettings.debugger.typerenderers";
    }

    private void setupRendererChooser() {
        myRendererChooser.getEmptyText().setText(PyLocalize.formDebuggerVariablesViewUserTypeRenderersNoRenderers());
        myRendererChooser.addElementsMarkListener((ElementsChooser.ElementsMarkListener<PyUserNodeRenderer>) (element, isMarked) ->
            element.setEnabled(isMarked));
        myRendererChooser.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateCurrentRenderer(myRendererChooser.getSelectedElements());
            }
        });
    }

    private void setupRendererSettings() {
        if (myRendererSettings != null) {
            myRendererSettings.setVisible(false);
        }
    }

    private void updateCurrentRendererName(String newName) {
        PyUserNodeRenderer currentRenderer = myCurrentRenderer;
        if (currentRenderer != null) {
            currentRenderer.setName(newName);
            myRendererChooser.refresh(currentRenderer);
        }
    }

    private void updateCurrentRenderer(List<PyUserNodeRenderer> selectedElements) {
        if (selectedElements.size() == 1) {
            setCurrentRenderer(selectedElements.get(0));
        }
        else {
            setCurrentRenderer(null);
        }
    }

    private void setCurrentRenderer(@Nullable PyUserNodeRenderer renderer) {
        if (myCurrentRenderer == renderer) {
            return;
        }
        RendererSettings rendererSettings = myRendererSettings;
        if (rendererSettings == null) {
            return;
        }
        if (rendererSettings.isModified()) {
            rendererSettings.apply();
        }
        myCurrentRenderer = renderer;
        rendererSettings.reset();
    }

    public List<String> getCurrentlyVisibleNames() {
        List<String> resultList = new ArrayList<>();
        for (int i = 0; i < myRendererChooser.getElementCount(); i++) {
            PyUserNodeRenderer element = myRendererChooser.getElementAt(i);
            if (element != null) {
                resultList.add(element.getName());
            }
        }
        return resultList;
    }

    private class RendererChooserToolbarDecorator {
        // Consulo ToolbarDecorator has no createDecorator(JComponent); the chooser's component is its JTable
        private final ToolbarDecorator myDecorator = ToolbarDecorator.createDecorator((JTable) myRendererChooser.getComponent());

        RendererChooserToolbarDecorator() {
            myDecorator.setToolbarPosition(ActionToolbarPosition.TOP);
            myDecorator.setAddAction(new AddAction());
            myDecorator.setRemoveAction(new RemoveAction());
            myDecorator.setMoveUpAction(new MoveAction(true));
            myDecorator.setMoveDownAction(new MoveAction(false));
        }

        private class AddAction implements AnActionButtonRunnable {
            @Override
            public void run(AnActionButton button) {
                PyUserNodeRenderer renderer = new PyUserNodeRenderer(true, getCurrentlyVisibleNames());
                myRendererChooser.addElement(renderer, renderer.isEnabled());
                myRendererChooser.moveElement(renderer, 0);
            }
        }

        private class RemoveAction implements AnActionButtonRunnable {
            @Override
            public void run(AnActionButton button) {
                for (PyUserNodeRenderer element : myRendererChooser.getSelectedElements()) {
                    myRendererChooser.removeElement(element);
                }
            }
        }

        private class MoveAction implements AnActionButtonRunnable {
            private final boolean myMoveUp;

            MoveAction(boolean moveUp) {
                myMoveUp = moveUp;
            }

            @Override
            public void run(AnActionButton button) {
                int selectedRow = myRendererChooser.getSelectedElementRow();
                if (selectedRow < 0) {
                    return;
                }
                int newRow = selectedRow + (myMoveUp ? -1 : 1);
                if (newRow < 0) {
                    newRow = myRendererChooser.getElementCount() - 1;
                }
                else if (newRow >= myRendererChooser.getElementCount()) {
                    newRow = 0;
                }
                myRendererChooser.moveElement(myRendererChooser.getElementAt(selectedRow), newRow);
            }
        }
    }

    @Override
    @RequiredUIAccess
    public void disposeUIResources() {
        RendererSettings rendererSettings = myRendererSettings;
        if (rendererSettings != null) {
            Disposer.dispose(rendererSettings);
        }
        myDisposed = true;
    }

    private class RendererSettings extends JPanel implements Disposable {
        private final JPanel myPanel;
        private final JTextField myRendererNameTextField = new JBTextField();
        private final JCheckBox myAppendDefaultChildrenCheckBox =
            new JBCheckBox(PyLocalize.formDebuggerVariablesViewUserTypeRenderersAppendDefaultChildren().get());
        private final JRadioButton myRbDefaultValueRenderer =
            new JBRadioButton(PyLocalize.formDebuggerVariablesViewUserTypeRenderersUseDefaultRenderer().get());
        private final JRadioButton myRbExpressionValueRenderer =
            new JBRadioButton(PyLocalize.formDebuggerVariablesViewUserTypeRenderersUseFollowingExpression().get());
        private final JRadioButton myRbDefaultChildrenRenderer =
            new JBRadioButton(PyLocalize.formDebuggerVariablesViewUserTypeRenderersUseDefaultRenderer().get());
        private final JRadioButton myRbListChildrenRenderer =
            new JBRadioButton(PyLocalize.formDebuggerVariablesViewUserTypeRenderersUseListOfExpressions().get());
        private final TextFieldWithCompletion myTypeNameTextField =
            new TextFieldWithCompletion(myProject, new TypeNameCompletionProvider(myProject), "", true, true, true);
        private final XDebuggerExpressionEditor myNodeValueExpressionEditor = myProject.getInstance(XDebuggerExpressionEditorFactory.class)
            .create(new PyDebuggerEditorsProvider(), "NodeValueExpression", null, XExpression.EMPTY_EXPRESSION, false, false, true);
        private final JComponent myChildrenRenderersListEditor;
        private final ChildrenListEditorTableModel myChildrenListEditorTableModel;
        private final JBTable myChildrenListEditorTable;

        RendererSettings() {
            super(new BorderLayout());
            myChildrenListEditorTableModel = new ChildrenListEditorTableModel();
            myChildrenListEditorTable = new JBTable(myChildrenListEditorTableModel);
            myChildrenRenderersListEditor = createChildrenListEditor();

            setupTypeNameEditor();
            myPanel = createSettingsPanel();
            setupPanelComponents();
            add(myPanel, BorderLayout.NORTH);
        }

        private JPanel createSettingsPanel() {
            ButtonGroup valueGroup = new ButtonGroup();
            valueGroup.add(myRbDefaultValueRenderer);
            valueGroup.add(myRbExpressionValueRenderer);
            ButtonGroup childrenGroup = new ButtonGroup();
            childrenGroup.add(myRbDefaultChildrenRenderer);
            childrenGroup.add(myRbListChildrenRenderer);

            JPanel valueExpressionPanel = FormBuilder.createFormBuilder()
                .addComponent(TargetAWT.to(myNodeValueExpressionEditor.getUIComponent()))
                .addTooltip(PyLocalize.formDebuggerVariablesViewUserTypeRenderersVariableName().get())
                .getPanel();
            valueExpressionPanel.setBorder(JBUI.Borders.emptyLeft(20));

            JPanel childrenListPanel = FormBuilder.createFormBuilder()
                .addComponent(myChildrenRenderersListEditor)
                .addTooltip(PyLocalize.formDebuggerVariablesViewUserTypeRenderersVariableName().get())
                .addComponent(myAppendDefaultChildrenCheckBox)
                .getPanel();
            childrenListPanel.setBorder(JBUI.Borders.emptyLeft(20));

            return FormBuilder.createFormBuilder()
                .addLabeledComponent(PyLocalize.formDebuggerVariablesViewUserTypeRenderersName().get(), myRendererNameTextField)
                .addLabeledComponent(
                    new JBLabel(PyLocalize.formDebuggerVariablesViewUserTypeRenderersApplyRendererToObjectsOfType().get()),
                    myTypeNameTextField,
                    true
                )
                .addComponent(new JBLabel(PyLocalize.formDebuggerVariablesViewUserTypeRenderersWhenRenderingNode().get()))
                .addComponent(myRbDefaultValueRenderer)
                .addComponent(myRbExpressionValueRenderer)
                .addComponent(valueExpressionPanel)
                .addComponent(new JBLabel(PyLocalize.formDebuggerVariablesViewUserTypeRenderersWhenExpandingNode().get()))
                .addComponent(myRbDefaultChildrenRenderer)
                .addComponent(myRbListChildrenRenderer)
                .addComponent(childrenListPanel)
                .getPanel();
        }

        private void setupRendererNameField() {
            myRendererNameTextField.getDocument().addDocumentListener(new DocumentAdapter() {
                @Override
                protected void textChanged(javax.swing.event.DocumentEvent e) {
                    String newName = myRendererNameTextField.getText();
                    updateCurrentRendererName(newName);
                }
            });
        }

        private void setupTypeNameEditor() {
            ComponentValidator myTypeNameFieldValidator = new ComponentValidator(this).withValidator(
                (Supplier<ValidationInfo>) () -> {
                    String text = myTypeNameTextField.getText();
                    if (!isValidTypeName(text)) {
                        return new ValidationInfo(PyLocalize.formDebuggerVariablesViewUserTypeRenderersClassNotFound(),
                            myTypeNameTextField);
                    }
                    else {
                        return null;
                    }
                }).installOn(myTypeNameTextField);

            myTypeNameTextField.addFocusListener(new FocusAdapter() {
                @Override
                public void focusLost(FocusEvent e) {
                    myTypeNameFieldValidator.revalidate();
                }
            });

            myTypeNameTextField.addDocumentListener(new DocumentListener() {
                @Override
                public void documentChanged(DocumentEvent event) {
                    updateSelfType();
                }
            });
        }

        private boolean isValidTypeName(String typeName) {
            return new PyTypeNameResolver(myProject).resolve(typeName) != null;
        }

        private void updateSelfType() {
            String typeName = myTypeNameTextField.getText();
            int lastDot = typeName.lastIndexOf('.');
            String moduleName = lastDot >= 0 ? typeName.substring(0, lastDot) : typeName;
            String className = lastDot >= 0 ? typeName.substring(lastDot + 1) : typeName;
            String importLine = moduleName.equals(className) ? "" : "from " + moduleName + " import " + className;
            String contextText =
                importLine + "\n" +
                    "def foo(self: " + className + "):\n" +
                    "  pass";
            PsiFile contextWithSelf = new PyExpressionCodeFragmentImpl(myProject, "fragment.py", contextText, true);
            int offset = contextText.indexOf("def");
            VirtualFile virtualFile = contextWithSelf.getVirtualFile();
            XSourcePosition srcPosition = XDebuggerUtil.getInstance().createPositionByOffset(virtualFile, offset);
            myNodeValueExpressionEditor.setSourcePosition(srcPosition);
        }

        private void setupRadioButtons() {
            myRbDefaultValueRenderer.setSelected(true);
            myRbDefaultChildrenRenderer.setSelected(true);
            ActionListener listener = e -> {
                Object source = e.getSource();
                if (source == myRbDefaultChildrenRenderer) {
                    myAppendDefaultChildrenCheckBox.setEnabled(myRbListChildrenRenderer.isSelected());
                    myChildrenListEditorTable.setEnabled(myRbListChildrenRenderer.isSelected());
                }
                else if (source == myRbListChildrenRenderer) {
                    myAppendDefaultChildrenCheckBox.setEnabled(myRbListChildrenRenderer.isSelected());
                    myChildrenListEditorTable.setEnabled(myRbListChildrenRenderer.isSelected());
                    PyUserNodeRenderer renderer = myCurrentRenderer;
                    if (myChildrenListEditorTableModel.getRowCount() == 0 && renderer != null) {
                        PyClass pyClass = new PyTypeNameResolver(myProject).resolve(renderer.getToType());
                        if (pyClass != null) {
                            pyClass.visitClassAttributes(it -> {
                                myChildrenListEditorTableModel.addRow("self." + it.getName());
                                return true;
                            }, false, null);
                        }
                    }
                }
            };
            myRbDefaultValueRenderer.addActionListener(listener);
            myRbExpressionValueRenderer.addActionListener(listener);
            myRbDefaultChildrenRenderer.addActionListener(listener);
            myRbListChildrenRenderer.addActionListener(listener);
        }

        private void setupPanelComponents() {
            setupRendererNameField();
            setupRadioButtons();
            myAppendDefaultChildrenCheckBox.setEnabled(false);
            myChildrenListEditorTable.setEnabled(false);
        }

        private JComponent createChildrenListEditor() {
            myChildrenListEditorTable.setShowGrid(true);
            return new ChildrenListEditorToolbarDecorator(myChildrenListEditorTable, myChildrenListEditorTableModel).myDecorator.createPanel();
        }

        @Override
        public void setVisible(boolean aFlag) {
            myPanel.setVisible(aFlag);
        }

        boolean isModified() {
            PyUserNodeRenderer it = myCurrentRenderer;
            if (it != null) {
                return !it.getName().equals(myRendererNameTextField.getText()) ||
                    !it.getToType().equals(myTypeNameTextField.getText()) ||
                    it.getValueRenderer().isDefault() != myRbDefaultValueRenderer.isSelected() ||
                    !it.getValueRenderer().getExpression().equals(myNodeValueExpressionEditor.getExpression().getExpression()) ||
                    it.getChildrenRenderer().isDefault() != myRbDefaultChildrenRenderer.isSelected() ||
                    !it.getChildrenRenderer().getChildren().equals(myChildrenListEditorTableModel.getClonedChildren()) ||
                    it.getChildrenRenderer().getAppendDefaultChildren() != myAppendDefaultChildrenCheckBox.isSelected();
            }
            return false;
        }

        void apply() {
            PyUserNodeRenderer it = myCurrentRenderer;
            if (it != null) {
                it.setName(myRendererNameTextField.getText());
                it.setToType(myTypeNameTextField.getText());
                it.getValueRenderer().setDefault(myRbDefaultValueRenderer.isSelected());
                it.getValueRenderer().setExpression(myNodeValueExpressionEditor.getExpression().getExpression());
                it.getChildrenRenderer().setDefault(myRbDefaultChildrenRenderer.isSelected());
                it.getChildrenRenderer().setChildren(myChildrenListEditorTableModel.getClonedChildren());
                it.getChildrenRenderer().setAppendDefaultChildren(myAppendDefaultChildrenCheckBox.isSelected());
                resetTypeInfo(it);
            }
        }

        private void resetTypeInfo(PyUserNodeRenderer renderer) {
            renderer.setTypeSourceFile("");
            renderer.setModuleRootHasOneTypeWithSameName(false);

            String type = renderer.getToType();
            PyClass cls = new PyTypeNameResolver(myProject).resolve(type);
            if (cls == null) {
                return;
            }
            String clsName = cls.getName();
            if (clsName == null) {
                return;
            }
            QualifiedName importPath = QualifiedNameFinder.findCanonicalImportPath(cls, null);

            renderer.setTypeCanonicalImportPath(importPath != null ? importPath + "." + clsName : "");
            String qualifiedName = cls.getQualifiedName();
            renderer.setTypeQualifiedName(qualifiedName != null ? qualifiedName : "");
            PsiFile containingFile = cls.getContainingFile();
            VirtualFile virtualFile = containingFile != null ? containingFile.getVirtualFile() : null;
            renderer.setTypeSourceFile(virtualFile != null ? virtualFile.getPath() : "");
            renderer.setModuleRootHasOneTypeWithSameName(PyTypeNameResolver.getClassesNumberInModuleRootWithName(
                cls,
                importPath != null ? importPath : QualifiedName.fromComponents(),
                myProject
            ) == 1);
        }

        private void resetChildrenListEditorTableModel(PyUserNodeRenderer currentRenderer) {
            myChildrenListEditorTableModel.clear();
            for (PyUserNodeRenderer.ChildInfo child : currentRenderer.getChildrenRenderer().getChildren()) {
                myChildrenListEditorTableModel.addRow(child.getExpression());
            }
        }

        private void resetRadioButtons(PyUserNodeRenderer currentRenderer) {
            if (currentRenderer.getValueRenderer().isDefault()) {
                myRbDefaultValueRenderer.doClick();
            }
            else {
                myRbExpressionValueRenderer.doClick();
            }
            if (currentRenderer.getChildrenRenderer().isDefault()) {
                myRbDefaultChildrenRenderer.doClick();
            }
            else {
                myRbListChildrenRenderer.doClick();
            }
        }

        void reset() {
            PyUserNodeRenderer it = myCurrentRenderer;
            if (it != null) {
                myRendererNameTextField.setText(it.getName());
                myTypeNameTextField.setText(it.getToType());
                myNodeValueExpressionEditor.setExpression(XExpression.fromText(it.getValueRenderer().getExpression()));
                myAppendDefaultChildrenCheckBox.setSelected(it.getChildrenRenderer().getAppendDefaultChildren());
                resetRadioButtons(it);
                resetChildrenListEditorTableModel(it);
                myPanel.setVisible(true);
            }
            else {
                myPanel.setVisible(false);
            }
            updateSelfType();
        }

        @Override
        public void dispose() {
            // do nothing
        }

        private class ChildrenListEditorToolbarDecorator {
            private final JBTable myTable;
            private final ChildrenListEditorTableModel myTableModel;
            private final ToolbarDecorator myDecorator;

            ChildrenListEditorToolbarDecorator(JBTable table, ChildrenListEditorTableModel tableModel) {
                myTable = table;
                myTableModel = tableModel;
                myDecorator = ToolbarDecorator.createDecorator(table);
                myDecorator.setToolbarPosition(ActionToolbarPosition.TOP);
                myDecorator.setAddAction(new AddAction());
                myDecorator.setRemoveAction(new RemoveAction());
                myDecorator.setMoveUpAction(new MoveAction(true));
                myDecorator.setMoveDownAction(new MoveAction(false));
            }

            private class AddAction implements AnActionButtonRunnable {
                @Override
                public void run(AnActionButton button) {
                    myTableModel.addRow("");
                }
            }

            private class RemoveAction implements AnActionButtonRunnable {
                @Override
                public void run(AnActionButton button) {
                    myTableModel.removeRows(myTable.getSelectedRows());
                }
            }

            private class MoveAction implements AnActionButtonRunnable {
                private final boolean myUp;

                MoveAction(boolean up) {
                    myUp = up;
                }

                @Override
                public void run(AnActionButton button) {
                    if (myUp) {
                        TableUtil.moveSelectedItemsUp(myTable);
                    }
                    else {
                        TableUtil.moveSelectedItemsDown(myTable);
                    }
                }
            }
        }

        private class ChildrenListEditorTableModel extends AbstractTableModel {
            private static final int COLUMN_COUNT = 1;
            private static final int EXPRESSION_TABLE_COLUMN = 0;

            private final List<PyUserNodeRenderer.ChildInfo> myData = new ArrayList<>();

            @Override
            public int getColumnCount() {
                return COLUMN_COUNT;
            }

            @Override
            public int getRowCount() {
                return myData.size();
            }

            @Override
            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return true;
            }

            @Override
            public Class<?> getColumnClass(int columnIndex) {
                if (columnIndex == EXPRESSION_TABLE_COLUMN) {
                    return String.class;
                }
                return super.getColumnClass(columnIndex);
            }

            @Override
            public @Nullable Object getValueAt(int rowIndex, int columnIndex) {
                if (rowIndex >= getRowCount()) {
                    return null;
                }
                PyUserNodeRenderer.ChildInfo row = myData.get(rowIndex);
                if (columnIndex == EXPRESSION_TABLE_COLUMN) {
                    return row.getExpression();
                }
                return null;
            }

            @Override
            public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
                if (rowIndex >= getRowCount()) {
                    return;
                }
                PyUserNodeRenderer.ChildInfo row = myData.get(rowIndex);
                if (columnIndex == EXPRESSION_TABLE_COLUMN) {
                    row.setExpression((String) aValue);
                }
            }

            @Override
            public @Nullable String getColumnName(int columnIndex) {
                if (columnIndex == EXPRESSION_TABLE_COLUMN) {
                    return null;
                }
                return "";
            }

            void addRow(String expression) {
                myData.add(new PyUserNodeRenderer.ChildInfo(expression));
                int lastRow = myData.size() - 1;
                fireTableRowsInserted(lastRow, lastRow);
            }

            void removeRows(int[] rows) {
                List<PyUserNodeRenderer.ChildInfo> nonSelected = new ArrayList<>();
                for (int id = 0; id < myData.size(); id++) {
                    boolean selected = false;
                    for (int row : rows) {
                        if (row == id) {
                            selected = true;
                            break;
                        }
                    }
                    if (!selected) {
                        nonSelected.add(myData.get(id));
                    }
                }
                myData.clear();
                myData.addAll(nonSelected);
                fireTableDataChanged();
            }

            void clear() {
                myData.clear();
                fireTableDataChanged();
            }

            List<PyUserNodeRenderer.ChildInfo> getClonedChildren() {
                return List.copyOf(myData);
            }
        }
    }
}
