// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.refactoring.classes.membersManager;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.FluentIterable;
import consulo.project.Project;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiReference;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.MultiMap;
import com.jetbrains.python.impl.NotNullPredicate;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.codeInsight.imports.AddImportHelper;
import com.jetbrains.python.impl.codeInsight.imports.AddImportHelper.ImportPriority;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.PyPsiFacade;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.PyFunctionBuilder;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.impl.refactoring.PyPsiRefactoringUtil;
import com.jetbrains.python.impl.refactoring.classes.PyClassRefactoringUtil;
import com.jetbrains.python.impl.refactoring.classes.PyDependenciesComparator;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Plugin that moves class methods
 *
 * @author Ilya.Kazakevich
 */
class MethodsManager extends MembersManager<PyFunction> {

    /**
     * Some decorators should be copied with methods if method is marked abstract. Here is list.
     */
    private static final String[] DECORATORS_MAY_BE_COPIED_TO_ABSTRACT =
        {PyNames.PROPERTY, PyNames.CLASSMETHOD, PyNames.STATICMETHOD};

    public static final String ABC_META_PACKAGE = "abc";
    private static final NoPropertiesPredicate NO_PROPERTIES = new NoPropertiesPredicate();

    MethodsManager() {
        super(PyFunction.class);
    }

    @Override
    public boolean hasConflict(PyFunction member, PyClass aClass) {
        return NamePredicate.hasElementWithSameName(member, Arrays.asList(aClass.getMethods()));
    }

    @Override
    protected Collection<PyElement> getDependencies(MultiMap<PyClass, PyElement> usedElements) {
        return Collections.emptyList();
    }

    @Override
    protected MultiMap<PyClass, PyElement> getDependencies(PyElement member) {
        MyPyRecursiveElementVisitor visitor = new MyPyRecursiveElementVisitor();
        member.accept(visitor);
        return visitor.myResult;
    }

    @Override
    protected List<? extends PyElement> getMembersCouldBeMoved(PyClass pyClass) {
        return FluentIterable.from(Arrays.asList(pyClass.getMethods())).filter(new NamelessFilter<>()).filter(NO_PROPERTIES).toList();
    }

    @Override
    protected Collection<PyElement> moveMembers(PyClass from,
                                                Collection<PyMemberInfo<PyFunction>> members,
                                                PyClass ... to) {
        Collection<PyFunction> methodsToMove = fetchElements(Collections2.filter(members, new AbstractFilter(false)));
        Collection<PyFunction> methodsToAbstract = fetchElements(Collections2.filter(members, new AbstractFilter(true)));

        makeMethodsAbstract(methodsToAbstract, to);
        return moveMethods(from, methodsToMove, true, to);
    }

    /**
     * Creates abstract version of each method in each class (does not touch method itself as opposite to {@link #moveMethods(PyClass, Collection, boolean, PyClass...)})
     *
     * @param currentFunctions functions to make them abstract
     * @param to               classes where abstract method should be created
     */
    private static void makeMethodsAbstract(Collection<PyFunction> currentFunctions, PyClass... to) {
        Set<PsiFile> filesToCheckImport = new HashSet<>();
        Set<PyClass> classesToAddMetaAbc = new HashSet<>();

        for (PyFunction function : currentFunctions) {
            for (PyClass destClass : to) {
                PyFunctionBuilder functionBuilder = PyFunctionBuilder.copySignature(function, DECORATORS_MAY_BE_COPIED_TO_ABSTRACT);
                functionBuilder.decorate(PyNames.ABSTRACTMETHOD);
                PyFunction abstractMethod = functionBuilder.buildFunction();
                PyDependenciesComparator.copyDependencyInfo(function, abstractMethod);
                PyClassRefactoringUtil.addMethods(destClass, false, abstractMethod);
                classesToAddMetaAbc.add(destClass);
            }
        }

        // Add ABCMeta to new classes if needed
        for (PyClass aClass : classesToAddMetaAbc) {
            Project project = aClass.getProject();
            PsiFile file = aClass.getContainingFile();

            PyClass abcMetaClass = PyPsiFacade.getInstance(project).createClassByQName(PyNames.ABC_META, aClass);
            TypeEvalContext context = TypeEvalContext.userInitiated(project, file);

            if (abcMetaClass != null && PyPsiRefactoringUtil.addMetaClassIfNotExist(aClass, abcMetaClass, context)) {
                filesToCheckImport.add(file);
            }
        }

        // Add imports for ABC if needed
        for (PsiFile file : filesToCheckImport) {
            AddImportHelper.addOrUpdateFromImportStatement(file, ABC_META_PACKAGE, PyNames.ABSTRACTMETHOD, null, ImportPriority.BUILTIN, null);
            PyClassRefactoringUtil.optimizeImports(file); //To remove redundant imports
        }
    }

