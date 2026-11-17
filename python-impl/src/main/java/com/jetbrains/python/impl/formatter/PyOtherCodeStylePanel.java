// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.impl.formatter;

import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.formatter.PyCodeStyleSettings;
import com.jetbrains.python.formatter.PyCodeStyleSettings.DictAlignment;
import com.jetbrains.python.highlighting.PyHighlighter;
import com.jetbrains.python.psi.LanguageLevel;
import consulo.codeEditor.EditorHighlighter;
import consulo.colorScheme.EditorColorsScheme;
import consulo.language.codeStyle.CodeStyleSettings;
import consulo.language.codeStyle.ui.setting.CodeStyleAbstractPanel;
import consulo.language.editor.highlight.HighlighterFactory;
import consulo.python.impl.localize.PySyntaxLocalize;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.ComboBox;
import consulo.ui.ex.awt.IdeBorderFactory;
import consulo.ui.ex.awt.JBCheckBox;
import consulo.ui.ex.awt.JBLabel;
import consulo.ui.ex.awt.SimpleListCellRenderer;
import consulo.virtualFileSystem.fileType.FileType;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.border.TitledBorder;
import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;

import static com.jetbrains.python.impl.formatter.PyImportsCodeStylePanel.constraints;

public class PyOtherCodeStylePanel extends CodeStyleAbstractPanel {

    private final JPanel myPanel;
    private final JBCheckBox myAddTrailingBlankLineCheckbox;
    private final ComboBox<DictAlignment> myDictAlignmentCombo;
    private final JPanel myPreviewPanel;
    private final JBCheckBox myFormatInjectedFragments;
    private final JBCheckBox myAddIndentInsideInjections;

    @RequiredUIAccess
    protected PyOtherCodeStylePanel(CodeStyleSettings settings) {
        super(PythonLanguage.getInstance(), null, settings);
        {
            // Consulo: the JB GUI Designer form (PyOtherCodeStylePanel.form) is built in code:
            // options on the left (top-aligned), preview on the right
            myPanel = new JPanel(new BorderLayout());
            myPreviewPanel = new JPanel();
            myPanel.add(myPreviewPanel, BorderLayout.CENTER);

            JPanel panel1 = new JPanel(new GridBagLayout());
            panel1.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            panel1.setOpaque(true);
            JPanel leftColumn = new JPanel(new BorderLayout());
            leftColumn.add(panel1, BorderLayout.NORTH);
            myPanel.add(leftColumn, BorderLayout.WEST);

            JBLabel jBLabel1 = new JBLabel(PySyntaxLocalize.formatterPanelDictAlignmentLabel().get());
            panel1.add(jBLabel1, constraints(0, 0, 0, GridBagConstraints.NONE, 0, 0));
            myAddTrailingBlankLineCheckbox = new JBCheckBox(PySyntaxLocalize.formatterPanelAddTrailingLineFeed().get());
            GridBagConstraints trailingConstraints = constraints(0, 3, 0, GridBagConstraints.NONE, 0, 0);
            trailingConstraints.gridwidth = 3;
            panel1.add(myAddTrailingBlankLineCheckbox, trailingConstraints);
            myDictAlignmentCombo = new ComboBox<>();
            panel1.add(myDictAlignmentCombo, constraints(1, 0, 0, GridBagConstraints.NONE, 0, 0));

            JPanel panel3 = new JPanel(new GridBagLayout());
            panel3.putClientProperty("html.disable", Boolean.FALSE);
            panel3.setBorder(IdeBorderFactory.PlainSmallWithIndent.createTitledBorder(
                BorderFactory.createEtchedBorder(),
                PySyntaxLocalize.formatterPanelInjectedFragments().get(),
                TitledBorder.DEFAULT_JUSTIFICATION,
                TitledBorder.DEFAULT_POSITION,
                panel3.getFont(),
                null
            ));
            GridBagConstraints panel3Constraints = constraints(0, 2, 0, GridBagConstraints.BOTH, 0, 0);
            panel3Constraints.gridwidth = 2;
            panel1.add(panel3, panel3Constraints);
            myAddIndentInsideInjections =
                new JBCheckBox(PySyntaxLocalize.formatterPanelInjectedFragmentsUseContinuationIndent().get());
            panel3.add(myAddIndentInsideInjections, constraints(0, 1, 0, GridBagConstraints.NONE, 0, 0));
            myFormatInjectedFragments = new JBCheckBox(PySyntaxLocalize.formatterPanelFormatInjectedFragments().get());
            panel3.add(myFormatInjectedFragments, constraints(0, 0, 0, GridBagConstraints.NONE, 0, 0));
        }
        addPanelToWatch(myPanel);
        installPreviewPanel(myPreviewPanel);

        // TODO consulo: com.intellij.util.ui.PresentableEnumUtil missing - filled with the enum values and their presentable text
        myDictAlignmentCombo.setModel(new DefaultComboBoxModel<>(DictAlignment.values()));
        myDictAlignmentCombo.setRenderer(SimpleListCellRenderer.<DictAlignment>create("", value -> value.getPresentableText().get()));

        myAddTrailingBlankLineCheckbox.addActionListener(e -> somethingChanged());

        myFormatInjectedFragments.addActionListener(e -> somethingChanged());

        myAddIndentInsideInjections.addActionListener(e -> somethingChanged());
    }

