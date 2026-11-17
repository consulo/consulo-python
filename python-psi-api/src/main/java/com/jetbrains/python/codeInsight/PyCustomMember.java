// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight;

import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyPsiFacade;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.PyType;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.util.CachedValueProvider;
import consulo.application.util.CachedValuesManager;
import consulo.application.util.ParameterizedCachedValue;
import consulo.application.util.ParameterizedCachedValueProvider;
import consulo.language.icon.IconDescriptorUpdaters;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiModificationTracker;
import consulo.language.psi.PsiReference;
import consulo.logging.Logger;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.ui.image.Image;
import consulo.util.dataholder.Key;
import consulo.util.dataholder.UserDataHolderBase;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.Function;

/**
 * Note: if you use {@link #myTypeName} to override real field, be sure to use
 * {@link com.jetbrains.python.psi.types.PyOverridingClassMembersProvider}
 *
 * @author Dennis.Ushakov
 */
public class PyCustomMember extends UserDataHolderBase {
    private static final Logger LOG = Logger.getInstance(PyCustomMember.class);
    private static final Key<ParameterizedCachedValue<PyClass, PsiElement>>
        RESOLVE = Key.create("resolve");
    private final String myName;
    private final boolean myResolveToInstance;
    private final @Nullable Function<? super PsiElement, ? extends PyType> myTypeCallback;
    private final @Nullable String myTypeName;

    private final @Nullable PsiElement myTarget;
    private @Nullable PyPsiPath myPsiPath;

    private boolean myIsClassVar = false;

    boolean myFunction = false;

    /**
     * Force resolving to {@code com.jetbrains.python.impl.codeInsight.PyCustomMemberProviderImpl.MyInstanceElement} even if element is function
     */
    private boolean myAlwaysResolveToCustomElement;
    private Image myIcon = PlatformIconGroup.nodesMethod();
    private @Nullable PyCustomMemberTypeInfo<?> myCustomTypeInfo;

    public PyCustomMember(String name, @Nullable String type, boolean resolveToInstance) {
        myName = name;
        myResolveToInstance = resolveToInstance;
        myTypeName = type;

        myTarget = null;
        myTypeCallback = null;
    }

    public PyCustomMember(String name) {
        myName = name;
        myResolveToInstance = false;
        myTypeName = null;

        myTarget = null;
        myTypeCallback = null;
    }

    public PyCustomMember(
        String name,
        @Nullable String type,
        Function<? super PsiElement, ? extends PyType> typeCallback
    ) {
        myName = name;

        myResolveToInstance = false;
        myTypeName = type;

        myTarget = null;
        myTypeCallback = typeCallback;
    }

    public PyCustomMember(String name, @Nullable PsiElement target, @Nullable String typeName) {
        myName = name;
        myTarget = target;
        myResolveToInstance = false;
        myTypeName = typeName;
        myTypeCallback = null;
    }

    public PyCustomMember(String name, @Nullable PsiElement target) {
        this(name, target, null);
    }

    public PyCustomMember resolvesTo(String moduleQName) {
        myPsiPath = new PyPsiPath.ToFile(moduleQName);
        return this;
    }

    public PyCustomMember resolvesToClass(String classQName) {
        myPsiPath = new PyPsiPath.ToClassQName(classQName);
        return this;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PyCustomMember member = (PyCustomMember)o;
        return myResolveToInstance == member.myResolveToInstance &&
            myFunction == member.myFunction &&
            myAlwaysResolveToCustomElement == member.myAlwaysResolveToCustomElement &&
            Objects.equals(myName, member.myName) &&
            Objects.equals(myTypeCallback, member.myTypeCallback) &&
            Objects.equals(myTypeName, member.myTypeName) &&
            Objects.equals(myTarget, member.myTarget) &&
            Objects.equals(myPsiPath, member.myPsiPath) &&
            Objects.equals(myCustomTypeInfo, member.myCustomTypeInfo);
    }

    @Override
    public int hashCode() {
        return Objects
            .hash(myName, myResolveToInstance, myTypeCallback, myTypeName, myTarget, myPsiPath, myFunction, myAlwaysResolveToCustomElement,
                myCustomTypeInfo
            );
    }

    /**
     * Force resolving to {@code com.jetbrains.python.impl.codeInsight.PyCustomMemberProviderImpl.MyInstanceElement} even if element is function
     */
    public final PyCustomMember alwaysResolveToCustomElement() {
        myAlwaysResolveToCustomElement = true;
        return this;
    }

