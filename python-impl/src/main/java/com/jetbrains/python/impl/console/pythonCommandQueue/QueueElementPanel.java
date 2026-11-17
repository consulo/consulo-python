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
package com.jetbrains.python.impl.console.pythonCommandQueue;

import com.jetbrains.python.console.pydev.ConsoleCommunication;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.platform.base.localize.CommonLocalize;
import consulo.ui.ex.JBColor;
import consulo.ui.ex.awt.GridBag;
import consulo.ui.ex.awt.JBLabel;
import consulo.ui.ex.awt.JBUI;
import consulo.ui.ex.awt.NonOpaquePanel;
import consulo.ui.ex.awt.UIUtil;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.ui.image.Image;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Panel for one command (CommandQueue)
 */
public final class QueueElementPanel {
    private final QueueElementButton myCancelButton = createCancelButton();
    private final JBLabel myText = new JBLabel();
    private final Item myItem;
    private final JPanel myRootPanel = new JPanel();
    private final ConsoleCommunication.ConsoleCodeFragment myCodeFragment;
    private final JPanel buttonPanel;
    private static final int TEXT_MAX_LENGTH = 25;
    private static final int LABEL_FONT_SIZE = 13;
    private static final int ROOT_PANEL_MINIMUM_SIZE_WIDTH = 200;
    private static final int ROOT_PANEL_MINIMUM_SIZE_HEIGHT = 20;
    private static final int ROOT_PANEL_PREFERRED_SIZE_WIDTH = -1;
    private static final int ROOT_PANEL_PREFERRED_SIZE_HEIGHT = 20;

    private volatile boolean isCanceled;

    public QueueElementPanel(ConsoleCommunication.ConsoleCodeFragment codeFragment, @Nullable Image icon) {
        myCodeFragment = codeFragment;
        myText.setFont(JBUI.Fonts.label(LABEL_FONT_SIZE));
        myItem = new Item(codeFragment.getText().trim());
        myText.setText(myItem.getShortText(TEXT_MAX_LENGTH));
        if (icon != null) {
            myText.setIcon(TargetAWT.to(icon));
        }
        buttonPanel = new JPanel();
        buttonPanel.add(createButtonPanel(myCancelButton.button));
        buttonPanel.setOpaque(true);
        buttonPanel.setBackground(JBColor.background());
        buttonPanel.setForeground(JBColor.foreground());

        myRootPanel.setLayout(new BorderLayout());
        myRootPanel.setMinimumSize(new Dimension(ROOT_PANEL_MINIMUM_SIZE_WIDTH, ROOT_PANEL_MINIMUM_SIZE_HEIGHT));
        myRootPanel.setPreferredSize(new Dimension(ROOT_PANEL_PREFERRED_SIZE_WIDTH, ROOT_PANEL_PREFERRED_SIZE_HEIGHT));
        myRootPanel.add(myText, BorderLayout.WEST);
        myRootPanel.add(buttonPanel, BorderLayout.EAST);
        myRootPanel.setBackground(JBColor.background());
        myRootPanel.setFocusable(true);
        myRootPanel.requestFocusInWindow();
        myRootPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                getCommandPanelParent().commandSelected(QueueElementPanel.this);
            }
        });
    }

    private static JPanel createButtonPanel(JComponent component) {
        JPanel iconsPanel = new NonOpaquePanel(new GridBagLayout());
        GridBag gb = new GridBag().setDefaultFill(GridBagConstraints.BOTH);
        iconsPanel.add(component, gb.next());
        return iconsPanel;
    }

    private QueueElementButton createCancelButton() {
        // Consulo-specific: InplaceButton/IconButton are missing in consulo-ui-ex-awt-api,
        // a clickable label with the same icon and tooltip stands in for the JB InplaceButton
        JBLabel cancelButton = new JBLabel(PlatformIconGroup.processStophovered());
        cancelButton.setToolTipText(CommonLocalize.buttonCancel().get());
        cancelButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                cancelRequest();
            }
        });

        cancelButton.setVisible(true);

        return new QueueElementButton(cancelButton, () -> cancelButton.setEnabled(!isCanceled));
    }

    public @Nullable String getText() {
        return myItem.getLongText();
    }

    private void cancelRequest() {
        if (myCancelButton.button.isEnabled()) {
            isCanceled = true;
            getCommandPanelParent().removeCommandByButton(myCodeFragment);
        }
    }

    private PythonCommandQueuePanel getCommandPanelParent() {
        Container parent = myRootPanel;
        while (!(parent instanceof PythonCommandQueuePanel)) {
            parent = parent.getParent();
        }
        return (PythonCommandQueuePanel) parent;
    }

    public void setIcon(Image icon) {
        myText.setIcon(TargetAWT.to(icon));
    }

    public void unsetCancelButton() {
        buttonPanel.setEnabled(false);
        buttonPanel.setVisible(false);
        buttonPanel.setFocusable(false);
    }

    public JComponent getQueuePanel() {
        return myRootPanel;
    }

    static class QueueElementButton {
        final JComponent button;
        final Runnable updateAction;

        QueueElementButton(JComponent button, Runnable updateAction) {
            this.button = button;
            this.updateAction = updateAction;
        }
    }

    public void setTextColor() {
        myText.setForeground(UIUtil.getListSelectionForeground(false));
    }

    public void setButtonColor() {
        buttonPanel.setBackground(new JBColor(() -> UIUtil.getListBackground()));
    }

    public void selectPanel() {
        myRootPanel.setBackground(UIUtil.getListSelectionBackground(true));
        myText.setForeground(UIUtil.getListSelectionForeground(true));
    }

    /**
     * Consulo-specific: port of JB {@code ContentChooser.Item}, which is not accessible in Consulo.
     */
    private static final class Item {
        private static final String RETURN_SYMBOL = "⏎";

        private final String longText;
        private String shortText = "";
        private boolean trimmed;

        Item(String longText) {
            this.longText = longText;
        }

        public String getLongText() {
            return longText;
        }

        public String getShortText(int maxChars) {
            int len = shortText.length();
            if (len > 0 && !trimmed) {
                return shortText;
            }
            if (len >= maxChars && (len - maxChars) * 10 / len == 0) {
                return shortText;
            }
            if (len > maxChars) {
                shortText = StringUtil.first(shortText, maxChars, true);
                trimmed = true;
                return shortText;
            }
            boolean hasSlashR = StringUtil.indexOf(longText, '\r', 0, Math.min(longText.length(), maxChars * 2 + 1)) > 0;
            if (!hasSlashR) {
                String s = StringUtil.first(longText, maxChars, true);
                trimmed = s != longText;
                shortText = StringUtil.convertLineSeparators(s, RETURN_SYMBOL);
            }
            else {
                String s = StringUtil.first(longText, maxChars * 2 + 1, false);
                String s2 = StringUtil.convertLineSeparators(s, RETURN_SYMBOL);
                shortText = StringUtil.first(s2, maxChars, true);
                trimmed = s != longText || s2 != shortText;
            }
            return shortText;
        }
    }
}
