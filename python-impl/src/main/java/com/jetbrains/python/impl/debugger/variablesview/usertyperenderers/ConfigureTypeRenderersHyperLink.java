// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.debugger.variablesview.usertyperenderers;

import com.jetbrains.python.debugger.PyDebugValue;
import consulo.execution.debug.frame.XDebuggerTreeNodeHyperlink;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.project.ProjectManager;
import consulo.python.impl.localize.PyLocalize;
import consulo.ui.ex.SimpleTextAttributes;
import consulo.ui.ex.awt.JBCurrentTheme;
import org.jspecify.annotations.Nullable;

import java.awt.event.MouseEvent;

public class ConfigureTypeRenderersHyperLink extends XDebuggerTreeNodeHyperlink {
    private static final SimpleTextAttributes TEXT_ATTRIBUTES =
        new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, JBCurrentTheme.Link.Foreground.PRESSED);

    private final @Nullable String myTypeRendererId;
    private final @Nullable Project myProject;
    private final @Nullable PyDebugValue myDebugValue;

    public ConfigureTypeRenderersHyperLink(@Nullable String typeRendererId, @Nullable Project project) {
        this(typeRendererId, project, null);
    }

    public ConfigureTypeRenderersHyperLink(@Nullable String typeRendererId,
                                           @Nullable Project project,
                                           @Nullable PyDebugValue debugValue) {
        super(LocalizeValue.join(LocalizeValue.of("  "), PyLocalize.formDebuggerVariablesViewUserTypeRenderersConfigureRenderer()));
        myTypeRendererId = typeRendererId;
        myProject = project;
        myDebugValue = debugValue;
    }

    @Override
    public void onClick(MouseEvent event) {
        Project project = myProject != null ? myProject : ProjectManager.getInstance().getDefaultProject();
        if (myTypeRendererId != null) {
            Integer index = ConfigureTypeRenderersActionKt.getRendererIndexWithId(myTypeRendererId);
            if (index != null) {
                ConfigureTypeRenderersActionKt.showSettingsWithSelectedRenderer(project, index);
            }
        }
        else {
            if (myDebugValue != null) {
                ConfigureTypeRenderersActionKt.showSettingsWithNewRenderer(project, myDebugValue);
            }
        }
    }

    @Override
    public boolean alwaysOnScreen() {
        return true;
    }

    @Override
    public SimpleTextAttributes getTextAttributes() {
        return TEXT_ATTRIBUTES;
    }
}
