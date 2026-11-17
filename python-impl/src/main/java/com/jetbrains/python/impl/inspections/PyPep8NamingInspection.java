// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.ast.impl.PyUtilCore;
import com.jetbrains.python.impl.PythonUiService;
import com.jetbrains.python.impl.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.Scope;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.search.PySuperMethodsSearch;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.ast.ASTNode;
import consulo.language.editor.inspection.InspectionToolState;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.scheme.InspectionProfile;
import consulo.language.editor.inspection.scheme.InspectionProjectProfileManager;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.psi.util.QualifiedName;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.Pair;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * User : ktisha
 */
@ExtensionImpl
public final class PyPep8NamingInspection extends PyInspection {
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNamePep8Naming();
    }

    @Override
    public LocalizeValue getGroupDisplayName() {
        return PyLocalize.inspGroupPythonCodeStyleIssues();
    }

    @Override
    public HighlightDisplayLevel getDefaultLevel() {
        return HighlightDisplayLevel.WEAK_WARNING;
    }

    private static final String INSPECTION_SHORT_NAME = "PyPep8NamingInspection";
    private static final Pattern LOWERCASE_REGEX = Pattern.compile("[_\\p{javaLowerCase}][_\\p{javaLowerCase}0-9]*");
    private static final Pattern UPPERCASE_REGEX = Pattern.compile("[_\\p{javaUpperCase}][_\\p{javaUpperCase}0-9]*");
    private static final Pattern MIXEDCASE_REGEX = Pattern.compile("_?_?[\\p{javaUpperCase}][\\p{javaLowerCase}\\p{javaUpperCase}0-9]*");
    // See error codes of the tool "pep8-naming"
    private static final Map<String, LocalizeValue> ERROR_CODES_DESCRIPTION = Map.of(
      "N801", PyLocalize.inspPep8NamingClassNamesShouldUseCapwordsConvention(),
      "N802", PyLocalize.inspPep8NamingFunctionNameShouldBeLowercase(),
      "N803", PyLocalize.inspPep8NamingArgumentNameShouldBeLowercase(),
      "N806", PyLocalize.inspPep8NamingVariableInFunctionShouldBeLowercase(),
      "N811", PyLocalize.inspPep8NamingConstantVariableImportedAsNonConstant(),
      "N812", PyLocalize.inspPep8NamingLowercaseVariableImportedAsNonLowercase(),
      "N813", PyLocalize.inspPep8NamingCamelcaseVariableImportedAsLowercase(),
      "N814", PyLocalize.inspPep8NamingCamelcaseVariableImportedAsConstant()
    );
    @Override
    public InspectionToolState<?> createStateProvider() {
        return new PyPep8NamingInspectionState();
    }

    private static void addFunctionQuickFixes(ProblemsHolder holder,
                                              PyClass containingClass,
                                              ASTNode nameNode,
                                              List<LocalQuickFix> quickFixes, TypeEvalContext typeEvalContext) {
        if (holder != null && holder.isOnTheFly()) {
            LocalQuickFix qf = PythonUiService.getInstance().createPyRenameElementQuickFix(nameNode.getPsi());
            if (qf != null) {
                quickFixes.add(qf);
            }
        }

        if (containingClass != null) {
            quickFixes.add(new PyPep8NamingInspection.IgnoreBaseClassQuickFix(containingClass, typeEvalContext));
        }
    }

    private LocalQuickFix[] createRenameAndIgnoreErrorQuickFixes(@Nullable PsiElement node,
                                                                 String errorCode) {
        List<LocalQuickFix> fixes = new ArrayList<>();
        if (node != null) {
            LocalQuickFix qf = PythonUiService.getInstance().createPyRenameElementQuickFix(node);
            if (qf != null) {
                fixes.add(qf);
            }
            fixes.add(new IgnoreErrorFix(errorCode));
        }
        return fixes.toArray(new LocalQuickFix[fixes.size()]);
    }

    @Override
    public PsiElementVisitor buildVisitor(ProblemsHolder holder,
                                          boolean isOnTheFly,
                                          LocalInspectionToolSession session,
                                          Object state) {
        return new Visitor(holder, PyInspectionVisitor.getContext(session), (PyPep8NamingInspectionState) state);
    }

    private static class IgnoreBaseClassQuickFix implements LocalQuickFix {
        private final List<String> myBaseClassNames;

        IgnoreBaseClassQuickFix(PyClass baseClass, TypeEvalContext context) {
            myBaseClassNames = new ArrayList<>();
            ContainerUtil.addIfNotNull(getBaseClassNames(), baseClass.getQualifiedName());
            for (PyClass ancestor : baseClass.getAncestorClasses(context)) {
                ContainerUtil.addIfNotNull(getBaseClassNames(), ancestor.getQualifiedName());
            }
        }

        @Override
        public LocalizeValue getName() {
            return PyLocalize.inspPep8IgnoreMethodNamesForDescendantsOfClass();
        }

        @Override
        public void applyFix(Project project, ProblemDescriptor descriptor) {
            PythonUiService.getInstance().showPopup(project, getBaseClassNames(), PyLocalize.inspPep8IgnoreBaseClass().get(),
                                                    (selectedValue) -> {
                                                        // TODO consulo: InspectionProfileModifiableModelKt.modifyAndCommitProjectProfile missing, modifying the current profile instead
                                                        InspectionProfile profile = InspectionProjectProfileManager.getInstance(project).getInspectionProfile();
                                                        profile.<PyPep8NamingInspection, PyPep8NamingInspectionState>modifyToolSettings(
                                                            PyPep8NamingInspection.class.getSimpleName(),
                                                            descriptor.getPsiElement(),
                                                            (inspection, state) -> ContainerUtil.addIfNotNull(state.ignoredBaseClasses, selectedValue)
                                                        );
                                                    });
        }

        public List<String> getBaseClassNames() {
            return myBaseClassNames;
        }

        // TODO consulo: IntentionPreviewInfo missing (JB: the quick fix updates the inspection's settings, nothing changes in the current file)
    }

    protected static class IgnoreErrorFix implements LocalQuickFix {
        private final String myCode;

        IgnoreErrorFix(String code) {
            myCode = code;
        }

        @Override
        public LocalizeValue getName() {
            return PyLocalize.qfixNameIgnoreErrorsLikeThis();
        }

        @Override
        public boolean startInWriteAction() {
            return false;
        }

        // TODO consulo: IntentionPreviewInfo missing (JB previews the updated ignoredErrors list option)

        @Override
        public void applyFix(Project project, ProblemDescriptor descriptor) {
            PsiFile file = descriptor.getStartElement().getContainingFile();
            // TODO consulo: InspectionProfileModifiableModelKt.modifyAndCommitProjectProfile missing, modifying the current profile instead
            InspectionProfile profile = InspectionProjectProfileManager.getInstance(project).getInspectionProfile();
            profile.<PyPep8NamingInspection, PyPep8NamingInspectionState>modifyToolSettings(INSPECTION_SHORT_NAME, file, (tool, state) -> {
                if (!state.ignoredErrors.contains(myCode)) {
                    state.ignoredErrors.add(myCode);
                }
            });
        }
    }

    public class Visitor extends PyInspectionVisitor {
        private final PyPep8NamingInspectionState myState;

        public Visitor(ProblemsHolder holder, TypeEvalContext context, PyPep8NamingInspectionState state) {
            super(holder, context);
            myState = state;
        }

        @Override
        public void visitPyAssignmentStatement(PyAssignmentStatement node) {
            PyFunction function = PsiTreeUtil.getParentOfType(node, PyFunction.class, true, PyClass.class);
            if (function == null) {
                return;
            }
            Scope scope = ControlFlowCache.getScope(function);
            for (Pair<PyExpression, PyExpression> pair : node.getTargetsToValuesMapping()) {
                PyExpression value = pair.getFirst();
                if (value == null) {
                    continue;
                }
                String name = value.getName();
                if (name == null || scope.isGlobal(name)) {
                    continue;
                }
                if (value instanceof PyTargetExpression) {
                    PyExpression qualifier = ((PyTargetExpression)value).getQualifier();
                    if (qualifier != null) {
                        return;
                    }
                }

                PyCallExpression assignedValue = PyUtil.as(pair.getSecond(), PyCallExpression.class);
                if (assignedValue != null
                    && assignedValue.getCallee() != null && PyNames.NAMEDTUPLE.equals(assignedValue.getCallee().getName())) {
                    return;
                }
                String errorCode = "N806";
                if (!LOWERCASE_REGEX.matcher(name).matches() && !name.startsWith("_") && !myState.ignoredErrors.contains(errorCode)) {
                    registerAndAddRenameAndIgnoreErrorQuickFixes(value, errorCode);
                }
            }
        }

        @Override
        public void visitPyParameter(PyParameter node) {
            String name = node.getName();
            if (name == null) {
                return;
            }

            String errorCode = "N803";
            if (!LOWERCASE_REGEX.matcher(name).matches() && !myState.ignoredErrors.contains(errorCode)) {
                registerAndAddRenameAndIgnoreErrorQuickFixes(node, errorCode);
            }
        }

        protected void registerAndAddRenameAndIgnoreErrorQuickFixes(@Nullable PsiElement node, String errorCode) {
            if (getHolder() != null && getHolder().isOnTheFly()) {
                registerProblem(node, ERROR_CODES_DESCRIPTION.get(errorCode).get(), createRenameAndIgnoreErrorQuickFixes(node, errorCode));
            }
            else {
                registerProblem(node, ERROR_CODES_DESCRIPTION.get(errorCode).get(), new IgnoreErrorFix(errorCode));
            }
        }

        @Override
        public void visitPyFunction(PyFunction function) {
            PyClass containingClass = function.getContainingClass();
            if (myState.ignoreOverriddenFunctions && isOverriddenMethod(function)) {
                return;
            }
            String name = function.getName();
            if (name == null) {
                return;
            }
            if (containingClass != null && (PyUtilCore.isSpecialName(name) || isIgnoredOrHasIgnoredAncestor(containingClass))) {
                return;
            }
            if (!LOWERCASE_REGEX.matcher(name).matches()) {
                ASTNode nameNode = function.getNameNode();
                if (nameNode != null) {
                    List<LocalQuickFix> quickFixes = new ArrayList<>();
                    addFunctionQuickFixes(getHolder(), containingClass, nameNode, quickFixes, myTypeEvalContext);
                    String errorCode = "N802";
                    if (!myState.ignoredErrors.contains(errorCode)) {
                        quickFixes.add(new IgnoreErrorFix(errorCode));
                        registerProblem(nameNode.getPsi(), ERROR_CODES_DESCRIPTION.get(errorCode).get(),
                                        quickFixes.toArray(LocalQuickFix.EMPTY_ARRAY));
                    }
                }
            }
        }

        private boolean isOverriddenMethod(PyFunction function) {
            return PySuperMethodsSearch.search(function, myTypeEvalContext).findFirst() != null;
        }

        private boolean isIgnoredOrHasIgnoredAncestor(PyClass pyClass) {
            Set<String> blackList = new HashSet<>(myState.ignoredBaseClasses);
            if (blackList.contains(pyClass.getQualifiedName())) {
                return true;
            }
            for (PyClassLikeType ancestor : pyClass.getAncestorTypes(myTypeEvalContext)) {
                if (ancestor != null && blackList.contains(ancestor.getClassQName())) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public void visitPyClass(PyClass node) {
            String name = node.getName();
            if (name == null) {
                return;
            }
            String errorCode = "N801";
            if (!myState.ignoredErrors.contains(errorCode)) {
                boolean isLowercaseContextManagerClass = isContextManager(node) && LOWERCASE_REGEX.matcher(name).matches();
                if (!isLowercaseContextManagerClass && !MIXEDCASE_REGEX.matcher(name).matches()) {
                    ASTNode nameNode = node.getNameNode();
                    if (nameNode != null) {
                        registerAndAddRenameAndIgnoreErrorQuickFixes(nameNode.getPsi(), errorCode);
                    }
                }
            }
        }

        private boolean isContextManager(PyClass node) {
            String[] contextManagerFunctionNames = {PyNames.ENTER, PyNames.EXIT};
            for (String name : contextManagerFunctionNames) {
                if (node.findMethodByName(name, false, myTypeEvalContext) == null) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public void visitPyImportElement(PyImportElement node) {
            String asName = node.getAsName();
            QualifiedName importedQName = node.getImportedQName();
            if (importedQName == null) {
                return;
            }
            String name = importedQName.getLastComponent();

            if (asName == null || name == null) {
                return;
            }
            if (UPPERCASE_REGEX.matcher(name).matches()) {
                String errorCode = "N811";
                if (!UPPERCASE_REGEX.matcher(asName).matches() && !myState.ignoredErrors.contains(errorCode)) {
                    registerAndAddRenameAndIgnoreErrorQuickFixes(node.getAsNameElement(), errorCode);
                }
            }
            else if (LOWERCASE_REGEX.matcher(name).matches()) {
                String errorCode = "N812";
                if (!LOWERCASE_REGEX.matcher(asName).matches() && !myState.ignoredErrors.contains(errorCode)) {
                    registerAndAddRenameAndIgnoreErrorQuickFixes(node.getAsNameElement(), errorCode);
                }
            }
            else if (LOWERCASE_REGEX.matcher(asName).matches()) {
                String errorCode = "N813";
                if (!myState.ignoredErrors.contains(errorCode)) {
                    registerAndAddRenameAndIgnoreErrorQuickFixes(node.getAsNameElement(), errorCode);
                }
            }
            else if (UPPERCASE_REGEX.matcher(asName).matches()) {
                String errorCode = "N814";
                if (!myState.ignoredErrors.contains(errorCode)) {
                    registerAndAddRenameAndIgnoreErrorQuickFixes(node.getAsNameElement(), errorCode);
                }
            }
        }
    }
}
