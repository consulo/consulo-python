// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.ast.PyAstClass;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.psi.stubs.PyClassStub;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiNameIdentifierOwner;
import consulo.language.psi.StubBasedPsiElement;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.util.collection.ArrayFactory;
import consulo.util.lang.ObjectUtil;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Represents a class declaration in source.
 */
public interface PyClass
    extends PyAstClass, PsiNameIdentifierOwner, PyCompoundStatement, PyDocStringOwner, StubBasedPsiElement<PyClassStub>,
    ScopeOwner, PyDecoratable, PyTypedElement, PyQualifiedNameOwner, PyStatementListContainer, PyWithAncestors,
    PyTypeParameterListOwner, PyDeprecatable {
    PyClass[] EMPTY_ARRAY = new PyClass[0];
    ArrayFactory<PyClass> ARRAY_FACTORY = count -> count == 0 ? EMPTY_ARRAY : new PyClass[count];

    @Override
    @RequiredReadAction
    default PyStatementList getStatementList() {
        return (PyStatementList)PyAstClass.super.getStatementList();
    }

    /**
     * A class definition is an implicit call site for {@code __init_subclass__} of its base classes:
     * the keyword arguments in the base classes list (other than {@code metaclass}) are passed to it.
     */
    @RequiredReadAction
    default List<PyExpression> getArguments() {
        PyArgumentList argumentList = getSuperClassExpressionList();
        if (argumentList == null) {
            return List.of();
        }
        List<PyExpression> arguments = new ArrayList<>();
        for (PyExpression argument : argumentList.getArguments()) {
            if (argument instanceof PyKeywordArgument keywordArgument) {
                if (!PyNames.METACLASS.equals(keywordArgument.getKeyword())) {
                    arguments.add(argument);
                }
            }
            else if (argument instanceof PyStarArgument starArgument && starArgument.isKeyword()) {
                arguments.add(argument);
            }
            // Positional expressions in the base classes list are the base classes themselves, not __init_subclass__ arguments.
        }
        return arguments;
    }

    /**
     * Returns only those ancestors from the hierarchy, that are resolved to PyClass PSI elements.
     *
     * @param context type eval context (pass null to use loose, but better provide one)
     * @see #getAncestorTypes(TypeEvalContext) for the full list of ancestors.
     */
    List<PyClass> getAncestorClasses(@Nullable TypeEvalContext context);

    /**
     * Returns types of expressions in the super classes list.
     * <p/>
     * If no super classes are specified, returns the type of the implicit super class for old- and new-style classes.
     *
     * @see #getAncestorTypes(TypeEvalContext) for the full list of ancestors.
     */
    List<PyClassLikeType> getSuperClassTypes(TypeEvalContext context);

    /**
     * Returns only those super classes for expressions from the super classes list, that are resolved to PyClass PSI elements.
     * <p/>
     * If no super classes are specified, returns the implicit super class for old- and new-style classes.
     *
     * @see #getSuperClassTypes(TypeEvalContext) for the full list of super classes.
     * @see #getAncestorTypes(TypeEvalContext) for the full list of ancestors.
     */
    PyClass[] getSuperClasses(@Nullable TypeEvalContext context);

    /**
     * Returns a PSI element for the super classes list.
     * <p/>
     * Operates at the AST level.
     */
    @Override
    @RequiredReadAction
    default @Nullable PyArgumentList getSuperClassExpressionList() {
        return (PyArgumentList)PyAstClass.super.getSuperClassExpressionList();
    }

    /**
     * Returns PSI elements for the expressions in the super classes list.
     * <p/>
     * Operates at the AST level.
     */
    @Override
    PyExpression[] getSuperClassExpressions();


    /**
     * Collects methods defined in the class, and its parents.
     * <p/>
     * This method does not access AST if the underlying PSI is stub-based.
     *
     * @return class methods
     */
    PyFunction[] getMethodsInherited(@Nullable TypeEvalContext context);

    /**
     * Collects methods defined in the class.
     * <p/>
     * This method does not access AST if underlying PSI is stub based.
     *
     * @return class methods
     */
    PyFunction[] getMethods();

    /**
     * Collects properties defined in the class, and its parents.
     *
     * @param context context to be used to resolve ancestors
     * @return Map [property_name] = [{@link Property}]
     */
    Map<String, Property> getPropertiesInherited(@Nullable TypeEvalContext context);

    /**
     * Collects properties defined in the class.
     *
     * @return Map [property_name] = [{@link Property}]
     */
    Map<String, Property> getProperties();

    /**
     * Finds a method with the given name.
     *
     * @param name      what to look for
     * @param inherited true: search in superclasses; false: only look for methods defined in this class
     * @param context   context to be used to resolve ancestors
     * @return method with given name or null, prefers implementation over same name overloads.
     */
    @Nullable
    PyFunction findMethodByName(@Nullable String name, boolean inherited, TypeEvalContext context);

    default @Nullable PyFunction findMethodInImplementations(@Nullable String name, TypeEvalContext context) {
        return null;
    }

    /**
     * Finds a method with the given name and all its overloads.
     *
     * @param name      what to look for
     * @param inherited true: search in superclasses; false: only look for methods defined in this class
     * @param context   context to be used to resolve ancestors
     * @return all methods with the given name or empty list.
     */
    List<PyFunction> multiFindMethodByName(String name, boolean inherited, @Nullable TypeEvalContext context);

    /**
     * Finds either __init__ or __new__, whichever is defined for given class.
     * If __init__ is defined, it is found first. This mimics the way initialization methods
     * are searched for and called by Python when a constructor call is made.
     * Since __new__ only makes sense for new-style classes, an old-style class never finds it with this method.
     *
     * @param inherited true: search in superclasses, too.
     * @param context   context to be used to resolve ancestors and check if this class is a new-style class
     * @return a method that would be called first when an instance of this class is instantiated,
     * prefers implementation over same name overloads.
     */
    @Nullable
    PyFunction findInitOrNew(boolean inherited, @Nullable TypeEvalContext context);

    /**
     * Finds either __init__ or __new__, whichever is defined for given class, and all its overloads.
     * If __init__ is defined, it is found first. This mimics the way initialization methods
     * are searched for and called by Python when a constructor call is made.
     * Since __new__ only makes sense for new-style classes, an old-style class never finds it with this method.
     *
     * @param inherited true: search in superclasses, too.
     * @param context   context to be used to resolve ancestors and check if this class is a new-style class
     * @return a method that would be called first when an instance of this class is instantiated and all its overloads.
     */
    List<PyFunction> multiFindInitOrNew(boolean inherited, @Nullable TypeEvalContext context);

    /**
     * Finds a property with the specified name in the class or one of its ancestors.
     *
     * @param name    of the property
     * @param context type eval (null to use loose context, but you better provide one)
     * @return descriptor of property accessors, or null if such property does not exist.
     */
    @Nullable
    Property findProperty(String name, boolean inherited, @Nullable TypeEvalContext context);

    /**
     * Apply a processor to every method, looking at superclasses in method resolution order as needed.
     * Consider using {@link PyClassLikeType#visitMembers(Predicate, boolean, TypeEvalContext)}
     *
     * @param processor what to apply
     * @param inherited true: search in superclasses, too.
     * @param context   loose context will be used if no context provided
     * @see PyClassLikeType#visitMembers(Predicate, boolean, TypeEvalContext)
     */
    boolean visitMethods(Predicate<? super PyFunction> processor, boolean inherited, @Nullable TypeEvalContext context);

    /**
     * Consider using {@link PyClassLikeType#visitMembers(Predicate, boolean, TypeEvalContext)}
     *
     * @see PyClassLikeType#visitMembers(Predicate, boolean, TypeEvalContext)
     */
    boolean visitClassAttributes(Predicate<? super PyTargetExpression> processor, boolean inherited, TypeEvalContext context);

    /**
     * Effectively collects assignments inside the class body.
     * <p/>
     * This method does not access AST if underlying PSI is stub based.
     * Note that only <strong>own</strong> attrs are fetched, not parent attrs.
     * If you need parent attributes, consider using {@link #getClassAttributesInherited(TypeEvalContext)}
     *
     * @see #getClassAttributesInherited(TypeEvalContext)
     */
    List<PyTargetExpression> getClassAttributes();

    /**
     * Returns all class attributes this class class contains, including inherited one.
     * Process may be heavy, depending or your context.
     *
     * @param context context to use for this process
     * @return list of attrs.
     * <p>
     * TODO: Replace it and {@link #getClassAttributes()} with a single getClassAttributes(@NotNull TypeEvalContext context, boolean inherited)
     */
    List<PyTargetExpression> getClassAttributesInherited(TypeEvalContext context);

    @Nullable
    PyTargetExpression findClassAttribute(String name, boolean inherited, TypeEvalContext context);

    /**
     * Effectively collects assignments to attributes of {@code self} in {@code __init__}, {@code __new__} and
     * other methods defined in the class.
     * <p/>
     * This method does not access AST if underlying PSI is stub based.
     */
    List<PyTargetExpression> getInstanceAttributes();

    @Nullable
    PyTargetExpression findInstanceAttribute(String name, boolean inherited);

    PyClass[] getNestedClasses();

    @Nullable
    PyClass findNestedClass(String name, boolean inherited);

    /**
     * @return true if the class is new-style and descends from 'object'.
     */
    boolean isNewStyleClass(TypeEvalContext context);

    /**
     * Scan properties in order of definition, until processor returns true for one of them.
     *
     * @param processor to check properties
     * @param inherited whether inherited properties need to be scanned, too
     * @return a property that processor accepted, or null.
     */
    @Nullable
    Property scanProperties(Predicate<? super Property> processor, boolean inherited);

    /**
     * Non-recursively searches for a property for which the given function is a getter, setter or deleter.
     *
     * @param callable the function which may be an accessor
     * @return the property, or null
     */
    @Nullable
    Property findPropertyByCallable(PyCallable callable);

    /**
     * @return True iff this and parent are the same or parent is one of our superclasses.
     */
    boolean isSubclass(PyClass parent, @Nullable TypeEvalContext context);

    boolean isSubclass(String superClassQName, @Nullable TypeEvalContext context);

    /**
     * Returns the aggregated list of names defined in `__slots__` attributes of the class and its ancestors.
     * <p>
     * Returned value is `null` if class or at least one of its ancestor does not follow the next conditions:
     * <ul>
     * <li>it should be a new style class</li>
     * <li>its `__slots__` should exist and should not contain `__dict__`</li>
     * </ul>
     *
     * @param context (will be used default if null)
     */
    @Nullable
    List<String> getSlots(@Nullable TypeEvalContext context);

    /**
     * Returns the list of names in the class' __slots__ attribute, or null if the class
     * does not define such an attribute.
     * <p>
     * Does not include synthesized slot attributes from <code>@dataclass(slots=True)</code>.
     *
     * @return the list of names or null.
     */
    @Nullable
    List<String> getOwnSlots();

    /**
     * Returns the list of names in the class' __match_args__ attribute, or null if the class
     * does not define such an attribute.
     *
     * @return the list of names or null.
     */
    default @Nullable List<String> getOwnMatchArgs() {
        return null;
    }

    /**
     * Process all declarations appearing at the syntactic level of this class' body, in particular class attributes, both
     * assignments and type declarations, methods, and nested classes.
     */
    boolean processClassLevelDeclarations(PsiScopeProcessor processor);

    /**
     * Process all definitions that can be accessed as attributes of this class.
     * These include immediate class-level declarations scanned by {@link #processClassLevelDeclarations(PsiScopeProcessor)} and class
     * object's attributes initialized in methods decorated with {@code @classmethod}.
     */
    default boolean processClassObjectAttributes(PsiScopeProcessor processor, @Nullable PsiElement location) {
        return processClassLevelDeclarations(processor);
    }

    boolean processInstanceLevelDeclarations(PsiScopeProcessor processor, @Nullable PsiElement location);

    //TODO: Add "addMetaClass" or move methods out of here

    /**
     * Returns the type representing the metaclass of the class if it is explicitly set, null otherwise.
     * <p/>
     * The metaclass might be defined outside the class in case of Python 2 file-level __metaclass__ attributes.
     *
     * @deprecated Use {@link #getMetaClassType(boolean, TypeEvalContext)} with inherited=false.
     */
    @Deprecated(forRemoval = true)
    @Nullable
    PyType getMetaClassType(TypeEvalContext context);

    /**
     * If {@code inherited} is false returns the respective type for the metaclass specified using either
     * {@code metaclass} keyword argument in the list of base classes in Python 3 or {@code __metaclass__} attribute
     * defined in this class itself or the module containing it in Python 2. If none is present, returns {@code null}.
     * <p>
     * If {@code inherited} is true, this method performs the same check for every class in MRO additionally taking
     * into account instantiated metaclass ancestors as in the following example:
     * <p>
     * <code><pre>
     * class Meta(type):
     *     pass
     *
     * Base = Meta('Base', (), {})
     *
     * class MyClass(Base):
     *     pass
     * </pre></code>
     * <p>
     * If several candidates are found, the most-derived one (the lowest one in the hierarchy) is returned,
     * and if it cannot be deduced, {@code type} is used as the fallback value.
     */
    default @Nullable PyClassLikeType getMetaClassType(boolean inherited, TypeEvalContext context) {
        return ObjectUtil.tryCast(getMetaClassType(context), PyClassLikeType.class);
    }

    /**
     * Returns the expression that defines the metaclass of the class.
     * <p/>
     * Operates at the AST level.
     */
    @Nullable
    PyExpression getMetaClassExpression();

    /**
     * @param context eval context
     * @return {@link PyType} casted if it has right type
     */
    @Override
    @Nullable
    PyClassLikeType getType(TypeEvalContext context);

    @Override
    @RequiredReadAction
    default @Nullable PyStringLiteralExpression getDocStringExpression() {
        return (PyStringLiteralExpression)PyAstClass.super.getDocStringExpression();
    }
}
