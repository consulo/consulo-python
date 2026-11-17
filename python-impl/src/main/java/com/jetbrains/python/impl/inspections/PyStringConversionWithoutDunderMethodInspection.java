// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.impl.codeInsight.PyDataclassParameters;
import com.jetbrains.python.impl.codeInsight.PyDataclassesKt;
import com.jetbrains.python.impl.inspections.PyInspectionMessages.CodifiedParam;
import com.jetbrains.python.impl.inspections.PyInspectionMessages.ProblemMessage;
import com.jetbrains.python.impl.inspections.quickfix.PyAddDunderMethodQuickFix;
import com.jetbrains.python.impl.psi.PyStringDunderUtil;
import com.jetbrains.python.impl.psi.resolve.PyResolveUtil;
import com.jetbrains.python.impl.psi.types.PyIntersectionType;
import com.jetbrains.python.impl.psi.types.PyUnionType;
import com.jetbrains.python.impl.psi.types.PyUnsafeUnionType;
import com.jetbrains.python.impl.pyi.PyiFile;
import com.jetbrains.python.impl.pyi.PyiUtil;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFStringFragment;
import com.jetbrains.python.psi.PyFormattedStringElement;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyKeywordArgument;
import com.jetbrains.python.psi.PyQualifiedNameOwner;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyFunctionType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeMember;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.ast.IElementType;
import consulo.language.editor.inspection.InspectionToolState;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.editor.inspection.scheme.InspectionProfile;
import consulo.language.editor.inspection.scheme.InspectionProjectProfileManager;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiUtilCore;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

/**
 * Warns when a type that doesn't define __str__, __repr__, or __format__
 * is converted to a string using str(), repr(), format(), f-strings, or print().
 */