    public PyCustomMember toClass(String name) {
        myPsiPath = new PyPsiPath.ToClass(myPsiPath, name);
        return this;
    }

    public PyCustomMember toFunction(String name) {
        myPsiPath = new PyPsiPath.ToFunction(myPsiPath, name);
        return this;
    }

    public PyCustomMember toFunctionRecursive(String name) {
        myPsiPath = new PyPsiPath.ToFunctionRecursive(myPsiPath, name);
        return this;
    }

    public PyCustomMember toClassAttribute(String name) {
        myPsiPath = new PyPsiPath.ToClassAttribute(myPsiPath, name);
        return this;
    }

    public PyCustomMember toCall(String name, String... args) {
        myPsiPath = new PyPsiPath.ToCall(myPsiPath, name, args);
        return this;
    }

    public PyCustomMember toAssignment(String assignee) {
        myPsiPath = new PyPsiPath.ToAssignment(myPsiPath, assignee);
        return this;
    }

    public PyCustomMember toPsiElement(PsiElement psiElement) {
        myPsiPath = new PyPsiPath() {

            @Override
            public PsiElement resolve(PsiElement context, PyResolveContext resolveContext) {
                return psiElement;
            }
        };
        return this;
    }

    public String getName() {
        return myName;
    }

    @RequiredReadAction
    public Image getIcon() {
        if (myTarget != null) {
            return IconDescriptorUpdaters.getIcon(myTarget, 0);
        }
        return myIcon;
    }

    /**
     * Resolves custom member in specified context.
     *
     * @param context        psi element to be used as psi context
     * @param resolveContext context to be used in resolve
     * @return resolved element
     */
    public @Nullable PsiElement resolve(PsiElement context, PyResolveContext resolveContext) {

        if (myTarget != null) {
            return myTarget;
        }

        PyClass targetClass = null;
        if (myTypeName != null) {

            ParameterizedCachedValueProvider<PyClass, PsiElement> provider = param -> {
                PyClass result = PyPsiFacade.getInstance(param.getProject()).createClassByQName(myTypeName, param);
                return CachedValueProvider.Result.create(result, PsiModificationTracker.MODIFICATION_COUNT);
            };
            targetClass = CachedValuesManager.getManager(context.getProject()).getParameterizedCachedValue(
                this,
                RESOLVE,
                provider,
                false,
                context
            );
        }
        PsiElement resolveTarget = findResolveTarget(context, resolveContext);
        if (resolveTarget instanceof PyFunction && !myAlwaysResolveToCustomElement) {
            return resolveTarget;
        }
        if (resolveTarget != null || targetClass != null) {
            if (targetClass == null && resolveTarget instanceof PyClass pyClass) {
                targetClass = pyClass;
            }

            return PyCustomMemberProvider.getInstance()
                .createPyCustomMemberTarget(this, targetClass, context, resolveTarget, myTypeCallback, myCustomTypeInfo, myResolveToInstance);
        }
        return null;
    }

    private @Nullable PsiElement findResolveTarget(PsiElement context, PyResolveContext resolveContext) {
        if (myPsiPath != null) {
            return myPsiPath.resolve(context, resolveContext);
        }
        return null;
    }

    public @Nullable String getShortType() {
        if (myTypeName == null) {
            return null;
        }
        int pos = myTypeName.lastIndexOf('.');
        return myTypeName.substring(pos + 1);
    }

    public PyCustomMember asFunction() {
        myFunction = true;
        return this;
    }

    public boolean isFunction() {
        return myFunction;
    }


    public PyCustomMember asClassVar() {
        myIsClassVar = true;
        return this;
    }

    public boolean isClassVar() {
        return myIsClassVar;
    }

    /**
     * Checks if some reference points to this element
     *
     * @param reference reference to check
     * @return true if reference points to it
     */
    public final boolean isReferenceToMe(PsiReference reference) {
        return PyCustomMemberProvider.getInstance().isReferenceToMe(reference, this);
    }

    /**
     * @param icon icon to use (will be used method icon otherwise)
     */
    public PyCustomMember withIcon(Image icon) {
        myIcon = icon;
        return this;
    }

    /**
     * Adds custom info to type if class has {@link #myTypeName} set.
     * Info could be later obtained by key.
     *
     * @param customInfo custom info to add
     */
    public PyCustomMember withCustomTypeInfo(PyCustomMemberTypeInfo<?> customInfo) {
        LOG.assertTrue(myTypeName != null, "Cant add custom type info if no type provided");
        myCustomTypeInfo = customInfo;
        return this;
    }
}
