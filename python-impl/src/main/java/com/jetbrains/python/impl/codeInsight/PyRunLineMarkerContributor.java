// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight;

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.PyIfStatementNavigator;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyIfStatement;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.dumb.DumbAware;
import consulo.execution.icon.ExecutionIconGroup;
import consulo.execution.lineMarker.ExecutorAction;
import consulo.execution.lineMarker.RunLineMarkerContributor;
import consulo.language.Language;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.ui.ex.action.AnAction;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

@ExtensionImpl
public final class PyRunLineMarkerContributor extends RunLineMarkerContributor implements DumbAware {
    public PyRunLineMarkerContributor() {
        // JB: if (PlatformUtils.isPyCharmEducational()) throw ExtensionNotApplicableException.create();
        // Consulo has no PyCharm Educational edition
    }

    @Override
    @RequiredReadAction
    public @Nullable Info getInfo(PsiElement element) {
        if (!isMainClauseOnTopLevel(element)) {
            return null;
        }
        AnAction[] actions = ExecutorAction.getActions();
        // Consulo: AllIcons.RunConfigurations.TestState.Run -> ExecutionIconGroup.gutterRun(); JB Info(icon, actions) joins the action texts as tooltip
        return new Info(ExecutionIconGroup.gutterRun(),
            e -> StringUtil.join(ContainerUtil.mapNotNull(actions, action -> getText(action, e)), "\n"),
            actions);
    }

    @RequiredReadAction
    private static boolean isMainClauseOnTopLevel(PsiElement element) {
        if (element.getNode().getElementType() == PyTokenTypes.IF_KEYWORD) {
            PyIfStatement statement = PyIfStatementNavigator.getIfStatementByIfKeyword(element);
            return statement != null &&
                ScopeUtil.getScopeOwner(element) instanceof PyFile containingFile &&
                containingFile.getVirtualFile().getFileType() == PythonFileType.INSTANCE &&
                PyUtil.isIfNameEqualsMain(statement);
        }
        else {
            return false;
        }
    }

    // TODO consulo: RunLineMarkerContributor#producesAllPossibleConfigurations(PsiFile) missing - JB returns false
    public boolean producesAllPossibleConfigurations(PsiFile file) {
        return false;
    }

    @Override
    public Language getLanguage() {
        return PythonLanguage.INSTANCE;
    }
}
