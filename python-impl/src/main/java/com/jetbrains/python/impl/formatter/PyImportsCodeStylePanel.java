// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.impl.formatter;

import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.formatter.PyCodeStyleSettings;
import consulo.codeEditor.EditorHighlighter;
import consulo.colorScheme.EditorColorsScheme;
import consulo.language.codeStyle.CodeStyleSettings;
import consulo.language.codeStyle.ui.setting.CodeStyleAbstractPanel;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PySyntaxLocalize;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.IdeBorderFactory;
import consulo.ui.ex.awt.JBCheckBox;
import consulo.ui.ex.awt.JBRadioButton;
import consulo.virtualFileSystem.fileType.FileType;
import org.jspecify.annotations.Nullable;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.border.TitledBorder;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

/**
 * @author Mikhail Golubev
 */
public class PyImportsCodeStylePanel extends CodeStyleAbstractPanel {
    private final JBCheckBox mySortImports;
    private final JBCheckBox mySortNamesInFromImports;
    private final JBCheckBox mySortImportsByTypeFirst;
    private final JPanel myRootPanel;
    private final JBCheckBox mySortCaseInsensitively;
    private final JBRadioButton myDoNothingWithFromImports;
    private final JBRadioButton myJoinFromImportsWithSameSource;
    private final JBRadioButton myAlwaysSplitFromImports;

    @RequiredUIAccess
    public PyImportsCodeStylePanel(CodeStyleSettings settings) {
        super(PythonLanguage.getInstance(), null, settings);
        {
            // Consulo: the JB GUI Designer form (PyImportsCodeStylePanel.form) is built in code with GridBagLayout
            myRootPanel = new JPanel(new GridBagLayout());
            myRootPanel.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));

            JPanel panel1 = new JPanel(new GridBagLayout());
            panel1.setBorder(IdeBorderFactory.PlainSmallWithIndent.createTitledBorder(
                null,
                PySyntaxLocalize.formatterImportsPanelOptimizeImports().get(),
                TitledBorder.DEFAULT_JUSTIFICATION,
                TitledBorder.DEFAULT_POSITION,
                null,
                null
            ));
            myRootPanel.add(panel1, constraints(0, 0, 0, GridBagConstraints.BOTH, 0, 0));
            // spacers of the form: grow to the right and to the bottom
            myRootPanel.add(new JPanel(), constraints(1, 0, 0, GridBagConstraints.HORIZONTAL, 1, 0));
            myRootPanel.add(new JPanel(), constraints(0, 1, 0, GridBagConstraints.VERTICAL, 0, 1));

            mySortImports = new JBCheckBox(PySyntaxLocalize.formatterImportsPanelSortImports().get());
            panel1.add(mySortImports, constraints(0, 0, 0, GridBagConstraints.NONE, 0, 0));
            mySortNamesInFromImports = new JBCheckBox(PySyntaxLocalize.formatterImportsPanelSortNamesInFromImports().get());
            panel1.add(mySortNamesInFromImports, constraints(0, 1, 2, GridBagConstraints.NONE, 0, 0));
            mySortImportsByTypeFirst = new JBCheckBox(PySyntaxLocalize.formatterImportsPanelSortByType().get());
            panel1.add(mySortImportsByTypeFirst, constraints(0, 2, 2, GridBagConstraints.NONE, 0, 0));
            mySortCaseInsensitively = new JBCheckBox(PySyntaxLocalize.formatterImportsPanelSortCaseInsensitively().get());
            mySortCaseInsensitively.setSelected(false);
            panel1.add(mySortCaseInsensitively, constraints(0, 3, 2, GridBagConstraints.NONE, 0, 0));

            JPanel panel2 = new JPanel(new GridBagLayout());
            panel2.setBorder(IdeBorderFactory.PlainSmallWithIndent.createTitledBorder(
                BorderFactory.createEtchedBorder(),
                PySyntaxLocalize.formatterImportsPanelFromImportsStructureTitle().get(),
                TitledBorder.DEFAULT_JUSTIFICATION,
                TitledBorder.DEFAULT_POSITION,
                null,
                null
            ));
            panel1.add(panel2, constraints(0, 4, 0, GridBagConstraints.BOTH, 1, 0));

            myDoNothingWithFromImports = new JBRadioButton(PySyntaxLocalize.formatterImportsPanelFromImportsLeaveAsIs().get());
            panel2.add(myDoNothingWithFromImports, constraints(0, 0, 0, GridBagConstraints.NONE, 0, 0));
            panel2.add(new JPanel(), constraints(1, 0, 0, GridBagConstraints.HORIZONTAL, 1, 0));
            myJoinFromImportsWithSameSource =
                new JBRadioButton(PySyntaxLocalize.formatterImportsPanelFromImportsJoinWithSameSource().get());
            myJoinFromImportsWithSameSource.setSelected(false);
            panel2.add(myJoinFromImportsWithSameSource, constraints(0, 1, 0, GridBagConstraints.NONE, 0, 0));
            myAlwaysSplitFromImports = new JBRadioButton(PySyntaxLocalize.formatterImportsPanelFromImportsAlwaysSplit().get());
            panel2.add(myAlwaysSplitFromImports, constraints(0, 2, 0, GridBagConstraints.NONE, 0, 0));