    @Override
    protected EditorHighlighter createHighlighter(EditorColorsScheme scheme) {
        return HighlighterFactory.createHighlighter(new PyHighlighter(LanguageLevel.PYTHON26), scheme);
    }

    @Override
    protected int getRightMargin() {
        return 80;
    }

    @Override
    protected FileType getFileType() {
        return PythonFileType.INSTANCE;
    }

    @Override
    protected String getPreviewText() {
        return PREVIEW;
    }

    @Override
    protected void resetImpl(CodeStyleSettings settings) {
        PyCodeStyleSettings pySettings = getCustomSettings(settings);
        for (DictAlignment alignment : DictAlignment.values()) {
            if (pySettings.DICT_ALIGNMENT == alignment.asInt()) {
                myDictAlignmentCombo.setSelectedItem(alignment);
                break;
            }
        }
        myAddTrailingBlankLineCheckbox.setSelected(pySettings.BLANK_LINE_AT_FILE_END);
        myFormatInjectedFragments.setSelected(pySettings.FORMAT_INJECTED_FRAGMENTS);
        myAddIndentInsideInjections.setSelected(pySettings.ADD_INDENT_INSIDE_INJECTIONS);
    }

    @Override
    public void apply(CodeStyleSettings settings) {
        PyCodeStyleSettings customSettings = getCustomSettings(settings);
        customSettings.DICT_ALIGNMENT = getDictAlignmentAsInt();
        customSettings.BLANK_LINE_AT_FILE_END = ensureTrailingBlankLine();
        customSettings.FORMAT_INJECTED_FRAGMENTS = formatInjectedFragments();
        customSettings.ADD_INDENT_INSIDE_INJECTIONS = addIndentInsideInjections();
    }

    @Override
    public boolean isModified(CodeStyleSettings settings) {
        PyCodeStyleSettings customSettings = getCustomSettings(settings);
        return customSettings.DICT_ALIGNMENT != getDictAlignmentAsInt() ||
            customSettings.BLANK_LINE_AT_FILE_END != ensureTrailingBlankLine() ||
            customSettings.FORMAT_INJECTED_FRAGMENTS != formatInjectedFragments() ||
            customSettings.ADD_INDENT_INSIDE_INJECTIONS != addIndentInsideInjections();
    }

    @Override
    public JComponent getPanel() {
        return myPanel;
    }

    private static PyCodeStyleSettings getCustomSettings(CodeStyleSettings settings) {
        return settings.getCustomSettings(PyCodeStyleSettings.class);
    }

    private int getDictAlignmentAsInt() {
        return ((DictAlignment) myDictAlignmentCombo.getSelectedItem()).asInt();
    }

    private boolean ensureTrailingBlankLine() {
        return myAddTrailingBlankLineCheckbox.isSelected();
    }

    private boolean formatInjectedFragments() {
        return myFormatInjectedFragments.isSelected();
    }

    private boolean addIndentInsideInjections() {
        return myAddIndentInsideInjections.isSelected();
    }

    public static final String PREVIEW = """
        x = max(
            1,
            2,
            3)

        def foo_decl(
            a,
            b,
            c):
            pass

        {
            "green": 42,
            "eggs and ham": -0.0e0
        }

        odds = [
            num for num in range(42)
            if num % 2 != 0\s
        ]""";
}