    /**
     * Moves methods (as opposite to {@link #makeMethodsAbstract(Collection, PyClass...)})
     *
     * @param from          source
     * @param methodsToMove what to move
     * @param to            where
     * @param skipIfExist   skip (do not add) if method already exists
     * @return newly added methods
     */
    static List<PyElement> moveMethods(PyClass from,
                                       Collection<? extends PyFunction> methodsToMove,
                                       boolean skipIfExist,
                                       PyClass ... to) {
        if (methodsToMove.isEmpty() || to.length == 0) return Collections.emptyList();
        List<PyElement> result = new ArrayList<>();
        for (PyClass destClass : to) {
            //We move copies here because there may be several destinations
            PyFunction[] copies = ContainerUtil.map2Array(methodsToMove, PyFunction.EMPTY_ARRAY, m -> (PyFunction)m.copy());
            result.addAll(PyClassRefactoringUtil.addMethods(destClass, skipIfExist, copies));
        }
        deleteElements(methodsToMove);
        return result;
    }

    @Override
    public PyMemberInfo<PyFunction> apply(PyFunction pyFunction) {
        PyUtil.MethodFlags flags = PyUtil.MethodFlags.of(pyFunction);
        assert flags != null : "No flags return while element is function " + pyFunction;
        final boolean isStatic = flags.isStaticMethod() || flags.isClassMethod();
        return new PyMemberInfo<>(pyFunction, isStatic, buildDisplayMethodName(pyFunction), isOverrides(pyFunction), this,
                                  couldBeAbstract(pyFunction));
    }

    /**
     * @return if method could be made abstract? (that means "create abstract version if method in parent class")
     */
    private static boolean couldBeAbstract(PyFunction function) {
        if (PyUtil.isInitMethod(function)) {
            return false; // Who wants to make __init__ abstract?!
        }
        PyUtil.MethodFlags flags = PyUtil.MethodFlags.of(function);
        assert flags != null : "Function should be called on method!";

        final boolean py3K = LanguageLevel.forElement(function).isPy3K();
        return flags.isInstanceMethod() || py3K; //Any method could be made abstract in py3
    }


    private static @Nullable Boolean isOverrides(PyFunction pyFunction) {
        PyClass clazz = PyUtil.getContainingClassOrSelf(pyFunction);
        assert clazz != null : "Refactoring called on function, not method: " + pyFunction;
        for (PyClass parentClass : clazz.getSuperClasses(null)) {
            PyFunction parentMethod = parentClass.findMethodByName(pyFunction.getName(), true, null);
            if (parentMethod != null) {
                return true;
            }
        }
        return null;
    }

    private static String buildDisplayMethodName(PyFunction pyFunction) {
        StringBuilder builder = new StringBuilder(pyFunction.getName());
        builder.append('(');
        PyParameter[] arguments = pyFunction.getParameterList().getParameters();
        for (PyParameter parameter : arguments) {
            builder.append(parameter.getName());
            if (arguments.length > 1 && parameter != arguments[arguments.length - 1]) {
                builder.append(", ");
            }
        }
        builder.append(')');
        return builder.toString();
    }


    /**
     * Filters member infos to find if they should be abstracted
     */
    private static final class AbstractFilter extends NotNullPredicate<PyMemberInfo<PyFunction>> {
        private final boolean myAllowAbstractOnly;

        /**
         * @param allowAbstractOnly returns only methods to be abstracted. Returns only methods to be moved otherwise.
         */
        private AbstractFilter(boolean allowAbstractOnly) {
            myAllowAbstractOnly = allowAbstractOnly;
        }

        @Override
        protected boolean applyNotNull(PyMemberInfo<PyFunction> input) {
            return input.isToAbstract() == myAllowAbstractOnly;
        }
    }

    private static class MyPyRecursiveElementVisitor extends PyRecursiveElementVisitorWithResult {
        @Override
        public void visitPyCallExpression(PyCallExpression node) {
            // TODO: refactor, messy code
            PyExpression callee = node.getCallee();
            if (callee != null) {
                PsiReference calleeRef = callee.getReference();
                if (calleeRef != null) {
                    PsiElement calleeDeclaration = calleeRef.resolve();
                    if (calleeDeclaration instanceof PyFunction calleeFunction) {
                        PyClass clazz = calleeFunction.getContainingClass();
                        if (clazz != null) {
                            if (PyUtil.isInitMethod(calleeFunction)) {
                                return; // Init call should not be marked as dependency
                            }
                            myResult.putValue(clazz, calleeFunction);
                        }
                    }
                }
            }
        }
    }

    /**
     * Filter out property setters and getters
     */
    private static class NoPropertiesPredicate implements Predicate<PyFunction> {
        @Override
        public boolean apply(PyFunction input) {
            return input.getProperty() == null;
        }
    }
}
