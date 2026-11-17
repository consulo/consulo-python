// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.impl.codeInsight.imports;

import consulo.annotation.component.ServiceImpl;
import consulo.application.Application;
import consulo.application.ReadAction;
import consulo.colorScheme.EditorColorsManager;
import consulo.colorScheme.EditorColorsScheme;
import consulo.colorScheme.EditorFontType;
import consulo.dataContext.DataContext;
import consulo.dataContext.DataManager;
import consulo.language.icon.IconDescriptorUpdaters;
import consulo.language.psi.PsiElement;
import consulo.python.impl.localize.PyLocalize;
import consulo.ui.ex.SimpleTextAttributes;
import consulo.ui.ex.awt.SimpleColoredComponent;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.ui.ex.popup.IPopupChooserBuilder;
import consulo.ui.ex.popup.JBPopupFactory;
import consulo.ui.image.Image;
import consulo.util.concurrent.AsyncPromise;
import consulo.util.concurrent.Promise;
import consulo.util.concurrent.Promises;
import consulo.util.lang.Pair;
import jakarta.inject.Singleton;

import javax.swing.JList;
import javax.swing.ListCellRenderer;
import java.awt.Component;
import java.awt.Font;
import java.util.List;
import java.util.function.Consumer;

@ServiceImpl
@Singleton
public class PyImportChooser implements ImportChooser {

    @Override
    public Promise<ImportCandidateHolder> selectImport(List<? extends ImportCandidateHolder> sources, boolean useQualifiedImport) {
        if (Application.get().isUnitTestMode()) {
            return Promises.resolvedPromise(sources.get(0));
        }

        AsyncPromise<ImportCandidateHolder> result = new AsyncPromise<>();

        // GUI part
        DataManager.getInstance().getDataContextFromFocus().doWhenDone((Consumer<DataContext>) dataContext -> {
            IPopupChooserBuilder<ImportCandidateHolder> popup = JBPopupFactory.getInstance()
                .<ImportCandidateHolder>createPopupChooserBuilder(sources)
                .setItemChosenCallback(item -> {
                    result.setResult(item);
                });
            processPopup(popup, useQualifiedImport);
            popup.createPopup()
                .showInBestPositionFor(dataContext);
        });
        return result;
    }

    protected void processPopup(IPopupChooserBuilder<? extends ImportCandidateHolder> popup, boolean useQualifiedImport) {
        popup.setRenderer(new CellRenderer())
            .setTitle(useQualifiedImport ? PyLocalize.actQualifyWithModule().get() : PyLocalize.actFromSomeModuleImport().get())
            .setNamerForFiltering(o -> ReadAction.compute(() -> o.getPresentableText()));
    }

    // Stolen from FQNameCellRenderer
    private static class CellRenderer extends SimpleColoredComponent implements ListCellRenderer<ImportCandidateHolder> {
        private final Font FONT;

        CellRenderer() {
            EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
            FONT = TargetAWT.to(scheme.getFont(EditorFontType.PLAIN));
            setOpaque(true);
        }

        @Override
        public Component getListCellRendererComponent(
            JList<? extends ImportCandidateHolder> list,
            ImportCandidateHolder value,
            int index,
            boolean isSelected,
            boolean cellHasFocus
        ) {
            clear();

            Pair<Image, String> presentation = ReadAction.compute(() -> {
                PsiElement importable = value.getImportable();
                return Pair.create(importable != null ? IconDescriptorUpdaters.getIcon(importable, 0) : null, value.getPresentableText());
            });
            if (presentation.first != null) {
                setIcon(presentation.first);
            }
            append(presentation.second, SimpleTextAttributes.REGULAR_ATTRIBUTES);

            setFont(FONT);
            if (isSelected) {
                setBackground(list.getSelectionBackground());
                setForeground(list.getSelectionForeground());
            }
            else {
                setBackground(list.getBackground());
                setForeground(list.getForeground());
            }
            return this;
        }
    }
}