@ExtensionImpl
public class PyStringConversionWithoutDunderMethodInspection extends PyInspection {
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameStringConversionWithoutDunder();
    }

    @Override
    public LocalizeValue getGroupDisplayName() {
        return PyLocalize.inspGroupPythonGeneral();
    }

    @Override
    public HighlightDisplayLevel getDefaultLevel() {
        return HighlightDisplayLevel.WEAK_WARNING;
    }

    @Override
    public InspectionToolState<?> createStateProvider() {
        return new PyStringConversionWithoutDunderMethodInspectionState();
    }

    @Override
    public PsiElementVisitor buildVisitor(ProblemsHolder holder,
                                          boolean isOnTheFly,
                                          LocalInspectionToolSession session,
                                          Object state) {
        return new Visitor(holder, PyInspectionVisitor.getContext(session), (PyStringConversionWithoutDunderMethodInspectionState) state);
    }

    private static class Visitor extends PyInspectionVisitor {
        private final PyStringConversionWithoutDunderMethodInspectionState inspection;

        Visitor(@Nullable ProblemsHolder holder,
                TypeEvalContext context,
                PyStringConversionWithoutDunderMethodInspectionState inspection) {
            super(holder, context);
            this.inspection = inspection;
        }

        @Override
        @RequiredReadAction
        public void visitPyCallExpression(PyCallExpression node) {
            super.visitPyCallExpression(node);

            if (!(node.getCallee() instanceof PyReferenceExpression callee)) {
                return;
            }

            // `str(x)` resolves to `builtins.str.__new__`, and so does an explicit `str.__new__(cls)` or a
            // `super().__new__(cls)` in a `str` subclass. Only the first one converts a value to a string.
            if (PyNames.NEW.equals(callee.getName())) {
                return;
            }

            // TODO: use `calleeType?.declarationElement?.qualifiedName` when overloads aren't union types PY-83781
            PsiElement resolvedCallee =
                PyResolveUtil.resolveDeclaration(callee.getReference(), PyResolveContext.defaultContext(myTypeEvalContext));
            if (resolvedCallee == null) {
                return;
            }

            String qualifiedCalleeName = PyNames.FQN.unqualifyBuiltinName(
                resolvedCallee instanceof PyQualifiedNameOwner qualifiedNameOwner ? qualifiedNameOwner.getQualifiedName() : null);
            if (qualifiedCalleeName == null) {
                return;
            }

            PyExpression[] arguments = node.getArguments();
            PyExpression firstArgument = arguments.length > 0 ? arguments[0] : null;
            if (qualifiedCalleeName.equals(PyNames.TYPE_STR + "." + PyNames.NEW)) {
                if (firstArgument != null) {
                    checkStringConversion(firstArgument, PyNames.DUNDER_STR);
                }
            }
            else if (qualifiedCalleeName.equals("repr")) {
                if (firstArgument != null) {
                    checkStringConversion(firstArgument, PyNames.DUNDER_REPR);
                }
            }
            else if (qualifiedCalleeName.equals("format")) {
                if (firstArgument != null) {
                    checkStringConversion(firstArgument, PyNames.DUNDER_FORMAT);
                }
            }
            else if (qualifiedCalleeName.equals("print")) {
                // Check all arguments to print()
                for (PyExpression argument : arguments) {
                    if (argument instanceof PyKeywordArgument) {
                        continue;
                    }
                    checkStringConversion(argument, PyNames.DUNDER_STR);
                }
            }
        }

        @Override
        @RequiredReadAction
        public void visitPyFormattedStringElement(PyFormattedStringElement node) {
            super.visitPyFormattedStringElement(node);

            for (PyFStringFragment fragment : node.getFragments()) {

                boolean isDebug = false;
                if (fragment.getFormatPart() == null) {
                    PsiElement leaf = PsiTreeUtil.prevLeaf(PsiTreeUtil.getDeepestLast(fragment));
                    while (leaf != null) {
                        IElementType elementType = PsiUtilCore.getElementType(leaf);
                        // Consulo: JB compares the element type with the TokenSet PyTokenTypes.WHITESPACE by reference (always unequal);
                        // the cast keeps that exact behavior compilable in Java
                        if (elementType != PyTokenTypes.RBRACE && (Object) elementType != PyTokenTypes.WHITESPACE) {
                            isDebug = elementType == PyTokenTypes.EQ;
                            break;
                        }
                        leaf = PsiTreeUtil.prevLeaf(leaf);
                    }
                }

                PsiElement typeConversion = fragment.getTypeConversion();
                String typeConversionText = typeConversion != null ? typeConversion.getText() : null;
                String requiredMethod;
                if (typeConversionText == null && isDebug) {
                    requiredMethod = PyNames.DUNDER_REPR;
                }
                else if (typeConversionText == null) {
                    requiredMethod = PyNames.DUNDER_FORMAT;
                }
                else if (typeConversionText.equals("!s")) {
                    requiredMethod = PyNames.DUNDER_STR;
                }
                else if (typeConversionText.equals("!r") || typeConversionText.equals("!a")) {
                    requiredMethod = PyNames.DUNDER_REPR;
                }
                else {
                    continue;
                }
                PyExpression expression = fragment.getExpression();
                if (expression != null) {
                    checkStringConversion(expression, requiredMethod);
                }
            }
        }

        @RequiredReadAction
        private void checkStringConversion(PyExpression expression, String requiredMethod) {
            handleType(expression, myTypeEvalContext.getType(expression), requiredMethod);
        }

        @RequiredReadAction
        private void handleType(PyExpression expression, @Nullable PyType type, String requiredMethod) {
            if (type == null) {
                return;
            }

            if (type instanceof PyUnionType unionType) {
                for (PyType member : unionType.getMembers()) {
                    handleType(expression, member, requiredMethod);
                }
            }
            else if (type instanceof PyIntersectionType || type instanceof PyUnsafeUnionType) {
                Collection<@Nullable PyType> members = type instanceof PyIntersectionType intersectionType
                    ? intersectionType.getMembers()
                    : ((PyUnsafeUnionType) type).getMembers();
                boolean anyMemberHasMethod = false;
                for (PyType memberType : members) {
                    if (memberType instanceof PyClassType classType ? !shouldWarnForType(classType, requiredMethod) : true) {
                        anyMemberHasMethod = true;
                        break;
                    }
                }
                if (!anyMemberHasMethod) {
                    registerProblem(expression, type, requiredMethod);
                }
            }
            else if (type instanceof PyFunctionType && inspection.reportedTypes.contains(PyNames.FQN.FUNCTION_TYPE)) {
                registerProblem(expression,
                                PyInspectionMessages.problemMessage(p -> PyLocalize.inspStringNotHelpful(p[0]), "FunctionType"),
                                ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                new RemoveFromReportedTypesQuickFix(PyNames.FQN.FUNCTION_TYPE, "FunctionType"));
            }
            else if (!(type instanceof PyClassType classType)) {
                return;
            }
            else if (inspection.reportedTypes.contains(classType.getClassQName())) {
                String classQName = PyNames.FQN.unqualifyBuiltinName(classType.getClassQName());
                if (classQName == null) {
                    return;
                }
                String typeName = classType.getName();
                if (typeName == null) {
                    return;
                }
                registerProblem(expression,
                                PyInspectionMessages.problemMessage(p -> PyLocalize.inspStringNotHelpful(p[0]),
                                                                    CodifiedParam.ofReference(classType.getPyClass(), typeName)),
                                ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                new RemoveFromReportedTypesQuickFix(classQName, typeName));
            }
            else if (classType.isDefinition() && inspection.reportedTypes.contains(PyNames.FQN.TYPE)) {
                registerProblem(expression,
                                PyInspectionMessages.problemMessage(p -> PyLocalize.inspStringNotHelpful(p[0]), "type"),
                                ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                new RemoveFromReportedTypesQuickFix(PyNames.FQN.TYPE, PyNames.TYPE));
            }
            else if (shouldWarnForType(classType, requiredMethod)) {
                registerProblem(expression, classType, requiredMethod);
            }
        }

        @RequiredReadAction
        private void registerProblem(PyExpression expression, PyType type, String requiredMethod) {
            CodifiedParam param = CodifiedParam.ofType(type, expression, myTypeEvalContext);
            ProblemMessage message;
            if (PyNames.DUNDER_REPR.equals(requiredMethod)) {
                message = PyInspectionMessages.problemMessage(p -> PyLocalize.inspStringConversionWithoutDunderRepr(p[0]), param);
            }
            else if (PyNames.DUNDER_STR.equals(requiredMethod)) {
                message = PyInspectionMessages.problemMessage(p -> PyLocalize.inspStringConversionWithoutDunderStr(p[0]), param);
            }
            else if (PyNames.DUNDER_FORMAT.equals(requiredMethod)) {
                message = PyInspectionMessages.problemMessage(p -> PyLocalize.inspStringConversionWithoutDunderFormat(p[0]), param);
            }
            else {
                return;
            }
            if (type instanceof PyClassType classType) {
                String classQName = PyNames.FQN.unqualifyBuiltinName(classType.getClassQName());
                if (classQName == null) {
                    return;
                }
                String[] parts = classQName.split("\\.");
                registerProblem(expression, message,
                                ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                new AddToIgnoredTypesQuickFix(classQName, parts[parts.length - 1]),
                                new PyAddDunderMethodQuickFix(classType.getPyClass(), requiredMethod));
            }
            else {
                registerProblem(expression, message);
            }
        }

        private boolean shouldWarnForType(PyClassType classType, String requiredMethod) {
            if (inspection.ignoredTypes.contains(classType.getClassQName())) {
                return false;
            }

            // Check if any ancestor is in ignored types (e.g., class A(B) is ignored when B is ignored)
            for (PyClassLikeType ancestor : classType.getPyClass().getAncestorTypes(myTypeEvalContext)) {
                if (inspection.ignoredTypes.contains(ancestor != null ? ancestor.getClassQName() : null)) {
                    return false;
                }
            }

            PyDataclassParameters dataclassParameters = PyDataclassesKt.parseDataclassParameters(classType.getPyClass(), myTypeEvalContext);
            boolean hasSyntheticRepr = dataclassParameters != null && dataclassParameters.getRepr();

            DunderMethodPresence repr = hasSyntheticRepr ? DunderMethodPresence.DEFINED : presenceOfDunderMethod(classType, PyNames.DUNDER_REPR);
            Supplier<DunderMethodPresence> str = lazy(() -> presenceOfDunderMethod(classType, PyNames.DUNDER_STR));
            Supplier<DunderMethodPresence> format = lazy(() -> presenceOfDunderMethod(classType, PyNames.DUNDER_FORMAT));

            // Report only when every method that the conversion can use is absent. An unknown answer never reports.
            if (PyNames.DUNDER_REPR.equals(requiredMethod)) {
                return repr.isAbsent();
            }
            else if (PyNames.DUNDER_STR.equals(requiredMethod)) {
                return repr.isAbsent() && str.get().isAbsent();
            }
            else if (PyNames.DUNDER_FORMAT.equals(requiredMethod)) {
                return repr.isAbsent() && str.get().isAbsent() && format.get().isAbsent();
            }
            else {
                return false;
            }
        }

        private static <T> Supplier<T> lazy(Supplier<T> supplier) {
            return new Supplier<>() {
                private @Nullable T myValue;

                @Override
                public T get() {
                    if (myValue == null) {
                        myValue = supplier.get();
                    }
                    return myValue;
                }
            };
        }

        /**
         * The classes of the MRO, without `object`. A method of `object` is never a custom string conversion.
         * A `null` entry is an ancestor that does not resolve to a class.
         */
        private List<@Nullable PyClass> mroWithoutObject(PyClassType classType) {
            List<@Nullable PyClassLikeType> types = new ArrayList<>();
            types.add(classType);
            types.addAll(classType.getPyClass().getAncestorTypes(myTypeEvalContext));
            List<@Nullable PyClass> result = new ArrayList<>();
            for (PyClassLikeType it : types) {
                if (PyNames.FQN.OBJECT.equals(it != null ? it.getClassQName() : null)) {
                    continue;
                }
                result.add(it instanceof PyClassType type ? type.getPyClass() : null);
            }
            return result;
        }

        private DunderMethodPresence presenceOfDunderMethod(PyClassType classType, String methodName) {
            // special case: no skeleton and no stubs
            String classQName = classType.getClassQName();
            if (classQName != null && PyNames.FQN.NONES.contains(classQName)) {
                return DunderMethodPresence.DEFINED;
            }

            String objectMethodQName = PyNames.FQN.OBJECT + "." + methodName;

            List<PyTypeMember> members = classType.findMember(methodName, PyResolveContext.defaultContext(myTypeEvalContext));
            PsiElement memberInStub = members.isEmpty() ? null : members.get(0).getElement();
            if (memberInStub != null &&
                !objectMethodQName.equals(memberInStub instanceof PyFunction function ? function.getQualifiedName() : null)) {
                return DunderMethodPresence.DEFINED;
            }

            // A type stub keeps `__str__` and `__repr__` only when they change the signature of the `object` ones, so a
            // stub alone never proves that a class has no custom string conversion. Ask the runtime class of every class
            // of the MRO, and answer UNKNOWN when a stub has no runtime class and no known answer.
            boolean unknown = false;
            boolean knownAbsent = false;
            for (PyClass mroClass : mroWithoutObject(classType)) {
                if (mroClass == null) {
                    if (!knownAbsent) {
                        unknown = true;
                    }
                    continue;
                }
                if (mroClass.findMethodByName(methodName, false, myTypeEvalContext) != null) {
                    return DunderMethodPresence.DEFINED;
                }

                // A known type answers for itself and for every ancestor of it. The MRO puts those ancestors after it, so a
                // stub among them adds nothing. A base of a subclass can also come later, and the walk must still see it.
                String mroClassQName = mroClass.getQualifiedName();
                if (mroClassQName != null && PyStringDunderUtil.TYPES_WITHOUT_USEFUL_STRING_CONVERSION.contains(mroClassQName)) {
                    knownAbsent = true;
                    continue;
                }

                if (!(PyiUtil.getOriginalElement(mroClass) instanceof PyClass implementation)) {
                    if (!knownAbsent && mroClass.getContainingFile() instanceof PyiFile) {
                        unknown = true;
                    }
                    continue;
                }
                // A stub often hides a private base that the runtime module declares, so search the implementation ancestors.
                PyFunction implementationMethod = implementation.findMethodInImplementations(methodName, myTypeEvalContext);
                if (implementationMethod != null &&
                    !objectMethodQName.equals(implementationMethod.getQualifiedName()) &&
                    !("builtins." + objectMethodQName).equals(implementationMethod.getQualifiedName())) {
                    return DunderMethodPresence.DEFINED;
                }
            }
            return unknown ? DunderMethodPresence.UNKNOWN : DunderMethodPresence.NOT_DEFINED;
        }
    }

    /**
     * What the IDE knows about a dunder method of a class.
     */
    private enum DunderMethodPresence {
        /**
         * A class of the MRO, `object` apart, defines the method.
         */
        DEFINED,

        /**
         * No class of the MRO defines the method, and every class of the MRO gives a reliable answer.
         */
        NOT_DEFINED,

        /**
         * A class of the MRO comes from a type stub that has no runtime class, so the IDE cannot answer.
         */
        UNKNOWN;

        boolean isAbsent() {
            return this == NOT_DEFINED;
        }
    }

    private static class RemoveFromReportedTypesQuickFix implements LocalQuickFix {
        private final String key;
        private final String displayName;

        RemoveFromReportedTypesQuickFix(String key, String displayName) {
            this.key = key;
            this.displayName = displayName;
        }

        @Override
        public LocalizeValue getName() {
            return PyLocalize.inspStringConversionRemoveFromReportedTypes(displayName);
        }

        @Override
        public boolean startInWriteAction() {
            return false;
        }

        @Override
        public void applyFix(Project project, ProblemDescriptor descriptor) {
            PsiElement element = descriptor.getPsiElement();
            if (element == null) {
                return;
            }
            InspectionProfile profile = InspectionProjectProfileManager.getInstance(project).getInspectionProfile();
            profile.<PyStringConversionWithoutDunderMethodInspection, PyStringConversionWithoutDunderMethodInspectionState>modifyToolSettings(
                PyStringConversionWithoutDunderMethodInspection.class.getSimpleName(),
                element,
                (inspection, state) -> state.reportedTypes.remove(key)
            );
        }
    }

    private static class AddToIgnoredTypesQuickFix implements LocalQuickFix {
        private final String key;
        private final String displayName;

        AddToIgnoredTypesQuickFix(String key, String displayName) {
            this.key = key;
            this.displayName = displayName;
        }

        @Override
        public LocalizeValue getName() {
            return PyLocalize.inspStringConversionAddToIgnoredTypes(displayName);
        }

        @Override
        public boolean startInWriteAction() {
            return false;
        }

        @Override
        public void applyFix(Project project, ProblemDescriptor descriptor) {
            PsiElement element = descriptor.getPsiElement();
            if (element == null) {
                return;
            }
            InspectionProfile profile = InspectionProjectProfileManager.getInstance(project).getInspectionProfile();
            profile.<PyStringConversionWithoutDunderMethodInspection, PyStringConversionWithoutDunderMethodInspectionState>modifyToolSettings(
                PyStringConversionWithoutDunderMethodInspection.class.getSimpleName(),
                element,
                (inspection, state) -> state.ignoredTypes.add(key)
            );
        }
    }
}
