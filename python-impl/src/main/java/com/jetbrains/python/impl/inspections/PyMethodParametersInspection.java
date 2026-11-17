// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.inspections.PyInspectionExtension;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.localize.LocalizeValue;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.scheme.InspectionProfile;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.ast.ASTNode;
import consulo.language.editor.inspection.scheme.InspectionProjectProfileManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import com.jetbrains.python.PyNames;
import consulo.python.impl.localize.PyLocalize;
import com.jetbrains.python.impl.inspections.quickfix.AddSelfQuickFix;
import com.jetbrains.python.impl.inspections.quickfix.RenameParameterQuickFix;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.PyParameterList;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jspecify.annotations.Nullable;

import java.util.Set;

/**
 * Looks for the 'self' or its equivalents.
 */
@ExtensionImpl
public final class PyMethodParametersInspection extends PyInspection {
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameProblematicFirstParameter();
    }

    @Override
    public LocalizeValue getGroupDisplayName() {
        return PyLocalize.inspGroupPythonClassStructure();
    }


    @Override
    public HighlightDisplayLevel getDefaultLevel() {
        return HighlightDisplayLevel.WEAK_WARNING;
    }


    public static @Nullable PyMethodParametersInspection getInstance(PsiElement element) {
        InspectionProfile inspectionProfile = InspectionProjectProfileManager.getInstance(element.getProject()).getCurrentProfile();
        String toolName = PyMethodParametersInspection.class.getSimpleName();
        return (PyMethodParametersInspection)inspectionProfile.getUnwrappedTool(toolName, element);
    }

    @Override
    public PsiElementVisitor buildVisitor(ProblemsHolder holder,
                                          boolean isOnTheFly,
                                          LocalInspectionToolSession session,
                                          Object state) {
        return new Visitor(holder, PyInspectionVisitor.getContext(session));
    }

    private static class Visitor extends PyInspectionVisitor {

        private Visitor(@Nullable ProblemsHolder holder, TypeEvalContext context) {
            super(holder, context);
        }

        @Override
        public void visitPyFunction(PyFunction node) {
            for (PyInspectionExtension extension : PyInspectionExtension.EP_NAME.getExtensionList()) {
                if (extension.ignoreMethodParameters(node, myTypeEvalContext)) {
                    return;
                }
            }

            // analyze function itself
            PyUtil.MethodFlags flags = PyUtil.MethodFlags.of(node);
            if (flags != null) {
                PyParameterList plist = node.getParameterList();
                PyParameter[] params = plist.getParameters();
                String methodName = node.getName();
                if (params.length == 0) { // fix: add
                    // check for "staticmetod"
                    if (flags.isStaticMethod()) return; // no params may be fine
                    // check actual param list
                    ASTNode name_node = node.getNameNode();
                    if (name_node != null) {
                        PsiElement open_paren = plist.getFirstChild();
                        PsiElement close_paren = plist.getLastChild();
                        if (
                            open_paren != null && close_paren != null &&
                            "(".equals(open_paren.getText()) && ")".equals(close_paren.getText())
                        ) {
                            String paramName;
                            if (flags.isMetaclassMethod() || flags.isClassMethod()) {
                                paramName = PyNames.CANONICAL_CLS;
                            }
                            else {
                                paramName = PyNames.CANONICAL_SELF;
                            }
                            registerProblem(
                                plist, PyInspectionMessages.problemMessage(msgArgs -> PyLocalize.inspMustHaveFirstParameter(msgArgs[0]), paramName),
                                ProblemHighlightType.GENERIC_ERROR, new AddSelfQuickFix(paramName)
                            );
                        }
                    }
                }
                else { // fix: rename
                    PyNamedParameter first_param = params[0].getAsNamed();
                    if (first_param != null) {
                        String pname = first_param.getName();
                        if (pname == null) {
                            return;
                        }
                        // every dup, swap, drop, or dup+drop of "self"
                        String[] mangled = {"eslf", "sself", "elf", "felf", "slef", "seelf", "slf", "sslf", "sefl", "sellf", "sef", "seef"};
                        if (PyUtil.among(pname, mangled)) {
                            registerProblem(
                                PyUtil.sure(params[0].getNode()).getPsi(),
                                PyLocalize.inspProbablyMistypedSelf().get(),
                                new RenameParameterQuickFix(PyNames.CANONICAL_SELF)
                            );
                            return;
                        }
                        if (flags.isMetaclassMethod()) {
                            if (flags.isStaticMethod() && !PyNames.NEW.equals(methodName)) {
                                return;
                            }
                            String expectedName;
                            Set<String> alternativeNames;
                            if (PyNames.NEW.equals(methodName) || flags.isClassMethod()) {
                                expectedName = PyNames.CANONICAL_CLS;
                                alternativeNames = Set.of("mcs", "mcls", "metacls");
                            }
                            else if (flags.isSpecialMetaclassMethod()) {
                                expectedName = PyNames.CANONICAL_CLS;
                                alternativeNames = Set.of();
                            }
                            else {
                                expectedName = PyNames.CANONICAL_SELF;
                                alternativeNames = Set.of(PyNames.CANONICAL_CLS);
                            }
                            if (!expectedName.equals(pname) && !alternativeNames.contains(pname)) {
                                registerProblem(
                                    PyUtil.sure(params[0].getNode()).getPsi(),
                                    PyInspectionMessages.problemMessage(msgArgs -> PyLocalize.inspUsuallyNamed(msgArgs[0]), expectedName),
                                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                    new RenameParameterQuickFix(expectedName)
                                );
                            }
                        }
                        else if (flags.isClassMethod() || PyNames.NEW.equals(methodName)) {
                            if (!PyNames.CANONICAL_CLS.equals(pname)) {
                                registerProblem(
                                    PyUtil.sure(params[0].getNode()).getPsi(),
                                    PyInspectionMessages.problemMessage(msgArgs -> PyLocalize.inspUsuallyNamed(msgArgs[0]), PyNames.CANONICAL_CLS),
                                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                    new RenameParameterQuickFix(PyNames.CANONICAL_CLS)
                                );
                            }
                        }
                        else if (!flags.isStaticMethod() && !first_param.isPositionalContainer() && !PyNames.CANONICAL_SELF.equals(pname)) {
                            registerProblem(
                                PyUtil.sure(params[0].getNode()).getPsi(),
                                PyLocalize.inspUsuallyNamedSelf().get(),
                                new RenameParameterQuickFix(PyNames.CANONICAL_SELF)
                            );
                        }
                    }
                    else { // the unusual case of a method with first tuple param
                        if (!flags.isStaticMethod()) {
                            registerProblem(plist, PyLocalize.inspFirstParamMustNotBeTuple().get());
                        }
                    }
                }
            }
        }
    }
}