            ButtonGroup buttonGroup = new ButtonGroup();
            buttonGroup.add(myDoNothingWithFromImports);
            buttonGroup.add(myJoinFromImportsWithSameSource);
            buttonGroup.add(myAlwaysSplitFromImports);
        }
        addPanelToWatch(myRootPanel);

        mySortImports.addActionListener(e -> {
            boolean sortingEnabled = mySortImports.isSelected();
            mySortNamesInFromImports.setEnabled(sortingEnabled);
            mySortImportsByTypeFirst.setEnabled(sortingEnabled);
            mySortCaseInsensitively.setEnabled(sortingEnabled);
        });
    }

    /**
     * Consulo: replacement for the GUI Designer GridConstraints (indent is in GUI Designer units of 10px).
     */
    static GridBagConstraints constraints(int x, int y, int indent, int fill, double weightX, double weightY) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = x;
        c.gridy = y;
        c.fill = fill;
        c.weightx = weightX;
        c.weighty = weightY;
        c.anchor = GridBagConstraints.NORTHWEST;
        c.insets = new Insets(0, indent * 10, 0, 0);
        return c;
    }

    @Override
    protected LocalizeValue getTabTitle() {
        return PySyntaxLocalize.formatterImportsPanelTitle();
    }

    @Override
    protected FileType getFileType() {
        return PythonFileType.INSTANCE;
    }

    @Override
    protected @Nullable EditorHighlighter createHighlighter(EditorColorsScheme scheme) {
        return null;
    }

    @Override
    protected @Nullable String getPreviewText() {
        return null;
    }

    @Override
    protected int getRightMargin() {
        return 0;
    }

    @Override
    public void apply(CodeStyleSettings settings) {
        PyCodeStyleSettings pySettings = settings.getCustomSettings(PyCodeStyleSettings.class);

        pySettings.OPTIMIZE_IMPORTS_SORT_IMPORTS = mySortImports.isSelected();
        pySettings.OPTIMIZE_IMPORTS_SORT_NAMES_IN_FROM_IMPORTS = mySortNamesInFromImports.isSelected();
        pySettings.OPTIMIZE_IMPORTS_SORT_BY_TYPE_FIRST = mySortImportsByTypeFirst.isSelected();
        pySettings.OPTIMIZE_IMPORTS_JOIN_FROM_IMPORTS_WITH_SAME_SOURCE = myJoinFromImportsWithSameSource.isSelected();
        pySettings.OPTIMIZE_IMPORTS_ALWAYS_SPLIT_FROM_IMPORTS = myAlwaysSplitFromImports.isSelected();
        pySettings.OPTIMIZE_IMPORTS_CASE_INSENSITIVE_ORDER = mySortCaseInsensitively.isSelected();
    }

    @Override
    public boolean isModified(CodeStyleSettings settings) {
        PyCodeStyleSettings pySettings = settings.getCustomSettings(PyCodeStyleSettings.class);

        return mySortImports.isSelected() != pySettings.OPTIMIZE_IMPORTS_SORT_IMPORTS ||
            mySortNamesInFromImports.isSelected() != pySettings.OPTIMIZE_IMPORTS_SORT_NAMES_IN_FROM_IMPORTS ||
            mySortImportsByTypeFirst.isSelected() != pySettings.OPTIMIZE_IMPORTS_SORT_BY_TYPE_FIRST ||
            myJoinFromImportsWithSameSource.isSelected() != pySettings.OPTIMIZE_IMPORTS_JOIN_FROM_IMPORTS_WITH_SAME_SOURCE ||
            myAlwaysSplitFromImports.isSelected() != pySettings.OPTIMIZE_IMPORTS_ALWAYS_SPLIT_FROM_IMPORTS ||
            mySortCaseInsensitively.isSelected() != pySettings.OPTIMIZE_IMPORTS_CASE_INSENSITIVE_ORDER;
    }

    @Override
    public @Nullable JComponent getPanel() {
        return myRootPanel;
    }

    @Override
    protected void resetImpl(CodeStyleSettings settings) {
        PyCodeStyleSettings pySettings = settings.getCustomSettings(PyCodeStyleSettings.class);

        mySortImports.setSelected(pySettings.OPTIMIZE_IMPORTS_SORT_IMPORTS);
        mySortNamesInFromImports.setSelected(pySettings.OPTIMIZE_IMPORTS_SORT_NAMES_IN_FROM_IMPORTS);
        mySortNamesInFromImports.setEnabled(mySortImports.isSelected());
        mySortImportsByTypeFirst.setSelected(pySettings.OPTIMIZE_IMPORTS_SORT_BY_TYPE_FIRST);
        mySortImportsByTypeFirst.setEnabled(mySortImports.isSelected());
        mySortCaseInsensitively.setSelected(pySettings.OPTIMIZE_IMPORTS_CASE_INSENSITIVE_ORDER);

        if (pySettings.OPTIMIZE_IMPORTS_JOIN_FROM_IMPORTS_WITH_SAME_SOURCE) {
            myJoinFromImportsWithSameSource.setSelected(true);
        }
        else if (pySettings.OPTIMIZE_IMPORTS_ALWAYS_SPLIT_FROM_IMPORTS) {
            myAlwaysSplitFromImports.setSelected(true);
        }
        else {
            myDoNothingWithFromImports.setSelected(true);
        }
    }
}
