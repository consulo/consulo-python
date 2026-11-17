// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.rest.inspections;

import com.jetbrains.python.impl.ReSTService;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.rest.quickfixes.AddIgnoredRoleFix;
import com.jetbrains.rest.RestFile;
import com.jetbrains.rest.RestTokenTypes;
import com.jetbrains.rest.RestUtil;
import com.jetbrains.rest.psi.RestDirectiveBlock;
import com.jetbrains.rest.psi.RestRole;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.InspectionToolState;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.ModuleUtilCore;
import consulo.localize.LocalizeValue;
import consulo.module.Module;
import consulo.project.Project;
import consulo.python.rest.localize.PythonRestLocalize;
import consulo.reStructuredText.localize.RestLocalize;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;


import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * User: catherine
 *
 * Looks for using not defined roles
 */
@ExtensionImpl
public final class RestRoleInspection extends RestInspection {
    @Override
    public HighlightDisplayLevel getDefaultLevel() {
        return HighlightDisplayLevel.WARNING;
    }

    @Override
    public InspectionToolState<?> createStateProvider() {
        return new RestRoleInspectionState();
    }

    @Override
    public LocalizeValue getDisplayName() {
        return RestLocalize.inspRoleNotDefined();
    }

    @Override
    public boolean isEnabledByDefault() {
        return false;
    }

    @Override
    public PsiElementVisitor buildVisitor(
        ProblemsHolder holder,
        boolean isOnTheFly,
        LocalInspectionToolSession session,
        Object state
    ) {
        RestRoleInspectionState inspectionState = (RestRoleInspectionState) state;
        return new Visitor(holder, inspectionState.ignoredRoles);
    }

    private class Visitor extends RestInspectionVisitor {
        private final Set<String> myIgnoredRoles;
        Set<String> mySphinxRoles = new HashSet<>();

        public Visitor(ProblemsHolder holder, List<String> ignoredRoles) {
            super(holder);
            myIgnoredRoles = Set.copyOf(ignoredRoles);
            Project project = holder.getProject();
            Module module = ModuleUtilCore.findModuleForPsiElement(holder.getFile());
            if (module == null) {
                return;
            }
            String dir = ReSTService.getInstance(module).getWorkdir();
            if (!dir.isEmpty()) {
                fillSphinxRoles(dir, project);
            }
        }

        private void fillSphinxRoles(String dir, Project project) {
            VirtualFile config = LocalFileSystem.getInstance().findFileByPath((dir.endsWith("/") ? dir : dir + "/") + "conf.py");
            if (config == null) {
                return;
            }

            PsiFile configFile = PsiManager.getInstance(project).findFile(config);
            if (configFile instanceof PyFile file) {
                List<PyFunction> functions = file.getTopLevelFunctions();
                for (PyFunction function : functions) {
                    if (!"setup".equals(function.getName())) {
                        continue;
                    }
                    PyStatementList stList = function.getStatementList();
                    PyStatement[] statements = stList.getStatements();
                    for (PyElement statement : statements) {
                        if (statement instanceof PyExpressionStatement) {
                            statement = ((PyExpressionStatement) statement).getExpression();
                        }
                        if (statement instanceof PyCallExpression) {
                            if (((PyCallExpression) statement).isCalleeText("add_role")) {
                                PyExpression arg = ((PyCallExpression) statement).getArguments()[0];
                                if (arg instanceof PyStringLiteralExpression) {
                                    mySphinxRoles.add(((PyStringLiteralExpression) arg).getStringValue());
                                }
                            }
                        }
                    }
                }
            }
        }

        @Override
        public void visitRole(RestRole node) {
            RestFile file = (RestFile) node.getContainingFile();

            if (PsiTreeUtil.getParentOfType(node, RestDirectiveBlock.class) != null) {
                return;
            }
            PsiElement sibling = node.getNextSibling();
            if (sibling == null || sibling.getNode().getElementType() != RestTokenTypes.INTERPRETED) {
                return;
            }
            if (RestUtil.PREDEFINED_ROLES.contains(node.getText()) || myIgnoredRoles.contains(node.getRoleName())) {
                return;
            }

            if (RestUtil.SPHINX_ROLES.contains(node.getText()) || RestUtil.SPHINX_ROLES.contains(":py" + node.getText()) || mySphinxRoles.contains(
                node.getRoleName())) {
                return;
            }

            Set<String> definedRoles = new HashSet<>();

            RestDirectiveBlock[] directives = PsiTreeUtil.getChildrenOfType(file, RestDirectiveBlock.class);
            if (directives != null) {
                for (RestDirectiveBlock block : directives) {
                    if (block.getDirectiveName().equals("role::")) {
                        PsiElement role = block.getFirstChild().getNextSibling();
                        if (role != null) {
                            String roleName = role.getText().trim();
                            int index = roleName.indexOf('(');
                            if (index != -1) {
                                roleName = roleName.substring(0, index);
                            }
                            definedRoles.add(roleName);
                        }
                    }
                }
            }
            if (definedRoles.contains(node.getRoleName())) {
                return;
            }
            registerProblem(
                node,
                PythonRestLocalize.pythonRestInspectionMessageNotDefinedRole(node.getRoleName()).get(),
                new AddIgnoredRoleFix(node.getRoleName())
            );
        }
    }
}
